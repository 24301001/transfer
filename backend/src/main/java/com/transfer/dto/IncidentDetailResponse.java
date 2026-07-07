package com.transfer.dto;

import com.transfer.model.DispatchTask;
import com.transfer.model.Incident;
import com.transfer.model.IncidentAttachment;
import com.transfer.model.PredictionResult;

import java.util.List;

public record IncidentDetailResponse(
        Incident incident,
        List<IncidentAttachment> attachments,
        List<PredictionResult> predictions,
        List<DispatchTask> dispatchTasks
) {
}
