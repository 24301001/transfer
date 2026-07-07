package com.transfer.adapter;

import org.springframework.stereotype.Component;

@Component
public class DefaultDeepSeekClient implements DeepSeekClient {

    @Override
    public String explain(PredictionOutcome outcome, String locationName, String description) {
        return "The incident at " + locationName
                + " is classified as " + outcome.accidentType()
                + " with " + outcome.riskLevel()
                + " risk. Estimated congestion lasts about "
                + outcome.congestionDurationMinutes()
                + " minutes, and road recovery may take about "
                + outcome.recoveryDurationMinutes()
                + " minutes. Suggested action: "
                + outcome.suggestions();
    }
}
