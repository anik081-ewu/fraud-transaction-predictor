from __future__ import annotations

import numpy as np


class StackedFraudEnsemble:
    """Deployable meta-classifier over chronologically trained base classifiers."""

    def __init__(self, member_models: dict[str, object], meta_model: object) -> None:
        self.member_models = dict(member_models)
        self.member_names = list(member_models)
        self.meta_model = meta_model

    def predict_proba(self, features) -> np.ndarray:
        member_probabilities = np.column_stack([
            self.member_models[name].predict_proba(features)[:, 1]
            for name in self.member_names
        ])
        return self.meta_model.predict_proba(member_probabilities)

    def predict(self, features) -> np.ndarray:
        return self.meta_model.predict(np.column_stack([
            self.member_models[name].predict_proba(features)[:, 1]
            for name in self.member_names
        ]))
