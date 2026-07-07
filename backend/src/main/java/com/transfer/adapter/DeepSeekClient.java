package com.transfer.adapter;

public interface DeepSeekClient {
    String explain(PredictionOutcome outcome, String locationName, String description);
}
