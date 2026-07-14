package com.transfer.dispatch;

import com.transfer.model.Incident;
import com.transfer.model.IncidentAttachment;
import com.transfer.dto.PredictionModuleResultRequest;

import java.util.List;
import java.util.Optional;

public interface DispatchRecommendationClient {

    Optional<DispatchRecommendationResult> recommend(
            Incident incident,
            List<IncidentAttachment> attachments,
            PredictionModuleResultRequest predictionResult
    );

    boolean isConfigured();

    boolean healthCheck();
}
