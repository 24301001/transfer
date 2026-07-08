package com.transfer.service;

import com.transfer.dto.PredictionModuleResultRequest;
import com.transfer.enums.RiskLevel;
import com.transfer.model.Incident;
import org.springframework.stereotype.Service;

@Service
public class FormalDispositionPlanService {

    public String buildFormalPlan(
            Incident incident,
            PredictionModuleResultRequest request,
            String riskFactors
    ) {
        StringBuilder builder = new StringBuilder();

        builder.append("【正式预测处置方案】");
        builder.append("预测结论：")
                .append(safe(request.accidentType()))
                .append("，风险等级")
                .append(request.riskLevel())
                .append("，预计拥堵")
                .append(request.congestionDurationMinutes())
                .append("分钟，道路恢复约")
                .append(request.recoveryDurationMinutes())
                .append("分钟。 ");

        builder.append("指挥调度：")
                .append(dispatchAdvice(request.riskLevel(), incident))
                .append(" ");

        builder.append("清障救援：")
                .append(clearanceAdvice(request.riskLevel(), incident, request))
                .append(" ");

        builder.append("绕行疏导：")
                .append(diversionAdvice(request.riskLevel(), incident, request))
                .append(" ");

        builder.append("现场安全：")
                .append(safetyAdvice(request.riskLevel(), incident, request))
                .append(" ");

        if (riskFactors != null && !riskFactors.isBlank()) {
            builder.append("风险因子：")
                    .append(riskFactors)
                    .append("。 ");
        }

        if (request.suggestion() != null
                && !request.suggestion().isBlank()) {
            builder.append("数据模块补充：")
                    .append(request.suggestion().trim())
                    .append(" ");
        }

        return limit(builder.toString(), 980);
    }

    private String dispatchAdvice(
            RiskLevel riskLevel,
            Incident incident
    ) {
        return switch (riskLevel) {
            case LOW -> "纳入指挥中心事故列表，保持关注，暂不建议扩大调度。";
            case MEDIUM -> "建议派警核查现场，视车道占用情况安排交通疏导。";
            case HIGH -> "建议优先派警到场，通知清障车辆待命，必要时联动救援力量。";
            case CRITICAL -> "建议启动高优先级响应，联动交警、清障、救援和医疗资源。";
        };
    }

    private String clearanceAdvice(
            RiskLevel riskLevel,
            Incident incident,
            PredictionModuleResultRequest request
    ) {
        boolean occupied = incident.getOccupiedLanes() != null
                && incident.getOccupiedLanes() >= 1;
        boolean longRecovery = request.recoveryDurationMinutes() != null
                && request.recoveryDurationMinutes() >= 60;

        if (riskLevel == RiskLevel.CRITICAL) {
            return "同步准备拖车、救援和医疗支援，优先处理伤亡、被困、起火或泄漏风险。";
        }
        if (riskLevel == RiskLevel.HIGH || occupied || longRecovery) {
            return "建议调度清障车辆，优先恢复主线或关键车道通行。";
        }
        return "暂以现场确认和轻微事故快处为主，清障资源按需介入。";
    }

    private String diversionAdvice(
            RiskLevel riskLevel,
            Incident incident,
            PredictionModuleResultRequest request
    ) {
        boolean longCongestion = request.congestionDurationMinutes() != null
                && request.congestionDurationMinutes() >= 45;
        boolean multiLane = incident.getOccupiedLanes() != null
                && incident.getOccupiedLanes() >= 2;

        if (riskLevel == RiskLevel.CRITICAL || longCongestion || multiLane) {
            return "建议发布绕行提示，必要时实施分流、限速或临时封控受影响车道。";
        }
        if (riskLevel == RiskLevel.HIGH || riskLevel == RiskLevel.MEDIUM) {
            return "建议加强事故点上游提示，引导车辆减速、有序并线。";
        }
        return "建议提示车辆减速通过，持续观察拥堵变化。";
    }

    private String safetyAdvice(
            RiskLevel riskLevel,
            Incident incident,
            PredictionModuleResultRequest request
    ) {
        String text = (safe(incident.getDescription())
                + " "
                + safe(request.accidentType()))
                .toLowerCase();

        if (containsAny(text, "受伤", "伤亡", "死亡", "流血", "被困")) {
            return "提醒现场人员先撤离至安全区域，伤者由医疗人员处置，避免二次事故。";
        }
        if (containsAny(text, "起火", "冒烟", "泄漏", "爆炸", "危化")) {
            return "扩大安全距离，禁止无关人员靠近，等待专业救援处理危险源。";
        }
        if (riskLevel == RiskLevel.CRITICAL || riskLevel == RiskLevel.HIGH) {
            return "设置上游预警和现场隔离，防止围观和二次碰撞。";
        }
        return "保持警示标志和现场隔离，避免人员停留在车道内。";
    }

    private boolean containsAny(String text, String... words) {
        if (text == null) {
            return false;
        }
        for (String word : words) {
            if (word != null && text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String text = value.replaceAll("\\s+", " ").trim();
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 1) + "…";
    }

    private String safe(String value) {
        return value == null || value.isBlank()
                ? "未提供"
                : value.trim();
    }
}
