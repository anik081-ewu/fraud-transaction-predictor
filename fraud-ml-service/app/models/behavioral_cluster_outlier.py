from __future__ import annotations

import numpy as np
from sklearn.cluster import MiniBatchKMeans


class BehavioralClusterOutlier:
    """Scalable cluster-conditional anomaly detector for transaction behaviour."""

    def __init__(
        self,
        n_clusters: int = 16,
        contamination: float = 0.01,
        batch_size: int = 2048,
        random_state: int = 42,
    ) -> None:
        self.n_clusters = n_clusters
        self.contamination = contamination
        self.batch_size = batch_size
        self.random_state = random_state

    def fit(self, matrix: np.ndarray) -> "BehavioralClusterOutlier":
        values = np.asarray(matrix, dtype=float)
        if values.ndim != 2 or len(values) < 2:
            raise ValueError("BehavioralClusterOutlier requires at least two rows")
        cluster_count = max(2, min(int(self.n_clusters), len(values)))
        self.clusterer_ = MiniBatchKMeans(
            n_clusters=cluster_count,
            batch_size=max(cluster_count, min(int(self.batch_size), len(values))),
            n_init=3,
            random_state=self.random_state,
        ).fit(values)
        labels = self.clusterer_.labels_
        distances = self._centroid_distances(values, labels)
        self.cluster_medians_ = np.zeros(cluster_count, dtype=float)
        self.cluster_scales_ = np.ones(cluster_count, dtype=float)
        global_median = float(np.median(distances))
        global_scale = self._robust_scale(distances)
        for cluster_id in range(cluster_count):
            cluster_distances = distances[labels == cluster_id]
            if len(cluster_distances) < 5:
                self.cluster_medians_[cluster_id] = global_median
                self.cluster_scales_[cluster_id] = global_scale
            else:
                self.cluster_medians_[cluster_id] = float(np.median(cluster_distances))
                self.cluster_scales_[cluster_id] = self._robust_scale(cluster_distances)
        training_scores = self.anomaly_score(values)
        contamination = float(np.clip(self.contamination, 0.001, 0.25))
        self.threshold_ = float(np.quantile(training_scores, 1.0 - contamination))
        return self

    def anomaly_score(self, matrix: np.ndarray) -> np.ndarray:
        values = np.asarray(matrix, dtype=float)
        labels = self.clusterer_.predict(values)
        distances = self._centroid_distances(values, labels)
        return np.maximum(
            0.0,
            (distances - self.cluster_medians_[labels]) / self.cluster_scales_[labels],
        )

    def decision_function(self, matrix: np.ndarray) -> np.ndarray:
        return self.threshold_ - self.anomaly_score(matrix)

    def predict(self, matrix: np.ndarray) -> np.ndarray:
        return np.where(self.decision_function(matrix) < 0.0, -1, 1)

    def _centroid_distances(self, values: np.ndarray, labels: np.ndarray) -> np.ndarray:
        differences = values - self.clusterer_.cluster_centers_[labels]
        return np.sqrt(np.sum(np.square(differences), axis=1))

    @staticmethod
    def _robust_scale(values: np.ndarray) -> float:
        median = float(np.median(values))
        median_absolute_deviation = float(np.median(np.abs(values - median)))
        return max(1.4826 * median_absolute_deviation, 1e-6)
