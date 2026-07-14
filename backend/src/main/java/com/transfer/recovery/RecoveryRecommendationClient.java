package com.transfer.recovery;

import com.transfer.dto.PredictionModuleResultRequest;
import com.transfer.model.Incident;
import com.transfer.model.IncidentAttachment;

import java.util.List;
import java.util.Optional;

public interface RecoveryRecommendationClient {

    Optional<RecoveryRecommendationResult> recommend(
            Incident incident,
            List<IncidentAttachment> attachments,
            PredictionModuleResultRequest predictionResult
    );

    boolean isConfigured();

    boolean healthCheck();
}
