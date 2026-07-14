package com.transfer.service;

import com.transfer.dto.IncidentArrivalEstimateResponse;
import com.transfer.enums.RiskLevel;
import com.transfer.model.Incident;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class ArrivalEstimateService {

    public IncidentArrivalEstimateResponse estimateFor(
            Incident incident
    ) {
        int minutes = estimateMinutes(incident);
        String text = "预计交警约 "
                + minutes
                + " 分钟到达，具体以指挥中心实际派警为准。";

        return new IncidentArrivalEstimateResponse(
                incident.getId(),
                minutes,
                text
        );
    }

    public void applyEstimate(Incident incident) {
        IncidentArrivalEstimateResponse response =
                estimateFor(incident);

        incident.setEstimatedPoliceArrivalMinutes(
                response.estimatedPoliceArrivalMinutes()
        );
        incident.setPoliceArrivalText(
                response.estimatedPoliceArrivalText()
        );
    }

    private int estimateMinutes(Incident incident) {
        int minutes = 12;

        if (incident.getRiskLevel() == RiskLevel.CRITICAL) {
            minutes -= 3;
        } else if (incident.getRiskLevel() == RiskLevel.HIGH) {
            minutes -= 2;
        }

        if (containsAny(
                join(
                        incident.getDescription(),
                        incident.getInitialAccidentType(),
                        incident.getConfirmedAccidentType()
                ),
                "受伤",
                "伤亡",
                "死亡",
                "被困",
                "流血",
                "起火"
        )) {
            minutes -= 2;
        }

        if (incident.getOccupiedLanes() != null
                && incident.getOccupiedLanes() >= 2) {
            minutes -= 1;
        }

        if (incident.getLongitude() == null
                || incident.getLatitude() == null) {
            minutes += 4;
        }

        if (containsAny(
                join(incident.getWeather(), incident.getRoadStatus()),
                "雨",
                "雪",
                "雾",
                "结冰",
                "拥堵",
                "缓行"
        )) {
            minutes += 4;
        }

        if (containsAny(
                join(incident.getRoadLevel(), incident.getRoadName()),
                "高速",
                "快速路",
                "主干道"
        )) {
            minutes -= 1;
        }

        return Math.max(6, Math.min(minutes, 35));
    }

    private boolean containsAny(String text, String... words) {
        String normalized = text == null
                ? ""
                : text.toLowerCase(Locale.ROOT);
        for (String word : words) {
            if (word != null
                    && normalized.contains(
                    word.toLowerCase(Locale.ROOT)
            )) {
                return true;
            }
        }
        return false;
    }

    private String join(String... values) {
        if (values == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                builder.append(value).append(' ');
            }
        }
        return builder.toString();
    }
}
