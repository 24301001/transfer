package com.transfer.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.transfer.dto.AiAttachmentResponse;
import com.transfer.dto.CommandDispatchRequest;
import com.transfer.dto.CommandIncidentDetailResponse;
import com.transfer.dto.CommandIncidentSummaryResponse;
import com.transfer.dto.DispatchVehicleRequest;
import com.transfer.dto.IncidentMapMarkerResponse;
import com.transfer.dto.MapLocationResponse;
import com.transfer.dto.PredictionDisplayResponse;
import com.transfer.dto.ResponderResponse;
import com.transfer.dto.SupportDecisionRequest;
import com.transfer.dto.UpdateVehicleLocationRequest;
import com.transfer.dto.VehicleEtaResponse;
import com.transfer.dto.VehicleTrackResponse;
import com.transfer.enums.IncidentStatus;
import com.transfer.enums.RiskLevel;
import com.transfer.enums.UserRole;
import com.transfer.enums.VehicleStatus;
import com.transfer.enums.VehicleType;
import com.transfer.model.DispatchTask;
import com.transfer.model.EmergencyVehicle;
import com.transfer.model.Incident;
import com.transfer.security.RequestSecurityAttributes;
import com.transfer.security.RequireRoles;
import com.transfer.service.CommandCenterService;
import com.transfer.service.EmergencyVehicleDispatchService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequireRoles({UserRole.ADMIN, UserRole.COMMAND_CENTER})
@RequestMapping("/api/v1/command-center")
public class CommandCenterController {

    private final CommandCenterService commandCenterService;

    private final EmergencyVehicleDispatchService
            emergencyVehicleDispatchService;

    public CommandCenterController(
            CommandCenterService commandCenterService,
            EmergencyVehicleDispatchService emergencyVehicleDispatchService
    ) {
        this.commandCenterService =
                commandCenterService;
        this.emergencyVehicleDispatchService =
                emergencyVehicleDispatchService;
    }

    /**
     * FR-13：查看所有事故列表。
     *
     * 同时包含：
     * FR-15 事故类型
     * FR-16 风险等级
     * FR-17 拥堵持续时间
     * FR-18 是否需要支援
     */
    @GetMapping("/incidents")
    public Page<CommandIncidentSummaryResponse>
    findIncidents(
            @RequestParam(required = false)
            IncidentStatus status,

            @RequestParam(required = false)
            RiskLevel riskLevel,

            @RequestParam(required = false)
            Boolean supportRequired,

            @RequestParam(required = false)
            String keyword,

            Pageable pageable
    ) {
        return commandCenterService.findIncidents(
                status,
                riskLevel,
                supportRequired,
                keyword,
                pageable
        );
    }

    /**
     * FR-14：获取所有事故百度地图点位。
     */
    @GetMapping("/incidents/map-markers")
    public List<IncidentMapMarkerResponse>
    findMapMarkers(
            @RequestParam(required = false)
            IncidentStatus status,

            @RequestParam(required = false)
            RiskLevel riskLevel,

            @RequestParam(required = false)
            Boolean supportRequired,

            @RequestParam(required = false)
            String keyword
    ) {
        return commandCenterService.findMapMarkers(
                status,
                riskLevel,
                supportRequired,
                keyword
        );
    }

    /**
     * 查看事故完整详情。
     */
    @GetMapping("/incidents/{incidentId}")
    public CommandIncidentDetailResponse findDetail(
            @PathVariable
            Long incidentId
    ) {
        return commandCenterService.findDetail(
                incidentId
        );
    }

    /**
     * FR-14：单独查看事故位置。
     */
    @GetMapping("/incidents/{incidentId}/location")
    public MapLocationResponse findLocation(
            @PathVariable
            Long incidentId
    ) {
        return commandCenterService.findLocation(
                incidentId
        );
    }

    /**
     * FR-18：指挥人员人工修改支援判断。
     */
    @PutMapping(
            "/incidents/{incidentId}/support-decision"
    )
    public Incident updateSupportDecision(
            @PathVariable
            Long incidentId,

            @Valid
            @RequestBody
            SupportDecisionRequest request
    ) {
        return commandCenterService
                .updateSupportDecision(
                        incidentId,
                        request
                );
    }

    /**
     * 指挥中心查看最新预测结果表。
     *
     * 返回内容包含模型直接预测结果，以及硅基流动生成的自然语言解释 explanation。
     */
    @GetMapping(
            "/incidents/{incidentId}/prediction-result/latest"
    )
    public PredictionDisplayResponse findLatestPredictionResult(
            @PathVariable
            Long incidentId
    ) {
        return commandCenterService
                .findLatestPredictionResult(
                        incidentId
                );
    }

    /**
     * 指挥中心手动调用 AI，基于最新预测结果生成自然语言解释。
     *
     * 该接口不生成清障处置建议，也不参与车辆调度决策。
     */
    @PostMapping(
            "/incidents/{incidentId}/prediction-explanation/generate"
    )
    public PredictionDisplayResponse regeneratePredictionExplanation(
            @PathVariable
            Long incidentId,

            HttpServletRequest servletRequest
    ) {
        Long operatorUserId = RequestSecurityAttributes
                .requireAuthenticatedUser(servletRequest)
                .userId();
        return commandCenterService
                .regeneratePredictionExplanation(
                        incidentId,
                        operatorUserId
                );
    }

    /**
     * FR-19：原有调度处理。
     */
    @PostMapping(
            "/incidents/{incidentId}/dispatch"
    )
    public ResponseEntity<List<DispatchTask>>
    dispatch(
            @PathVariable
            Long incidentId,

            @Valid
            @RequestBody
            CommandDispatchRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        commandCenterService.dispatch(
                                incidentId,
                                request
                        )
                );
    }

    /**
     * 查看指定事故已经产生的调度任务。
     */
    @GetMapping(
            "/incidents/{incidentId}/dispatch-tasks"
    )
    public List<DispatchTask>
    findIncidentDispatchTasks(
            @PathVariable
            Long incidentId
    ) {
        return commandCenterService
                .findIncidentDispatchTasks(
                        incidentId
                );
    }

    /**
     * 查询可以调度的人员。
     *
     * 示例：
     * /responders
     * /responders?role=FIELD_OFFICER
     * /responders?role=RESCUE_WORKER
     */
    @GetMapping("/responders")
    public List<ResponderResponse> findResponders(
            @RequestParam(required = false)
            UserRole role
    ) {
        return commandCenterService
                .findResponders(role);
    }

    /**
     * 查询救护车、清障车车辆库。
     *
     * 示例：
     * /vehicles
     * /vehicles?vehicleType=AMBULANCE
     * /vehicles?vehicleType=CLEARANCE_TRUCK&status=AVAILABLE
     */
    @GetMapping("/vehicles")
    public List<EmergencyVehicle> findVehicles(
            @RequestParam(required = false)
            VehicleType vehicleType,

            @RequestParam(required = false)
            VehicleStatus status
    ) {
        return emergencyVehicleDispatchService
                .findVehicles(
                        vehicleType,
                        status
                );
    }

    /**
     * 指挥中心点击某个事故后，查询某类车辆到达时间。
     *
     * 示例：
     * /incidents/1/vehicle-etas?vehicleType=AMBULANCE
     * /incidents/1/vehicle-etas?vehicleType=CLEARANCE_TRUCK
     */
    @GetMapping(
            "/incidents/{incidentId}/vehicle-etas"
    )
    public List<VehicleEtaResponse> estimateVehicleEtas(
            @PathVariable
            Long incidentId,

            @RequestParam
            VehicleType vehicleType
    ) {
        return emergencyVehicleDispatchService
                .estimateForIncident(
                        incidentId,
                        vehicleType
                );
    }

    /**
     * 调度救护车/清障车。
     *
     * 指挥中心先查看 ETA 列表，再手动选择 vehicleId 调度。
     * 后端不做 AI 调度，也不自动选择最快车辆。
     */
    @PostMapping(
            "/incidents/{incidentId}/vehicle-dispatch"
    )
    public ResponseEntity<DispatchTask> dispatchVehicle(
            @PathVariable
            Long incidentId,

            @Valid
            @RequestBody
            DispatchVehicleRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        emergencyVehicleDispatchService
                                .dispatchVehicle(
                                        incidentId,
                                        request
                                )
                );
    }

    /**
     * 查询调度车辆实时轨迹。
     *
     * 前端可以每隔 1～3 秒请求一次这个接口。
     */
    @GetMapping(
            "/dispatch-tasks/{taskId}/vehicle-track"
    )
    public VehicleTrackResponse findVehicleTrack(
            @PathVariable
            Long taskId
    ) {
        return emergencyVehicleDispatchService
                .findVehicleTrack(taskId);
    }

    /**
     * 更新车辆真实 GPS 位置。
     *
     * 后续如果你同伴有 GPS 模拟器，可以调这个接口实时上报车辆位置。
     */
    @PutMapping("/vehicles/{vehicleId}/location")
    public EmergencyVehicle updateVehicleLocation(
            @PathVariable
            Long vehicleId,

            @Valid
            @RequestBody
            UpdateVehicleLocationRequest request
    ) {
        return emergencyVehicleDispatchService
                .updateVehicleLocation(
                        vehicleId,
                        request
                );
    }

    /**
     * 获取事故的 AI 检测附件列表（供指挥中心查看带检测框的图片/视频）。
     * 返回所有已完成 AI 识别的附件及其检测结果。
     */
    @GetMapping("/incidents/{incidentId}/ai-attachments")
    public List<AiAttachmentResponse> findAiAttachments(
            @PathVariable Long incidentId,
            HttpServletRequest request
    ) {
        String accessToken = resolveBearerToken(request);

        return commandCenterService
                .findAiDetectedAttachments(incidentId)
                .stream()
                .map(attachment -> AiAttachmentResponse.from(
                        attachment,
                        buildAiAttachmentPlaybackUrl(
                                incidentId,
                                attachment.getId(),
                                accessToken
                        )
                ))
                .toList();
    }

    /**
     * 通过后端代理输出 YOLO 处理后的图片/视频。
     *
     * <p>支持 Range 请求，视频标签可以分段加载并拖动播放进度。</p>
     */
    @GetMapping("/incidents/{incidentId}/ai-attachments/{attachmentId}/content")
    public ResponseEntity<StreamingResponseBody> streamAiAttachment(
            @PathVariable Long incidentId,
            @PathVariable Long attachmentId,
            @RequestHeader(
                    value = HttpHeaders.RANGE,
                    required = false
            ) String rangeHeader
    ) {
        CommandCenterService.AiAttachmentMediaStream media =
                commandCenterService.openAiAttachmentMedia(
                        incidentId,
                        attachmentId,
                        rangeHeader
                );

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, media.contentType());
        headers.set(HttpHeaders.ACCEPT_RANGES, media.acceptRanges());
        headers.setCacheControl("private, max-age=60");

        if (media.contentLength() >= 0) {
            headers.setContentLength(media.contentLength());
        }
        if (media.contentRange() != null) {
            headers.set(HttpHeaders.CONTENT_RANGE, media.contentRange());
        }
        if (media.etag() != null) {
            headers.set(HttpHeaders.ETAG, media.etag());
        }
        if (media.lastModified() > 0) {
            headers.setLastModified(media.lastModified());
        }

        return ResponseEntity
                .status(media.statusCode())
                .headers(headers)
                .body(media.body());
    }

    /**
     * 指挥中心标记附件为已查看。
     */
    @PutMapping("/incidents/{incidentId}/ai-attachments/{attachmentId}/review")
    public ResponseEntity<Void> reviewAttachment(
            @PathVariable Long incidentId,
            @PathVariable Long attachmentId
    ) {
        commandCenterService.markAttachmentReviewed(incidentId, attachmentId);
        return ResponseEntity.ok().build();
    }
    private String buildAiAttachmentPlaybackUrl(
            Long incidentId,
            Long attachmentId,
            String accessToken
    ) {
        return ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/api/v1/command-center/incidents/{incidentId}/ai-attachments/{attachmentId}/content")
                .queryParam("access_token", accessToken)
                .buildAndExpand(incidentId, attachmentId)
                .encode()
                .toUriString();
    }

    private String resolveBearerToken(
            HttpServletRequest request
    ) {
        String authorization = request.getHeader(
                HttpHeaders.AUTHORIZATION
        );

        if (authorization == null
                || !authorization.regionMatches(
                true,
                0,
                "Bearer ",
                0,
                7
        )) {
            return "";
        }

        return authorization.substring(7).trim();
    }

}
