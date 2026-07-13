package com.transfer.service;

import com.transfer.common.BadRequestException;

import com.transfer.common.ResourceNotFoundException;

import com.transfer.dto.ClearanceRescueTaskResponse;

import com.transfer.dto.CommandDispatchRequest;

import com.transfer.dto.CreateDispatchTaskRequest;

import com.transfer.dto.DispatchAssignmentRequest;

import com.transfer.dto.MapLocationResponse;

import com.transfer.dto.MapPointResponse;

import com.transfer.dto.UpdateTaskStatusRequest;

import com.transfer.enums.CoordinateType;

import com.transfer.enums.IncidentStatus;

import com.transfer.enums.NotificationChannel;

import com.transfer.enums.TaskStatus;

import com.transfer.enums.TaskType;

import com.transfer.enums.UserStatus;

import com.transfer.enums.VehicleStatus;

import com.transfer.model.DispatchTask;

import com.transfer.model.EmergencyVehicle;

import com.transfer.model.Incident;

import com.transfer.model.UserAccount;

import com.transfer.repository.DispatchTaskRepository;

import com.transfer.repository.EmergencyVehicleRepository;

import com.transfer.repository.UserAccountRepository;

import org.springframework.data.domain.Page;

import org.springframework.data.domain.Pageable;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import java.time.format.DateTimeFormatter;

import java.util.ArrayList;

import java.util.List;

import java.util.Map;

import java.util.UUID;

@Service
public class DispatchTaskService {

    private static final List<TaskStatus>
            ACTIVE_STATUSES =

            List.of(

                    TaskStatus.DISPATCHED,

                    TaskStatus.DEPARTED,

                    TaskStatus.ARRIVED,

                    TaskStatus.PROCESSING
            );


    private final DispatchTaskRepository
            dispatchTaskRepository;


    private final EmergencyVehicleRepository
            emergencyVehicleRepository;


    private final UserAccountRepository
            userAccountRepository;


    private final IncidentService
            incidentService;


    private final MapService
            mapService;


    private final NotificationService
            notificationService;


    private final OperationLogService
            operationLogService;


    private final RealtimeService
            realtimeService;


    public DispatchTaskService(

            DispatchTaskRepository
                    dispatchTaskRepository,

            EmergencyVehicleRepository
                    emergencyVehicleRepository,

            UserAccountRepository
                    userAccountRepository,

            IncidentService
                    incidentService,

            MapService
                    mapService,

            NotificationService
                    notificationService,

            OperationLogService
                    operationLogService,

            RealtimeService
                    realtimeService

    ) {

        this.dispatchTaskRepository =

                dispatchTaskRepository;


        this.emergencyVehicleRepository =

                emergencyVehicleRepository;


        this.userAccountRepository =

                userAccountRepository;


        this.incidentService =

                incidentService;


        this.mapService =

                mapService;


        this.notificationService =

                notificationService;


        this.operationLogService =

                operationLogService;


        this.realtimeService =

                realtimeService;
    }


    /**
     * 原有的单任务创建接口。
     */
    @Transactional
    public DispatchTask create(

            CreateDispatchTaskRequest request

    ) {

        Incident incident =

                incidentService

                        .findIncident(

                                request

                                        .incidentId()
                        );


        DispatchTask task =

                createOne(

                        incident,

                        request

                                .taskType(),

                        request

                                .receiverUserId(),

                        request

                                .assignedByUserId(),

                        request

                                .vehicleRequired(),

                        request

                                .vehicleType(),

                        request

                                .advice()
                );


        incidentService

                .updateStatus(

                        incident

                                .getId(),

                        IncidentStatus.DISPATCHED
                );


        return task;
    }


    /**
     * 指挥中心批量调度。
     */
    @Transactional
    public List<DispatchTask> createBatch(

            Long incidentId,

            CommandDispatchRequest request

    ) {

        Incident incident =

                incidentService

                        .findIncident(

                                incidentId
                        );


        if (

                request

                        .assignments()

                        == null

                        ||

                        request

                                .assignments()

                                .isEmpty()

        ) {

            throw new BadRequestException(

                    "At least one dispatch "

                            + "assignment is required"
            );
        }


        List<DispatchTask> tasks =

                new ArrayList<>();


        for (

                DispatchAssignmentRequest assignment

                : request.assignments()

        ) {

            /*
             * 这里只处理指挥人员
             * 手动填写的建议。
             */
            String advice =

                    firstNonBlank(

                            assignment

                                    .advice(),

                            request

                                    .commonAdvice()
                    );


            tasks.add(

                    createOne(

                            incident,

                            assignment

                                    .taskType(),

                            assignment

                                    .receiverUserId(),

                            request

                                    .assignedByUserId(),

                            assignment

                                    .vehicleRequired(),

                            assignment

                                    .vehicleType(),

                            advice
                    )
            );
        }


        incidentService

                .updateStatus(

                        incident

                                .getId(),

                        IncidentStatus.DISPATCHED
                );


        return tasks;
    }


    @Transactional(
            readOnly = true
    )
    public Page<DispatchTask> findAll(

            Pageable pageable

    ) {

        return dispatchTaskRepository

                .findAll(

                        pageable
                );
    }


    @Transactional(
            readOnly = true
    )
    public List<DispatchTask> findMyTasks(

            Long receiverUserId

    ) {

        if (receiverUserId == null) {

            return dispatchTaskRepository

                    .findAll();
        }


        return dispatchTaskRepository

                .findByReceiverUserIdOrderByCreatedAtDesc(

                        receiverUserId
                );
    }


    @Transactional(
            readOnly = true
    )
    public List<ClearanceRescueTaskResponse>
    findMyCurrentTasks(

            Long receiverUserId

    ) {

        return findMyTasksByStatuses(

                receiverUserId,

                ACTIVE_STATUSES
        );
    }


    @Transactional(
            readOnly = true
    )
    public List<ClearanceRescueTaskResponse>
    findMyHistoryTasks(

            Long receiverUserId

    ) {

        return findMyTasksByStatuses(

                receiverUserId,

                List.of(

                        TaskStatus.COMPLETED,

                        TaskStatus.CANCELLED
                )
        );
    }


    @Transactional(
            readOnly = true
    )
    public DispatchTask findTask(

            Long taskId

    ) {

        return dispatchTaskRepository

                .findById(

                        taskId
                )

                .orElseThrow(

                        () ->

                                new ResourceNotFoundException(

                                        "Dispatch task not found: "

                                                + taskId
                                )
                );
    }


    @Transactional(
            readOnly = true
    )
    public ClearanceRescueTaskResponse
    findTaskDetail(

            Long taskId

    ) {

        return toClearanceRescueTaskResponse(

                findTask(

                        taskId
                )
        );
    }


    @Transactional(
            readOnly = true
    )
    public List<DispatchTask>
    findByIncidentId(

            Long incidentId

    ) {

        incidentService

                .findIncident(

                        incidentId
                );


        return dispatchTaskRepository

                .findByIncidentIdOrderByCreatedAtDesc(

                        incidentId
                );
    }


    @Transactional
    public DispatchTask updateStatus(

            Long taskId,

            UpdateTaskStatusRequest request

    ) {

        DispatchTask task =

                dispatchTaskRepository

                        .findById(

                                taskId
                        )

                        .orElseThrow(

                                () ->

                                        new ResourceNotFoundException(

                                                "Dispatch task not found: "

                                                        + taskId
                                        )
                        );


        LocalDateTime now =

                LocalDateTime.now();


        task.setStatus(

                request

                        .status()
        );


        task.setFeedback(

                request

                        .feedback()
        );


        if (

                request

                        .status()

                        == TaskStatus.DEPARTED

                        &&

                        task

                                .getDepartedAt()

                                == null

        ) {

            task.setDepartedAt(

                    now
            );
        }


        if (

                (

                        request

                                .status()

                                == TaskStatus.ARRIVED

                                ||

                                request

                                        .status()

                                        == TaskStatus.PROCESSING

                )

                        &&

                        task

                                .getArrivedAt()

                                == null

        ) {

            task.setArrivedAt(

                    now
            );
        }


        if (

                request

                        .status()

                        == TaskStatus.PROCESSING

        ) {

            incidentService

                    .updateStatus(

                            task

                                    .getIncidentId(),

                            IncidentStatus.PROCESSING
                    );
        }


        if (

                request

                        .status()

                        == TaskStatus.COMPLETED

        ) {

            task.setCompletedAt(

                    now
            );


            incidentService

                    .updateStatus(

                            task

                                    .getIncidentId(),

                            IncidentStatus.CLEARED
                    );
        }


        syncEmergencyVehicleStatus(

                task,

                request

                        .status()
        );


        DispatchTask saved =

                dispatchTaskRepository

                        .save(

                                task
                        );


        operationLogService

                .record(

                        task

                                .getReceiverUserId(),

                        "UPDATE_TASK_STATUS",

                        "DispatchTask",

                        saved

                                .getId()

                                .toString(),

                        null,

                        saved

                                .getStatus()

                                .name()
                );


        realtimeService

                .publish(

                        "DISPATCH_TASK_UPDATED",

                        Map.of(

                                "taskId",

                                saved

                                        .getId(),


                                "incidentId",

                                saved

                                        .getIncidentId(),


                                "status",

                                saved

                                        .getStatus()
                        )
                );


        return saved;
    }


    private DispatchTask createOne(

            Incident incident,

            TaskType taskType,

            Long receiverUserId,

            Long assignedByUserId,

            Boolean vehicleRequired,

            String vehicleType,

            String advice

    ) {

        if (taskType == null) {

            throw new BadRequestException(

                    "taskType is required"
            );
        }


        if (receiverUserId != null) {

            validateReceiver(

                    receiverUserId
            );


            boolean duplicate =

                    dispatchTaskRepository

                            .existsByIncidentIdAndTaskTypeAndReceiverUserIdAndStatusIn(

                                    incident

                                            .getId(),

                                    taskType,

                                    receiverUserId,

                                    ACTIVE_STATUSES
                            );


            if (duplicate) {

                throw new BadRequestException(

                        "该人员已经有同类型的未完成调度任务，"

                                + "receiverUserId="

                                + receiverUserId
                );
            }
        }


        DispatchTask task =

                new DispatchTask();


        task.setTaskNo(

                generateTaskNo()
        );


        task.setIncidentId(

                incident

                        .getId()
        );


        task.setTaskType(

                taskType
        );


        task.setReceiverUserId(

                receiverUserId
        );


        task.setAssignedByUserId(

                assignedByUserId
        );


        task.setVehicleRequired(

                Boolean.TRUE.equals(

                        vehicleRequired
                )
        );


        task.setVehicleType(

                trimToNull(

                        vehicleType
                )
        );


        task.setLocationName(

                incident

                        .getLocationName()
        );


        task.setRiskLevel(

                incident

                        .getRiskLevel()
        );


        task.setAdvice(

                firstNonBlank(

                        /*
                         * 指挥人员在创建调度任务时
                         * 手动输入的任务说明优先。
                         */
                        advice,


                        /*
                         * 如需任务说明，
                         * 使用事故已有建议。
                         */
                        incident

                                .getSuggestion()
                )
        );


        task.setStatus(

                TaskStatus.DISPATCHED
        );


        DispatchTask saved =

                dispatchTaskRepository

                        .save(

                                task
                        );


        if (receiverUserId != null) {

            notificationService

                    .send(

                            receiverUserId,

                            NotificationChannel.SYSTEM,

                            "新的调度任务",

                            "任务 "

                                    + saved

                                    .getTaskNo()

                                    + "，事故编号 "

                                    + incident

                                    .getIncidentNo()
                    );
        }


        operationLogService

                .record(

                        assignedByUserId,

                        "CREATE_DISPATCH_TASK",

                        "DispatchTask",

                        saved

                                .getId()

                                .toString(),

                        null,

                        saved

                                .getTaskNo()
                );


        realtimeService

                .publish(

                        "DISPATCH_TASK_CREATED",

                        Map.of(

                                "taskId",

                                saved

                                        .getId(),


                                "incidentId",

                                incident

                                        .getId(),


                                "status",

                                saved

                                        .getStatus()
                        )
                );


        return saved;
    }


    private List<ClearanceRescueTaskResponse>
    findMyTasksByStatuses(

            Long receiverUserId,

            List<TaskStatus> statuses

    ) {

        List<DispatchTask> tasks =

                receiverUserId == null

                        ? dispatchTaskRepository

                        .findByStatusInOrderByCreatedAtDesc(

                                statuses
                        )

                        : dispatchTaskRepository

                        .findByReceiverUserIdAndStatusInOrderByCreatedAtDesc(

                                receiverUserId,

                                statuses
                        );


        return tasks

                .stream()

                .map(

                        this::

                                toClearanceRescueTaskResponse
                )

                .toList();
    }


    /**
     * 构建清障车或救护车任务详情。
     *
     * 保留现有业务逻辑：
     *
     * 1. 指挥人员继续手动选择车辆；
     *
     * 2. ETA 计算和展示逻辑不变；
     *
     * 3. 车辆调度逻辑不变；
     *
     * 4. 只把 navigationUrl 改为：
     *
     * 车辆所在医院/救援中心
     *          ↓
     *       事故地点
     */
    private ClearanceRescueTaskResponse
    toClearanceRescueTaskResponse(

            DispatchTask task

    ) {

        Incident incident =

                incidentService

                        .findIncident(

                                task

                                        .getIncidentId()
                        );


        /*
         * 原来的事故地图对象。
         *
         * 默认 navigationUrl
         * 只是事故位置 marker。
         */
        MapLocationResponse incidentMap =

                mapService

                        .fromStoredIncident(

                                incident,

                                incident

                                        .getBaiduLongitude()

                                        == null

                                        ? "尚未生成百度地图坐标"

                                        : "已生成百度地图坐标"
                        );


        /*
         * 创建车辆起点到事故终点
         * 的驾车路线。
         */
        String drivingNavigationUrl =

                buildVehicleToIncidentNavigationUrl(

                        task,

                        incident
                );


        /*
         * 保留原地图返回内容，
         * 只替换 navigationUrl。
         */
        MapLocationResponse
                mapWithDrivingNavigation =

                replaceNavigationUrl(

                        incidentMap,

                        drivingNavigationUrl
                );


        return ClearanceRescueTaskResponse

                .from(

                        task,

                        incident,

                        mapWithDrivingNavigation,

                        null
                );
    }


    /**
     * 根据指挥人员手动选择的车辆，
     * 生成：
     *
     * 医院/救援中心
     *       ↓
     * 事故地点
     *
     * 的百度驾车路线。
     *
     * 不自动选择车辆。
     *
     * 不重新计算 ETA。
     *
     * 不修改调度状态。
     */
    private String
    buildVehicleToIncidentNavigationUrl(

            DispatchTask task,

            Incident incident

    ) {

        /*
         * 起点：
         *
         * 手动调度时保存的
         * 车辆百度坐标。
         */
        MapPointResponse origin =

                resolveVehicleStartBaiduPoint(

                        task
                );


        /*
         * 终点：
         *
         * 调度任务保存的
         * 事故目标百度坐标。
         */
        MapPointResponse destination =

                resolveIncidentTargetBaiduPoint(

                        task,

                        incident
                );


        /*
         * 坐标不完整时，
         * 不生成路线。
         *
         * 后续继续使用原事故 marker。
         */
        if (

                origin == null

                        ||

                        destination == null

        ) {

            return "";
        }


        /*
         * 获取医院或救援中心名称。
         */
        String originName =

                resolveVehicleOriginName(

                        task
                );


        /*
         * 获取事故地点名称。
         */
        String destinationName =

                firstNonBlank(

                        incident

                                .getMapFormattedAddress(),

                        incident

                                .getAddress(),

                        incident

                                .getLocationName(),

                        "事故地点"
                );


        return mapService

                .buildDrivingNavigationUrl(

                        origin

                                .longitude(),

                        origin

                                .latitude(),

                        originName,


                        destination

                                .longitude(),

                        destination

                                .latitude(),

                        destinationName
                );
    }


    /**
     * 获取手动调度时保存的
     * 车辆百度起点坐标。
     *
     * 新任务优先使用：
     *
     * dispatch_tasks
     * .vehicle_start_baidu_longitude
     *
     * dispatch_tasks
     * .vehicle_start_baidu_latitude
     *
     * 旧任务没有坐标快照时，
     * 再查询已绑定车辆。
     */
    private MapPointResponse
    resolveVehicleStartBaiduPoint(

            DispatchTask task

    ) {

        /*
         * 优先使用任务保存的
         * 车辆起点坐标。
         */
        if (

                task

                        .getVehicleStartBaiduLongitude()

                        != null

                        &&

                        task

                                .getVehicleStartBaiduLatitude()

                                != null

        ) {

            return new MapPointResponse(

                    task

                            .getVehicleStartBaiduLongitude(),

                    task

                            .getVehicleStartBaiduLatitude(),

                    CoordinateType.BD09
            );
        }


        /*
         * 任务没有绑定车辆。
         */
        if (

                task

                        .getEmergencyVehicleId()

                        == null

        ) {

            return null;
        }


        /*
         * 查询指挥人员手动选择的车辆。
         */
        EmergencyVehicle vehicle =

                emergencyVehicleRepository

                        .findById(

                                task

                                        .getEmergencyVehicleId()
                        )

                        .orElse(

                                null
                        );


        if (vehicle == null) {

            return null;
        }


        /*
         * 车辆已经有百度坐标，
         * 直接使用。
         */
        if (

                vehicle

                        .getBaiduLongitude()

                        != null

                        &&

                        vehicle

                                .getBaiduLatitude()

                                != null

        ) {

            return new MapPointResponse(

                    vehicle

                            .getBaiduLongitude(),

                    vehicle

                            .getBaiduLatitude(),

                    CoordinateType.BD09
            );
        }


        /*
         * 原始坐标也不存在。
         */
        if (

                vehicle

                        .getLongitude()

                        == null

                        ||

                        vehicle

                                .getLatitude()

                                == null

        ) {

            return null;
        }


        /*
         * 将车辆原始坐标转换为
         * 百度 BD09 坐标。
         */
        try {

            return mapService

                    .convertToBaidu(

                            vehicle

                                    .getLongitude(),

                            vehicle

                                    .getLatitude(),

                            vehicle

                                    .getCoordinateType()

                                    == null

                                    ? CoordinateType.WGS84

                                    : vehicle

                                    .getCoordinateType()
                    );


        } catch (

                RuntimeException ex

        ) {

            /*
             * 坐标转换失败时，
             * 不让任务详情接口整体报错。
             */
            return null;
        }
    }


    /**
     * 获取事故目标地点
     * 的百度坐标。
     */
    private MapPointResponse
    resolveIncidentTargetBaiduPoint(

            DispatchTask task,

            Incident incident

    ) {

        /*
         * 优先使用调度任务保存的
         * 事故目标位置快照。
         */
        if (

                task

                        .getIncidentTargetBaiduLongitude()

                        != null

                        &&

                        task

                                .getIncidentTargetBaiduLatitude()

                                != null

        ) {

            return new MapPointResponse(

                    task

                            .getIncidentTargetBaiduLongitude(),

                    task

                            .getIncidentTargetBaiduLatitude(),

                    CoordinateType.BD09
            );
        }


        /*
         * 兼容旧任务：
         *
         * 使用 incidents 表
         * 已保存的百度坐标。
         */
        if (

                incident

                        .getBaiduLongitude()

                        != null

                        &&

                        incident

                                .getBaiduLatitude()

                                != null

        ) {

            return new MapPointResponse(

                    incident

                            .getBaiduLongitude(),

                    incident

                            .getBaiduLatitude(),

                    CoordinateType.BD09
            );
        }


        /*
         * 事故没有原始坐标。
         */
        if (

                incident

                        .getLongitude()

                        == null

                        ||

                        incident

                                .getLatitude()

                                == null

        ) {

            return null;
        }


        /*
         * 将事故原始坐标转换为
         * 百度 BD09 坐标。
         */
        try {

            return mapService

                    .convertToBaidu(

                            incident

                                    .getLongitude(),

                            incident

                                    .getLatitude(),

                            incident

                                    .getCoordinateType()

                                    == null

                                    ? CoordinateType.WGS84

                                    : incident

                                    .getCoordinateType()
                    );


        } catch (

                RuntimeException ex

        ) {

            return null;
        }
    }


    /**
     * 获取导航起点名称。
     *
     * emergency_vehicles.current_address：
     *
     * 救护车可填写医院或急救中心；
     *
     * 清障车可填写道路救援中心。
     */
    private String
    resolveVehicleOriginName(

            DispatchTask task

    ) {

        String currentAddress =

                null;


        if (

                task

                        .getEmergencyVehicleId()

                        != null

        ) {

            EmergencyVehicle vehicle =

                    emergencyVehicleRepository

                            .findById(

                                    task

                                            .getEmergencyVehicleId()
                            )

                            .orElse(

                                    null
                            );


            if (vehicle != null) {

                currentAddress =

                        vehicle

                                .getCurrentAddress();
            }
        }


        return firstNonBlank(

                /*
                 * 第一优先级：
                 *
                 * 医院或救援中心名称。
                 */
                currentAddress,


                /*
                 * 第二优先级：
                 *
                 * 车辆名称。
                 */
                task

                        .getEmergencyVehicleName(),


                /*
                 * 第三优先级：
                 *
                 * 车辆编号。
                 */
                task

                        .getEmergencyVehicleNo(),


                /*
                 * 最后兜底名称。
                 */
                "车辆所在医院或救援中心"
        );
    }


    /**
     * 保留原 MapLocationResponse
     * 的全部字段，
     *
     * 只把事故 marker 链接
     * 替换成完整驾车路线。
     */
    private MapLocationResponse
    replaceNavigationUrl(

            MapLocationResponse map,

            String drivingNavigationUrl

    ) {

        /*
         * 没有生成路线时，
         * 保留原来的事故位置链接。
         */
        if (

                map == null

                        ||

                        drivingNavigationUrl

                                == null

                        ||

                        drivingNavigationUrl

                                .isBlank()

        ) {

            return map;
        }


        return new MapLocationResponse(

                map

                        .mapReady(),

                map

                        .message(),


                map

                        .sourceLongitude(),

                map

                        .sourceLatitude(),

                map

                        .sourceCoordinateType(),


                map

                        .baiduLongitude(),

                map

                        .baiduLatitude(),


                map

                        .formattedAddress(),

                map

                        .semanticDescription(),


                map

                        .province(),

                map

                        .city(),

                map

                        .district(),

                map

                        .town(),

                map

                        .street(),

                map

                        .streetNumber(),

                map

                        .business(),

                map

                        .adcode(),


                /*
                 * 新导航链接。
                 */
                drivingNavigationUrl
        );
    }


    /**
     * 同步应急车辆状态。
     */
    private void syncEmergencyVehicleStatus(

            DispatchTask task,

            TaskStatus status

    ) {

        if (

                task

                        .getEmergencyVehicleId()

                        == null

        ) {

            return;
        }


        EmergencyVehicle vehicle =

                emergencyVehicleRepository

                        .findById(

                                task

                                        .getEmergencyVehicleId()
                        )

                        .orElse(

                                null
                        );


        if (vehicle == null) {

            return;
        }


        switch (status) {

            case DISPATCHED ->

                    vehicle

                            .setStatus(

                                    VehicleStatus.DISPATCHED
                            );


            case DEPARTED ->

                    vehicle

                            .setStatus(

                                    VehicleStatus.EN_ROUTE
                            );


            case ARRIVED,

                 PROCESSING -> {

                vehicle

                        .setStatus(

                                VehicleStatus.ARRIVED
                        );


                moveVehicleToIncidentTarget(

                        vehicle,

                        task
                );
            }


            case COMPLETED,

                 CANCELLED -> {

                vehicle

                        .setStatus(

                                VehicleStatus.AVAILABLE
                        );


                vehicle

                        .setCurrentTaskId(

                                null
                        );


                moveVehicleToIncidentTarget(

                        vehicle,

                        task
                );
            }
        }


        emergencyVehicleRepository

                .save(

                        vehicle
                );
    }


    /**
     * 车辆到达事故现场后，
     * 更新车辆当前位置。
     */
    private void moveVehicleToIncidentTarget(

            EmergencyVehicle vehicle,

            DispatchTask task

    ) {

        if (

                task

                        .getIncidentTargetLongitude()

                        != null

        ) {

            vehicle

                    .setLongitude(

                            task

                                    .getIncidentTargetLongitude()
                    );
        }


        if (

                task

                        .getIncidentTargetLatitude()

                        != null

        ) {

            vehicle

                    .setLatitude(

                            task

                                    .getIncidentTargetLatitude()
                    );
        }


        if (

                task

                        .getIncidentTargetBaiduLongitude()

                        != null

        ) {

            vehicle

                    .setBaiduLongitude(

                            task

                                    .getIncidentTargetBaiduLongitude()
                    );
        }


        if (

                task

                        .getIncidentTargetBaiduLatitude()

                        != null

        ) {

            vehicle

                    .setBaiduLatitude(

                            task

                                    .getIncidentTargetBaiduLatitude()
                    );
        }
    }


    /**
     * 校验任务接收人。
     */
    private UserAccount validateReceiver(

            Long receiverUserId

    ) {

        UserAccount user =

                userAccountRepository

                        .findById(

                                receiverUserId
                        )

                        .orElseThrow(

                                () ->

                                        new ResourceNotFoundException(

                                                "Receiver user not found: "

                                                        + receiverUserId
                                        )
                        );


        if (

                user

                        .getStatus()

                        != UserStatus.ENABLED

        ) {

            throw new BadRequestException(

                    "Receiver user is disabled: "

                            + receiverUserId
            );
        }


        return user;
    }


    /**
     * 生成任务编号。
     */
    private String generateTaskNo() {

        return "TASK"

                + LocalDateTime

                .now()

                .format(

                        DateTimeFormatter

                                .ofPattern(

                                        "yyyyMMddHHmmss"
                                )
                )

                + UUID

                .randomUUID()

                .toString()

                .substring(

                        0,

                        6
                );
    }


    /**
     * 返回第一个非空字符串。
     */
    private String firstNonBlank(

            String... values

    ) {

        if (values == null) {

            return null;
        }


        for (

                String value

                : values

        ) {

            if (

                    value != null

                            &&

                            !value.isBlank()

            ) {

                return value.trim();
            }
        }


        return null;
    }


    /**
     * 去除字符串前后空格，
     * 空字符串转换成 null。
     */
    private String trimToNull(

            String value

    ) {

        return value == null

                ||

                value

                        .trim()

                        .isEmpty()

                ? null

                : value.trim();
    }
}
