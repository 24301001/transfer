from __future__ import annotations

from pathlib import Path

import gymnasium as gym
import numpy as np
import pandas as pd
from gymnasium import spaces

from traffic_dispatch_rl.config import EMS_PROCESSED, FIRE_PROCESSED


class _DispatchEnv(gym.Env):
    metadata = {"render_modes": []}

    def __init__(self, data_path: Path, obs_columns: list[str], seed: int | None = None):
        super().__init__()
        if not data_path.exists():
            raise FileNotFoundError(
                f"Processed data not found: {data_path}. Run `python -m traffic_dispatch_rl.preprocess` first."
            )
        self.data = pd.read_csv(data_path).fillna(0)
        self.obs_columns = obs_columns
        self.rng = np.random.default_rng(seed)
        self.index = 0

        arr = self.data[self.obs_columns].to_numpy(dtype=np.float32)
        self.mean = arr.mean(axis=0)
        self.std = arr.std(axis=0)
        self.std[self.std == 0] = 1.0

        self.observation_space = spaces.Box(
            low=-10.0, high=10.0, shape=(len(obs_columns),), dtype=np.float32
        )

    def _obs(self) -> np.ndarray:
        row = self.data.iloc[self.index]
        values = row[self.obs_columns].to_numpy(dtype=np.float32)
        return np.clip((values - self.mean) / self.std, -10, 10).astype(np.float32)

    def reset(self, *, seed: int | None = None, options: dict | None = None):
        super().reset(seed=seed)
        self.index = int(self.rng.integers(0, len(self.data)))
        return self._obs(), {}


class EMSDispatchEnv(_DispatchEnv):
    """One-step EMS dispatch environment.

    Actions:
      0 = normal ambulance dispatch
      1 = priority ambulance dispatch
      2 = two ambulances / advanced support
      3 = reroute to nearest hospital with advanced support
    """

    ACTIONS = {
        0: {"ambulance": 1, "cost": 1.0, "time_factor": 1.00},
        1: {"ambulance": 1, "cost": 1.4, "time_factor": 0.88},
        2: {"ambulance": 2, "cost": 2.1, "time_factor": 0.78},
        3: {"ambulance": 2, "cost": 2.5, "time_factor": 0.72},
    }

    def __init__(self, data_path: Path = EMS_PROCESSED, seed: int | None = None):
        self.action_space = spaces.Discrete(len(self.ACTIONS))
        super().__init__(
            data_path,
            [
                "month",
                "weekday",
                "hour",
                "severity_code",
                "severity_norm",
                "call_type",
                "borough",
                "dispatch_area",
                "special_event",
                "standby",
                "transfer",
                "held",
                "dispatch_seconds",
                "travel_seconds",
            ],
            seed,
        )

    def step(self, action: int):
        row = self.data.iloc[self.index]
        spec = self.ACTIONS[int(action)]
        baseline = float(row["response_seconds"])
        severity = float(row["severity_norm"])
        urgency_bonus = 1.0 - min(0.18, severity * 0.12) if action in {1, 2, 3} else 1.0
        simulated_response = baseline * spec["time_factor"] * urgency_bonus
        cost = spec["cost"]
        under_dispatch_penalty = 80.0 if severity > 0.75 and action == 0 else 0.0
        over_dispatch_penalty = 25.0 if severity < 0.25 and action in {2, 3} else 0.0
        reward = -(simulated_response / 60.0) - 0.8 * cost - under_dispatch_penalty - over_dispatch_penalty
        info = {
            "resource": "ems",
            "action_name": ["normal", "priority", "two_ambulances", "advanced_support"][int(action)],
            "ambulance": spec["ambulance"],
            "estimated_response_minutes": simulated_response / 60.0,
            "resource_cost": cost,
        }
        return self._obs(), float(reward), True, False, info


class FireDispatchEnv(_DispatchEnv):
    """One-step fire dispatch environment.

    Actions:
      0 = one engine
      1 = one engine + one ladder
      2 = two engines + one ladder
      3 = two engines + one ladder + support unit
    """

    ACTIONS = {
        0: {"engines": 1, "ladders": 0, "other": 0, "cost": 1.0, "time_factor": 1.00},
        1: {"engines": 1, "ladders": 1, "other": 0, "cost": 1.7, "time_factor": 0.86},
        2: {"engines": 2, "ladders": 1, "other": 0, "cost": 2.5, "time_factor": 0.76},
        3: {"engines": 2, "ladders": 1, "other": 1, "cost": 3.1, "time_factor": 0.68},
    }

    def __init__(self, data_path: Path = FIRE_PROCESSED, seed: int | None = None):
        self.action_space = spaces.Discrete(len(self.ACTIONS))
        super().__init__(
            data_path,
            [
                "month",
                "weekday",
                "hour",
                "borough",
                "alarm_source",
                "alarm_index",
                "highest_alarm",
                "classification",
                "classification_group",
                "dispatch_seconds",
                "travel_seconds",
                "engines",
                "ladders",
                "other_units",
            ],
            seed,
        )

    def step(self, action: int):
        row = self.data.iloc[self.index]
        spec = self.ACTIONS[int(action)]
        baseline = float(row["response_seconds"])
        historical_units = float(row["engines"] + row["ladders"] + row["other_units"])
        chosen_units = float(spec["engines"] + spec["ladders"] + spec["other"])
        adequacy_penalty = max(0.0, historical_units - chosen_units) * 35.0
        over_dispatch_penalty = max(0.0, chosen_units - historical_units - 1.0) * 18.0
        simulated_response = baseline * spec["time_factor"]
        reward = -(simulated_response / 60.0) - spec["cost"] - adequacy_penalty - over_dispatch_penalty
        info = {
            "resource": "fire",
            "action_name": ["engine", "engine_ladder", "two_engines_ladder", "full_support"][int(action)],
            "firetruck": spec["engines"],
            "ladder": spec["ladders"],
            "support_unit": spec["other"],
            "estimated_response_minutes": simulated_response / 60.0,
            "resource_cost": spec["cost"],
        }
        return self._obs(), float(reward), True, False, info

