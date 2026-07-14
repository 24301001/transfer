from __future__ import annotations

import argparse
import sys
from pathlib import Path

from traffic_dispatch_rl.config import EMS_MODEL, FIRE_MODEL, MODEL_DIR
from traffic_dispatch_rl.envs import EMSDispatchEnv, FireDispatchEnv


def _add_sb3_path(path: str | None) -> None:
    if path:
        sys.path.insert(0, str(Path(path).resolve()))


def main() -> None:
    parser = argparse.ArgumentParser(description="Train emergency dispatch PPO agents.")
    parser.add_argument("--agent", choices=["ems", "fire", "all"], default="all")
    parser.add_argument("--timesteps", type=int, default=20_000)
    parser.add_argument(
        "--sb3-root",
        type=str,
        default=None,
        help="Optional path to stable-baselines3 source root if it is not installed.",
    )
    args = parser.parse_args()
    _add_sb3_path(args.sb3_root)

    from stable_baselines3 import PPO
    from stable_baselines3.common.env_checker import check_env

    MODEL_DIR.mkdir(parents=True, exist_ok=True)

    if args.agent in {"ems", "all"}:
        env = EMSDispatchEnv()
        check_env(env, warn=True)
        model = PPO("MlpPolicy", env, verbose=1)
        model.learn(total_timesteps=args.timesteps)
        model.save(EMS_MODEL)
        print(f"EMS model saved to {EMS_MODEL}")

    if args.agent in {"fire", "all"}:
        env = FireDispatchEnv()
        check_env(env, warn=True)
        model = PPO("MlpPolicy", env, verbose=1)
        model.learn(total_timesteps=args.timesteps)
        model.save(FIRE_MODEL)
        print(f"Fire model saved to {FIRE_MODEL}")


if __name__ == "__main__":
    main()

