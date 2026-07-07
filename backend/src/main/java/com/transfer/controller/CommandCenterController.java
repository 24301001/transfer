package com.transfer.controller;

import com.transfer.dto.CommandDispatchRequest;
import com.transfer.dto.CommandIncidentDetailResponse;
import com.transfer.dto.CommandIncidentSummaryResponse;
import com.transfer.dto.IncidentMapMarkerResponse;
import com.transfer.dto.MapLocationResponse;
import com.transfer.dto.ResponderResponse;
import com.transfer.dto.SupportDecisionRequest;
import com.transfer.enums.IncidentStatus;
import com.transfer.enums.RiskLevel;
import com.transfer.enums.UserRole;
import com.transfer.model.DispatchTask;
import com.transfer.model.Incident;
import com.transfer.service.CommandCenterService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/command-center")
public class CommandCenterController {

    private final CommandCenterService
            commandCenterService;

    public CommandCenterController(
            CommandCenterService commandCenterService
    ) {
        this.commandCenterService =
                commandCenterService;
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
     * FR-19：调度处理。
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
}
