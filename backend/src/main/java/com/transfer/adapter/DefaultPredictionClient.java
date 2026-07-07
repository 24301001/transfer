package com.transfer.adapter;

import com.transfer.dto.PredictionRequest;
import com.transfer.enums.RiskLevel;
import org.springframework.stereotype.Component;

@Component
public class DefaultPredictionClient implements PredictionClient {

    @Override
    public PredictionOutcome predict(PredictionRequest request) {
        int score = 0;
        String text = normalize(request.description()) + " " + normalize(request.accidentType());

        if (containsAny(text, "injury", "injured", "casualty", "fatal", "hurt", "受伤", "伤亡")) {
            score += 3;
        }
        if (containsAny(text, "rollover", "fire", "explosion", "closed", "侧翻", "起火", "爆炸", "封闭")) {
            score += 3;
        }
        if (request.occupiedLanes() != null) {
            score += Math.min(request.occupiedLanes(), 4);
        }
        if (request.trafficFlow() != null && request.trafficFlow() >= 1500) {
            score += 2;
        }
        if (containsAny(normalize(request.weather()), "rain", "snow", "fog", "雨", "雪", "雾")) {
            score += 1;
        }

        RiskLevel riskLevel = score >= 7 ? RiskLevel.CRITICAL
                : score >= 5 ? RiskLevel.HIGH
                : score >= 3 ? RiskLevel.MEDIUM
                : RiskLevel.LOW;
        String accidentType = request.accidentType() != null && !request.accidentType().isBlank()
                ? request.accidentType()
                : inferAccidentType(text);
        int congestion = switch (riskLevel) {
            case LOW -> 20;
            case MEDIUM -> 45;
            case HIGH -> 90;
            case CRITICAL -> 150;
        };
        int recovery = congestion + switch (riskLevel) {
            case LOW -> 15;
            case MEDIUM -> 30;
            case HIGH -> 60;
            case CRITICAL -> 100;
        };
        String suggestions = buildSuggestion(riskLevel);
        return new PredictionOutcome(accidentType, riskLevel, congestion, recovery, 0.82, "fallback-rule-v1", suggestions);
    }

    private String inferAccidentType(String text) {
        if (containsAny(text, "rear", "追尾")) {
            return "Rear-end collision";
        }
        if (containsAny(text, "rollover", "侧翻")) {
            return "Vehicle rollover";
        }
        if (containsAny(text, "closed", "block", "封闭", "占道")) {
            return "Lane blockage";
        }
        return "Traffic collision";
    }

    private String buildSuggestion(RiskLevel riskLevel) {
        return switch (riskLevel) {
            case LOW -> "Place warning signs and guide vehicles to pass slowly.";
            case MEDIUM -> "Place warning signs, protect the scene, and consider local traffic diversion.";
            case HIGH -> "Dispatch police and clearance resources, close affected lanes, and notify nearby units.";
            case CRITICAL -> "Start emergency response, dispatch police, rescue, clearance and medical resources, and expand traffic control.";
        };
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase();
    }
}
