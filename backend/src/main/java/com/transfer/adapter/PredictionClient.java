package com.transfer.adapter;

import com.transfer.dto.PredictionRequest;
import com.transfer.dto.PredictionSubmitResponse;

public interface PredictionClient {
    PredictionSubmitResponse submit(PredictionRequest request);
}