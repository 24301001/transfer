from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from traffic_dispatch_rl.config import EMS_MODEL, FIRE_MODEL
from traffic_dispatch_rl.rules import tow_recommendation


def _add_sb3_path(path: str | None) -> None:
    if path:
        sys.path.insert(0, str(Path(path).resolve()))


def _predict(model_path: Path, env, sb3_root: str | None):
    _add_sb3_path(sb3_root)
    from stable_baselines3 import PPO

    model = PPO.load(model_path, env=env)
    obs, _ = env.reset()
    action, _ = model.predict(obs, deterministic=True)
    _, _, _, _, info = env.step(int(action))
    return info


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate dispatch recommendation.")
    parser.add_argument("--fire", type=int, default=0)
    parser.add_argument("--injury-risk", choices=["low", "medium", "high"], default="medium")
    parser.add_argument("--car-flip", type=int, default=0)
    parser.add_argument("--vehicle-num", type=int, default=1)
    parser.add_argument("--affected-lanes", type=int, default=0)
    parser.add_argument("--lane-status", type=int, default=0)
    parser.add_argument("--sb3-root", type=str, default=None)
    args = parser.parse_args()

    result: dict = {"ambulance": 0, "firetruck": 0, "towtruck": 0, "heavy_towtruck": 0, "details": []}

    if args.injury_risk in {"medium", "high"} and EMS_MODEL.exists():
        from traffic_dispatch_rl.envs import EMSDispatchEnv

        ems = _predict(EMS_MODEL, EMSDispatchEnv(), args.sb3_root)
        result["ambulance"] = ems.get("ambulance", 0)
        result["details"].append(ems)
    elif args.injury_risk == "high":
        result["ambulance"] = 2
        result["details"].append({"resource": "ems", "action_name": "fallback_two_ambulances"})
    elif args.injury_risk == "medium":
        result["ambulance"] = 1
        result["details"].append({"resource": "ems", "action_name": "fallback_one_ambulance"})

    if args.fire and FIRE_MODEL.exists():
        from traffic_dispatch_rl.envs import FireDispatchEnv

        fire = _predict(FIRE_MODEL, FireDispatchEnv(), args.sb3_root)
        result["firetruck"] = fire.get("firetruck", 0)
        result["details"].append(fire)
    elif args.fire:
        result["firetruck"] = 1
        result["details"].append({"resource": "fire", "action_name": "fallback_one_firetruck"})

    tow = tow_recommendation(
        car_flip=args.car_flip,
        vehicle_num=args.vehicle_num,
        affected_lanes=args.affected_lanes,
        lane_status=args.lane_status,
    )
    result["towtruck"] = tow.towtruck
    result["heavy_towtruck"] = tow.heavy_towtruck
    result["details"].append({"resource": "tow", "note": tow.note})

    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
