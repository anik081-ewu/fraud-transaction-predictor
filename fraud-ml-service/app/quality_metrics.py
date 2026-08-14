"""
Label-free quality metrics for anomaly detectors.

These are the metrics that let detectors be ranked without confirmed fraud labels.
Shared by training and comparison so a given model's number
means the same thing wherever it is reported.
"""
from __future__ import annotations

from typing import Iterable

import numpy as np


def _finite(values: Iterable[float]) -> np.ndarray:
    array = np.asarray(list(values), dtype=float)
    return array[np.isfinite(array)]


def excess_mass_auc(
    normality_scores: Iterable[float],
    reference_normality_scores: Iterable[float],
    t_max: float = 1.0,
    n_steps: int = 100,
) -> float:
    """
    Excess-Mass AUC (Goix, "How to evaluate the quality of unsupervised anomaly
    detection algorithms?", 2016), estimated by Monte Carlo.

        EM(t) = max over levels u of [ mass(u) - t * volume(u) ]

    where mass(u) is the fraction of real rows scoring >= u, and volume(u) is the
    fraction of *uniformly sampled* points in the feature bounding box scoring >= u —
    a Monte Carlo estimate of the level set's Lebesgue measure.

    IMPORTANT: both inputs must be **normality**-oriented (higher = more normal, like a
    density estimate or sklearn's decision_function). Excess Mass evaluates density level
    sets, so passing anomaly scores inverts the volume term: uniform samples drawn far
    from the data are genuinely anomalous, so they would dominate the high-score level
    sets and every detector would collapse to the same value. Negate anomaly scores
    before calling.

    A chance detector has mass ~ volume, giving a raw curve mean near 0.5; a perfect one
    keeps mass at 1 while volume collapses, giving 1.0. The result is rescaled so chance
    maps to 0.0 and perfect to 1.0, because the raw statistic only ever occupies the
    upper half of its range and would otherwise read ~50 for everything.

    Both terms are rank-based, so the result is invariant to any monotonic rescaling
    applied to both inputs — which is what makes it comparable across detectors whose
    raw scores live on completely different scales.

    :param normality_scores: normality scores for the real dataset rows
    :param reference_normality_scores: normality scores for uniform samples over the
        feature bounding box. Without these, level-set volume cannot be estimated.
    """
    real = _finite(normality_scores)
    reference = _finite(reference_normality_scores)
    if real.size == 0 or reference.size == 0:
        return 0.0

    # Evaluate at observed score levels, so the max is taken over exactly the level sets
    # the detector can actually produce.
    levels = np.unique(np.quantile(real, np.linspace(0.0, 1.0, 256)))
    if levels.size < 2:
        return 0.0

    # mass and volume are both monotonically non-increasing in the level
    mass = np.array([float(np.mean(real >= level)) for level in levels])
    volume = np.array([float(np.mean(reference >= level)) for level in levels])

    t_values = np.linspace(0.0, float(t_max), int(n_steps))
    # EM(t) = max_u (mass(u) - t * volume(u)), clamped at 0
    em_curve = np.array([max(0.0, float(np.max(mass - t * volume))) for t in t_values])
    raw = float(np.mean(em_curve))
    # EM(0) = 1 for any detector, so the raw mean floors near 0.5 at chance. Rescale that
    # floor to 0 so the reported number spans the full 0-1 range.
    return float(np.clip(2.0 * (raw - 0.5), 0.0, 1.0))


def uniform_reference_matrix(
    x: np.ndarray,
    sample_count: int = 4096,
    seed: int = 42,
    features_per_subspace: int = 5,
) -> np.ndarray:
    """
    Reference points for the volume term of :func:`excess_mass_auc`, built by perturbing a
    random low-dimensional subspace of real rows.

    Each reference point is a real row with ``features_per_subspace`` randomly chosen
    features replaced by uniform draws from those features' observed range. The subspace is
    re-drawn repeatedly, so the pooled sample probes many directions around the data.

    Why not sample uniformly over the whole bounding box: with tens of features, essentially
    every such point lands astronomically far from the data manifold. Every competent
    detector then scores the entire reference as extreme, the volume term collapses to zero
    at any useful level, and EM-AUC pins at 1.0 for every model — which is exactly what
    happened on a 70-feature dataset. Goix's own implementation avoids this by evaluating on
    random low-dimensional subspaces; perturbing a subspace of real rows achieves the same
    thing while reusing a scorer already fitted on the full feature set.

    The trade-off is that this measures volume relative to local perturbations of the data
    rather than Lebesgue measure over the full box, so the value is a relative ranking
    signal rather than an absolute excess-mass figure.
    """
    matrix = np.asarray(x, dtype=float)
    if matrix.ndim != 2 or matrix.size == 0:
        return np.empty((0, 0), dtype=float)
    rows, features = matrix.shape
    minimums = np.nanmin(matrix, axis=0)
    maximums = np.nanmax(matrix, axis=0)
    # Degenerate (constant) features would otherwise collapse to zero width
    spans = np.where(maximums > minimums, maximums - minimums, 1.0)

    rng = np.random.default_rng(seed)
    count = int(sample_count)
    subspace_size = max(1, min(int(features_per_subspace), features))

    # Start from real rows so the reference stays on the data manifold outside the subspace
    reference = matrix[rng.integers(0, rows, size=count)].copy()
    # Perturb a freshly drawn subspace per point, vectorised via argsort-of-random
    chosen = np.argsort(rng.random((count, features)), axis=1)[:, :subspace_size]
    point_index = np.repeat(np.arange(count), subspace_size)
    feature_index = chosen.reshape(-1)
    draws = rng.random(point_index.shape[0])
    reference[point_index, feature_index] = (
        minimums[feature_index] + draws * spans[feature_index]
    )
    return reference


def uniform_reference_scores(
    score_one,
    feature_columns: list[str],
    minimums: dict[str, float],
    maximums: dict[str, float],
    observed_rows: list[dict[str, float]],
    sample_count: int = 2048,
    seed: int = 42,
    features_per_subspace: int = 5,
) -> list[float]:
    """
    Streaming counterpart to :func:`uniform_reference_matrix`, for river-style models that
    score one feature dict at a time.

    Uses the same subspace-perturbation scheme — real rows with a few features randomised —
    so all detectors estimate level-set volume the same way and their
    EM-AUC values stay on one scale. Falls back to fully uniform draws only when no real
    rows were captured, which reproduces the high-dimensional saturation and is therefore a
    last resort.
    """
    if not feature_columns:
        return []
    rng = np.random.default_rng(seed)
    bounds = {}
    for name in feature_columns:
        low = float(minimums.get(name, 0.0))
        high = float(maximums.get(name, 0.0))
        bounds[name] = (low, high if high > low else low + 1.0)

    subspace_size = max(1, min(int(features_per_subspace), len(feature_columns)))
    scores: list[float] = []
    for _ in range(int(sample_count)):
        if observed_rows:
            base = dict(observed_rows[int(rng.integers(0, len(observed_rows)))])
            for name in rng.choice(feature_columns, size=subspace_size, replace=False):
                low, high = bounds[str(name)]
                base[str(name)] = low + float(rng.random()) * (high - low)
            sample = base
        else:
            sample = {
                name: bounds[name][0] + float(rng.random()) * (bounds[name][1] - bounds[name][0])
                for name in feature_columns
            }
        try:
            value = float(score_one(sample))
        except Exception:
            continue
        if np.isfinite(value):
            scores.append(value)
    return scores


def score_skewness(scores: Iterable[float]) -> float:
    """
    Normalised skewness of the score distribution, squashed to [0, 1] with 0.5 meaning
    symmetric.

    A detector that separates anomalies produces a right-tailed score distribution.
    Reported alongside EM-AUC because a detector can concentrate mass well and still
    produce an unusable, near-symmetric score distribution.
    """
    values = _finite(scores)
    if values.size < 3:
        return 0.0
    mean = float(values.mean())
    std = float(values.std())
    if std < 1e-12:
        return 0.0
    skew = float(np.mean(((values - mean) / std) ** 3))
    return float(1.0 / (1.0 + np.exp(-skew)))
