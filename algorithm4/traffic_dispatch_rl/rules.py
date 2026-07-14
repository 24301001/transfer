from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class DispatchPlan:
    police: int = 0
    ambulance: int = 0
    firetruck: int = 0
    towtruck: int = 0
    heavy_towtruck: int = 0
    note: str = ""


def tow_recommendation(
    *,
    car_flip: int = 0,
    vehicle_num: int = 1,
    affected_lanes: int = 0,
    lane_status: int = 0,
) -> DispatchPlan:
    """Rule-based tow/clearance recommendation for missing tow dispatch data."""
    towtruck = 0
    heavy_towtruck = 0

    if car_flip:
        heavy_towtruck = 1
    if vehicle_num >= 5:
        towtruck += 2
    elif vehicle_num >= 2:
        towtruck += 1
    if affected_lanes >= 2 or lane_status == 2:
        towtruck = max(towtruck, 2)

    if towtruck == 0 and heavy_towtruck == 0:
        note = "No tow truck required by current rule set."
    elif heavy_towtruck:
        note = "Rollover or heavy clearance risk detected; dispatch heavy tow."
    else:
        note = "Lane blockage or multi-vehicle incident detected; dispatch tow."

    return DispatchPlan(towtruck=towtruck, heavy_towtruck=heavy_towtruck, note=note)

