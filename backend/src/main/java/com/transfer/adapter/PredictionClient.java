package com.transfer.adapter;

import com.transfer.dto.PredictionRequest;

public interface PredictionClient {
    PredictionOutcome predict(PredictionRequest request);
}
