package com.transfer.service;

import com.transfer.common.BadRequestException;
import com.transfer.common.ResourceNotFoundException;
import com.transfer.dto.DispatchVehicleRequest;
import com.transfer.dto.MapPointResponse;
import com.transfer.dto.UpdateVehicleLocationRequest;
import com.transfer.dto.VehicleEtaResponse;
import com.transfer.dto.VehicleTrackResponse;
import com.transfer.enums.CoordinateType;
import com.transfer.enums.IncidentStatus;
import com.transfer.enums.NotificationChannel;
import com.transfer.enums.TaskStatus;
import com.transfer.enums.TaskType;
import com.transfer.enums.VehicleStatus;
import com.transfer.enums.VehicleType;
import com.transfer.enums.UserStatus;
import com.transfer.model.DispatchTask;
import com.transfer.model.EmergencyVehicle;
import com.transfer.model.Incident;
import com.transfer.repository.DispatchTaskRepository;
import com.transfer.repository.EmergencyVehicleRepository;
import com.transfer.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class EmergencyVehicleDispatchService {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    /**
     * 直线距离会比真实道路距离短，所以乘一个系数，更接近实际路程。
     */
    private static final double ROAD_FACTOR = 1.25;

    private final EmergencyVehicleRepository vehicleRepository;
    private final DispatchTaskRepository dispatchTaskRepository;
    private final UserAccountRepository userAccountRepository;
    private final IncidentService incidentService;
    private final MapService mapService;
    private final NotificationService notificationService;
    private final OperationLogService operationLogService;
    private final RealtimeService realtimeService;

    public EmergencyVehicleDispatchService(
            EmergencyVehicleRepository vehicleRepository,
            DispatchTaskRepository dispatchTaskRepository,
            UserAccountRepository userAccountRepository,
            IncidentService incidentService,
            MapService mapService,
            NotificationService notificationService,
            OperationLogService operationLogService,
            RealtimeService realtimeService
    ) {
        this.vehicleRepository = vehicleRepository;
        this.dispatchTaskRepository = dispatchTaskRepository;
        this.userAccountRepository = userAccountRepository;
        this.incidentService = incidentService;
        this.mapService = mapService;
        this.notificationService = notificationService;
        this.operationLogService = operationLogService;
        this.realtimeService = realtimeService;
    }

    @Transactional(readOnly = true)
    public List<EmergencyVehicle> findVehicles(
            VehicleType vehicleType,
            VehicleStatus status
    ) {
        if (vehicleType != null && status != null) {
            return vehicleRepository
                    .findByVehicleTypeAndStatusOrderByVehicleNoAsc(
                            vehicleType,
                            status
                    );
        }

        if (vehicleType != null) {
            return vehicleRepository
                    .findByVehicleTypeOrderByVehicleNoAsc(
                            vehicleType
                    );
        }

        if (status != null) {
            return vehicleRepository
                    .findByStatusOrderByVehicleNoAsc(
                            status
                    );
        }

        return vehicleRepository.findAll();
    }

    /**
     * 指挥中心点击某个事故后，查询某类车辆预计多久到达。
     */
    @Transactional
    public List<VehicleEtaResponse> estimateForIncident(
            Long incidentId,
            VehicleType vehicleType
    ) {
        if (vehicleType == null) {
            throw new BadRequestException(
                    "vehicleType is required"
            );
        }

        Incident incident =
                incidentService.findIncident(incidentId);

        ensureIncidentCoordinate(incident);

        List<EmergencyVehicle> vehicles =
                vehicleRepository
                        .findByVehicleTypeAndStatusOrderByVehicleNoAsc(
                                vehicleType,
                                VehicleStatus.AVAILABLE
                        );

        List<VehicleEtaResponse> responses =
                vehicles.stream()
                        .filter(this::hasCoordinate)
                        .map(vehicle -> {
                            refreshVehicleBaiduPoint(vehicle);
                            return buildEtaResponse(
                                    incident,
                                    vehicle,
                                    false
                            );
                        })
                        .sorted(
                                Comparator
                                        .comparing(
                                                VehicleEtaResponse
                                                        ::estimatedArrivalMinutes
                                        )
                                        .thenComparing(
                                                VehicleEtaResponse
                                                        ::distanceKm
                                        )
                        )
                        .toList();

        if (responses.isEmpty()) {
            return responses;
        }

        VehicleEtaResponse fastest = responses.get(0);

        return responses.stream()
                .map(response -> {
                    boolean isFastest =
                            response.vehicleId()
                                    .equals(
                                            fastest.vehicleId()
                                    );

                    return new VehicleEtaResponse(
                            response.vehicleId(),
                            response.vehicleNo(),
                            response.vehicleName(),
                            response.vehicleType(),
                            response.status(),

                            response.longitude(),
                            response.latitude(),
                            response.coordinateType(),

                            response.baiduLongitude(),
                            response.baiduLatitude(),

                            response.speedKmh(),
                            response.distanceKm(),
                            response.estimatedArrivalMinutes(),

                            isFastest,
                            isFastest
                                    ? "最快可达车辆，预计 "
                                    + response.estimatedArrivalMinutes()
                                    + " 分钟到达"
                                    : "预计 "
                                    + response.estimatedArrivalMinutes()
                                    + " 分钟到达"
                    );
                })
                .toList();
    }

    /**
     * 调度车辆。
     *
     * 后端只根据距离 ÷ 速度计算 ETA 并展示，
     * 车辆必须由指挥人员在 ETA 列表中手动选择。
     */
    @Transactional
    public DispatchTask dispatchVehicle(
            Long incidentId,
            DispatchVehicleRequest request
    ) {
        if (request == null) {
            throw new BadRequestException(
                    "dispatch vehicle request is required"
            );
        }

        if (request.vehicleType() == null) {
            throw new BadRequestException(
                    "vehicleType is required"
            );
        }

        if (request.vehicleId() == null) {
            throw new BadRequestException(
                    "必须由指挥中心在 ETA 列表中手动选择车辆，vehicleId is required"
            );
        }

        if (request.receiverUserId() != null) {
            validateReceiver(request.receiverUserId());
        }

        Incident incident =
                incidentService.findIncident(incidentId);

        ensureIncidentCoordinate(incident);

        EmergencyVehicle vehicle =
                findSelectedVehicle(
                        request.vehicleId(),
                        request.vehicleType()
                );

        refreshVehicleBaiduPoint(vehicle);

        Double targetLongitude =
                resolveIncidentLongitude(incident);

        Double targetLatitude =
                resolveIncidentLatitude(incident);

        double distanceKm =
                calculateRoadDistanceKm(
                        vehicle.getLongitude(),
                        vehicle.getLatitude(),
                        targetLongitude,
                        targetLatitude
                );

        double speedKmh =
                resolveSpeedKmh(vehicle);

        int etaMinutes =
                calculateEtaMinutes(
                        distanceKm,
                        speedKmh
                );

        DispatchTask task =
                new DispatchTask();

        task.setTaskNo(
                generateTaskNo()
        );

        task.setIncidentId(
                incident.getId()
        );

        TaskType taskType =
                toTaskType(
                        vehicle.getVehicleType()
                );

        task.setTaskType(taskType);

        task.setReceiverUserId(
                request.receiverUserId()
        );

        task.setAssignedByUserId(
                request.assignedByUserId()
        );

        task.setStatus(
                TaskStatus.DISPATCHED
        );

        task.setVehicleRequired(
                true
        );

        task.setVehicleType(
                vehicle.getVehicleType().name()
        );

        task.setLocationName(
                incident.getLocationName()
        );

        task.setRiskLevel(
                incident.getRiskLevel()
        );

        task.setAdvice(
                firstNonBlank(
                        request.advice(),
                        "指挥中心已手动调度"
                                + displayVehicleType(
                                vehicle.getVehicleType()
                        )
                                + "："
                                + vehicle.getVehicleNo()
                                + "，预计 "
                                + etaMinutes
                                + " 分钟到达。"
                )
        );

        task.setEmergencyVehicleId(
                vehicle.getId()
        );

        task.setEmergencyVehicleNo(
                vehicle.getVehicleNo()
        );

        task.setEmergencyVehicleName(
                vehicle.getVehicleName()
        );

        task.setVehicleStartLongitude(
                vehicle.getLongitude()
        );

        task.setVehicleStartLatitude(
                vehicle.getLatitude()
        );

        task.setVehicleStartBaiduLongitude(
                vehicle.getBaiduLongitude()
        );

        task.setVehicleStartBaiduLatitude(
                vehicle.getBaiduLatitude()
        );

        task.setIncidentTargetLongitude(
                targetLongitude
        );

        task.setIncidentTargetLatitude(
                targetLatitude
        );

        task.setIncidentTargetBaiduLongitude(
                incident.getBaiduLongitude()
        );

        task.setIncidentTargetBaiduLatitude(
                incident.getBaiduLatitude()
        );

        task.setDispatchDistanceKm(
                round(distanceKm)
        );

        task.setDispatchSpeedKmh(
                round(speedKmh)
        );

        task.setEstimatedArrivalMinutes(
                etaMinutes
        );

        DispatchTask saved =
                dispatchTaskRepository.save(task);

        vehicle.setStatus(
                VehicleStatus.DISPATCHED
        );

        vehicle.setCurrentTaskId(
                saved.getId()
        );

        vehicleRepository.save(vehicle);

        incidentService.updateStatus(
                incident.getId(),
                IncidentStatus.DISPATCHED
        );

        if (request.receiverUserId() != null) {
            notificationService.send(
                    request.receiverUserId(),
                    NotificationChannel.SYSTEM,
                    "新的清障救援任务",
                    "任务 "
                            + saved.getTaskNo()
                            + "，事故编号 "
                            + incident.getIncidentNo()
                            + "，请根据任务详情和导航信息前往现场。"
            );
        }

        operationLogService.record(
                request.assignedByUserId(),
                "DISPATCH_EMERGENCY_VEHICLE_MANUAL",
                "DispatchTask",
                saved.getId().toString(),
                null,
                vehicle.getVehicleNo()
        );

        realtimeService.publish(
                "VEHICLE_DISPATCHED",
                Map.of(
                        "taskId",
                        saved.getId(),

                        "incidentId",
                        incident.getId(),

                        "incidentNo",
                        incident.getIncidentNo(),

                        "vehicleId",
                        vehicle.getId(),

                        "vehicleNo",
                        vehicle.getVehicleNo(),

                        "vehicleType",
                        vehicle.getVehicleType().name(),

                        "estimatedArrivalMinutes",
                        etaMinutes
                )
        );

        return saved;
    }

    /**
     * 前端可以每隔 1～3 秒轮询这个接口，拿车辆当前位置和剩余时间。
     */
    @Transactional
    public VehicleTrackResponse findVehicleTrack(
            Long taskId
    ) {
        DispatchTask task =
                dispatchTaskRepository
                        .findById(taskId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException(
                                        "Dispatch task not found: "
                                                + taskId
                                )
                        );

        if (task.getEmergencyVehicleId() == null) {
            throw new BadRequestException(
                    "This dispatch task has no emergency vehicle"
            );
        }

        EmergencyVehicle vehicle =
                vehicleRepository
                        .findById(
                                task.getEmergencyVehicleId()
                        )
                        .orElseThrow(
                                () -> new ResourceNotFoundException(
                                        "Emergency vehicle not found: "
                                                + task.getEmergencyVehicleId()
                                )
                        );

        LocalDateTime now =
                LocalDateTime.now();

        LocalDateTime startTime =
                task.getDepartedAt() == null
                        ? task.getCreatedAt()
                        : task.getDepartedAt();

        if (task.getStatus() == TaskStatus.DISPATCHED
                && task.getDepartedAt() == null) {
            startTime = now;
        }

        if (startTime == null) {
            startTime = now;
        }

        int totalMinutes =
                task.getEstimatedArrivalMinutes() == null
                        || task.getEstimatedArrivalMinutes() <= 0
                        ? 1
                        : task.getEstimatedArrivalMinutes();

        long elapsedSeconds =
                Math.max(
                        0,
                        Duration
                                .between(
                                        startTime,
                                        now
                                )
                                .getSeconds()
                );

        double totalSeconds =
                Math.max(
                        60,
                        totalMinutes * 60.0
                );

        double progress =
                Math.min(
                        1.0,
                        elapsedSeconds / totalSeconds
                );

        if (task.getStatus() == TaskStatus.DISPATCHED
                && task.getDepartedAt() == null) {
            progress = 0.0;
        }

        if (task.getStatus() == TaskStatus.ARRIVED
                || task.getStatus() == TaskStatus.PROCESSING
                || task.getStatus() == TaskStatus.COMPLETED) {
            progress = 1.0;
        }

        Double currentLongitude =
                interpolate(
                        task.getVehicleStartLongitude(),
                        task.getIncidentTargetLongitude(),
                        progress
                );

        Double currentLatitude =
                interpolate(
                        task.getVehicleStartLatitude(),
                        task.getIncidentTargetLatitude(),
                        progress
                );

        Double currentBaiduLongitude =
                interpolate(
                        task.getVehicleStartBaiduLongitude(),
                        task.getIncidentTargetBaiduLongitude(),
                        progress
                );

        Double currentBaiduLatitude =
                interpolate(
                        task.getVehicleStartBaiduLatitude(),
                        task.getIncidentTargetBaiduLatitude(),
                        progress
                );

        vehicle.setLongitude(
                currentLongitude
        );

        vehicle.setLatitude(
                currentLatitude
        );

        vehicle.setBaiduLongitude(
                currentBaiduLongitude
        );

        vehicle.setBaiduLatitude(
                currentBaiduLatitude
        );

        int remainingMinutes =
                (int) Math.ceil(
                        Math.max(
                                0,
                                totalMinutes
                                        * (1.0 - progress)
                        )
                );

        if (progress >= 1.0
                && task.getStatus() != TaskStatus.ARRIVED
                && task.getStatus() != TaskStatus.PROCESSING
                && task.getStatus() != TaskStatus.COMPLETED) {

            task.setStatus(
                    TaskStatus.ARRIVED
            );

            task.setArrivedAt(
                    now
            );

            vehicle.setStatus(
                    VehicleStatus.ARRIVED
            );

            dispatchTaskRepository.save(task);

            realtimeService.publish(
                    "VEHICLE_ARRIVED",
                    Map.of(
                            "taskId",
                            task.getId(),

                            "incidentId",
                            task.getIncidentId(),

                            "vehicleId",
                            vehicle.getId(),

                            "vehicleNo",
                            vehicle.getVehicleNo()
                    )
            );
        }

        vehicleRepository.save(vehicle);

        return new VehicleTrackResponse(
                task.getId(),
                task.getIncidentId(),

                vehicle.getId(),
                vehicle.getVehicleNo(),
                vehicle.getVehicleName(),
                vehicle.getVehicleType(),

                task.getStatus(),
                vehicle.getStatus(),

                currentLongitude,
                currentLatitude,
                currentBaiduLongitude,
                currentBaiduLatitude,

                task.getIncidentTargetLongitude(),
                task.getIncidentTargetLatitude(),
                task.getIncidentTargetBaiduLongitude(),
                task.getIncidentTargetBaiduLatitude(),

                task.getDispatchDistanceKm(),
                round(
                        progress * 100.0
                ),
                totalMinutes,
                remainingMinutes,

                buildTrackMessage(
                        task,
                        progress,
                        remainingMinutes
                )
        );
    }

    /**
     * 给真实 GPS 或前端模拟器更新车辆位置用。
     */
    @Transactional
    public EmergencyVehicle updateVehicleLocation(
            Long vehicleId,
            UpdateVehicleLocationRequest request
    ) {
        if (request == null) {
            throw new BadRequestException(
                    "vehicle location request is required"
            );
        }

        EmergencyVehicle vehicle =
                vehicleRepository
                        .findById(vehicleId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException(
                                        "Emergency vehicle not found: "
                                                + vehicleId
                                )
                        );

        if (request.longitude() != null) {
            vehicle.setLongitude(
                    request.longitude()
            );
        }

        if (request.latitude() != null) {
            vehicle.setLatitude(
                    request.latitude()
            );
        }

        if (request.coordinateType() != null) {
            vehicle.setCoordinateType(
                    request.coordinateType()
            );
        }

        if (request.speedKmh() != null
                && request.speedKmh() > 0) {
            vehicle.setSpeedKmh(
                    request.speedKmh()
            );
        }

        if (request.status() != null) {
            vehicle.setStatus(
                    request.status()
            );
        }

        if (request.currentAddress() != null) {
            vehicle.setCurrentAddress(
                    trimToNull(
                            request.currentAddress()
                    )
            );
        }

        refreshVehicleBaiduPoint(vehicle);

        EmergencyVehicle saved =
                vehicleRepository.save(vehicle);

        realtimeService.publish(
                "VEHICLE_LOCATION_UPDATED",
                Map.of(
                        "vehicleId",
                        saved.getId(),

                        "vehicleNo",
                        saved.getVehicleNo(),

                        "vehicleType",
                        saved.getVehicleType().name(),

                        "longitude",
                        saved.getLongitude(),

                        "latitude",
                        saved.getLatitude(),

                        "baiduLongitude",
                        saved.getBaiduLongitude(),

                        "baiduLatitude",
                        saved.getBaiduLatitude()
                )
        );

        return saved;
    }

    private String buildTrackMessage(
            DispatchTask task,
            double progress,
            int remainingMinutes
    ) {
        if (task.getStatus() == TaskStatus.DISPATCHED
                && task.getDepartedAt() == null) {
            return "车辆已由指挥中心手动调度，等待清障/救援人员反馈已出发";
        }

        if (progress >= 1.0) {
            return "车辆已到达事故现场";
        }

        return "车辆正在赶往事故现场，预计还需 "
                + remainingMinutes
                + " 分钟";
    }

    private EmergencyVehicle findSelectedVehicle(
            Long vehicleId,
            VehicleType vehicleType
    ) {
        EmergencyVehicle vehicle =
                vehicleRepository
                        .findById(vehicleId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException(
                                        "Emergency vehicle not found: "
                                                + vehicleId
                                )
                        );

        if (vehicle.getVehicleType() != vehicleType) {
            throw new BadRequestException(
                    "Selected vehicle type is "
                            + vehicle.getVehicleType()
                            + ", but request type is "
                            + vehicleType
            );
        }

        if (vehicle.getStatus() != VehicleStatus.AVAILABLE) {
            throw new BadRequestException(
                    "Vehicle is not available: "
                            + vehicle.getVehicleNo()
            );
        }

        return vehicle;
    }

    private VehicleEtaResponse buildEtaResponse(
            Incident incident,
            EmergencyVehicle vehicle,
            boolean fastest
    ) {
        Double targetLongitude =
                resolveIncidentLongitude(incident);

        Double targetLatitude =
                resolveIncidentLatitude(incident);

        double distanceKm =
                calculateRoadDistanceKm(
                        vehicle.getLongitude(),
                        vehicle.getLatitude(),
                        targetLongitude,
                        targetLatitude
                );

        double speedKmh =
                resolveSpeedKmh(vehicle);

        int etaMinutes =
                calculateEtaMinutes(
                        distanceKm,
                        speedKmh
                );

        return new VehicleEtaResponse(
                vehicle.getId(),
                vehicle.getVehicleNo(),
                vehicle.getVehicleName(),
                vehicle.getVehicleType(),
                vehicle.getStatus(),

                vehicle.getLongitude(),
                vehicle.getLatitude(),
                vehicle.getCoordinateType(),

                vehicle.getBaiduLongitude(),
                vehicle.getBaiduLatitude(),

                round(speedKmh),
                round(distanceKm),
                etaMinutes,

                fastest,
                fastest
                        ? "最快可达车辆，预计 "
                        + etaMinutes
                        + " 分钟到达"
                        : "预计 "
                        + etaMinutes
                        + " 分钟到达"
        );
    }

    private void ensureIncidentCoordinate(
            Incident incident
    ) {
        if (resolveIncidentLongitude(incident) == null
                || resolveIncidentLatitude(incident) == null) {
            throw new BadRequestException(
                    "Incident has no coordinate"
            );
        }

        if (incident.getBaiduLongitude() != null
                && incident.getBaiduLatitude() != null) {
            return;
        }

        try {
            mapService.enrichIncident(incident);
        } catch (RuntimeException ignored) {
            if (incident.getBaiduLongitude() == null) {
                incident.setBaiduLongitude(
                        resolveIncidentLongitude(incident)
                );
            }

            if (incident.getBaiduLatitude() == null) {
                incident.setBaiduLatitude(
                        resolveIncidentLatitude(incident)
                );
            }
        }
    }

    private void refreshVehicleBaiduPoint(
            EmergencyVehicle vehicle
    ) {
        if (!hasCoordinate(vehicle)) {
            return;
        }

        try {
            MapPointResponse point =
                    mapService.convertToBaidu(
                            vehicle.getLongitude(),
                            vehicle.getLatitude(),
                            vehicle.getCoordinateType() == null
                                    ? CoordinateType.WGS84
                                    : vehicle.getCoordinateType()
                    );

            vehicle.setBaiduLongitude(
                    point.longitude()
            );

            vehicle.setBaiduLatitude(
                    point.latitude()
            );

        } catch (RuntimeException ignored) {
            vehicle.setBaiduLongitude(
                    vehicle.getLongitude()
            );

            vehicle.setBaiduLatitude(
                    vehicle.getLatitude()
            );
        }
    }

    private boolean hasCoordinate(
            EmergencyVehicle vehicle
    ) {
        return vehicle != null
                && vehicle.getLongitude() != null
                && vehicle.getLatitude() != null;
    }

    private Double resolveIncidentLongitude(
            Incident incident
    ) {
        return incident.getLongitude() != null
                ? incident.getLongitude()
                : incident.getBaiduLongitude();
    }

    private Double resolveIncidentLatitude(
            Incident incident
    ) {
        return incident.getLatitude() != null
                ? incident.getLatitude()
                : incident.getBaiduLatitude();
    }

    private double calculateRoadDistanceKm(
            Double fromLongitude,
            Double fromLatitude,
            Double toLongitude,
            Double toLatitude
    ) {
        if (fromLongitude == null
                || fromLatitude == null
                || toLongitude == null
                || toLatitude == null) {
            throw new BadRequestException(
                    "Coordinate is required for ETA calculation"
            );
        }

        double lat1 =
                Math.toRadians(fromLatitude);

        double lat2 =
                Math.toRadians(toLatitude);

        double deltaLat =
                Math.toRadians(toLatitude - fromLatitude);

        double deltaLon =
                Math.toRadians(toLongitude - fromLongitude);

        double a =
                Math.sin(deltaLat / 2)
                        * Math.sin(deltaLat / 2)
                        + Math.cos(lat1)
                        * Math.cos(lat2)
                        * Math.sin(deltaLon / 2)
                        * Math.sin(deltaLon / 2);

        double c =
                2 * Math.atan2(
                        Math.sqrt(a),
                        Math.sqrt(1 - a)
                );

        return EARTH_RADIUS_KM
                * c
                * ROAD_FACTOR;
    }

    private int calculateEtaMinutes(
            double distanceKm,
            double speedKmh
    ) {
        if (speedKmh <= 0) {
            return 999;
        }

        return Math.max(
                1,
                (int) Math.ceil(
                        distanceKm / speedKmh * 60.0
                )
        );
    }

    private double resolveSpeedKmh(
            EmergencyVehicle vehicle
    ) {
        if (vehicle.getSpeedKmh() != null
                && vehicle.getSpeedKmh() > 0) {
            return vehicle.getSpeedKmh();
        }

        if (vehicle.getVehicleType() == VehicleType.AMBULANCE) {
            return 45.0;
        }

        return 35.0;
    }

    private TaskType toTaskType(
            VehicleType vehicleType
    ) {
        return switch (vehicleType) {
            case AMBULANCE -> TaskType.MEDICAL;
            case CLEARANCE_TRUCK -> TaskType.CLEARANCE;
        };
    }

    private String displayVehicleType(
            VehicleType vehicleType
    ) {
        return switch (vehicleType) {
            case AMBULANCE -> "救护车";
            case CLEARANCE_TRUCK -> "清障车";
        };
    }

    private Double interpolate(
            Double start,
            Double end,
            double progress
    ) {
        if (start == null || end == null) {
            return null;
        }

        return start + (end - start) * progress;
    }

    private void validateReceiver(
            Long receiverUserId
    ) {
        userAccountRepository
                .findById(receiverUserId)
                .filter(user -> user.getStatus()
                        == UserStatus.ENABLED)
                .orElseThrow(
                        () -> new BadRequestException(
                                "Receiver user is not available: "
                                        + receiverUserId
                        )
                );
    }


    private String generateTaskNo() {
        return "TASK"
                + LocalDateTime.now()
                .format(
                        DateTimeFormatter.ofPattern(
                                "yyyyMMddHHmmss"
                        )
                )
                + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 6);
    }

    private Double round(
            Double value
    ) {
        if (value == null) {
            return null;
        }

        return Math.round(value * 100.0) / 100.0;
    }

    private String firstNonBlank(
            String... values
    ) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (value != null
                    && !value.isBlank()) {
                return value.trim();
            }
        }

        return null;
    }

    private String trimToNull(
            String value
    ) {
        return value == null
                || value.trim().isEmpty()
                ? null
                : value.trim();
    }
}
