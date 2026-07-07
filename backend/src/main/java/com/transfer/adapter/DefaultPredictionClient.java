package com.transfer.adapter;

import com.transfer.dto.PredictionRequest;
import com.transfer.dto.PredictionSubmitResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Component
public class DefaultPredictionClient implements PredictionClient {

    private final String submitUrl;
    private final RestTemplate restTemplate = new RestTemplate();

    public DefaultPredictionClient(@Value("${app.prediction-module.submit-url:}") String submitUrl) {
        this.submitUrl = submitUrl == null ? "" : submitUrl.trim();
    }

    @Override
    public PredictionSubmitResponse submit(PredictionRequest request) {
        if (submitUrl.isBlank()) {
            return new PredictionSubmitResponse(
                    false,
                    "PREDICTION_MODULE_NOT_CONFIGURED",
                    "Prediction module submit URL is not configured. The payload is returned for integration testing.",
                    null,
                    request,
                    null
            );
        }
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(submitUrl, request, String.class);
            return new PredictionSubmitResponse(
                    response.getStatusCode().is2xxSuccessful(),
                    response.getStatusCode().is2xxSuccessful() ? "SUBMITTED" : "SUBMIT_FAILED",
                    "Prediction request has been submitted to the data prediction module.",
                    response.getHeaders().getFirst("X-Trace-Id"),
                    request,
                    response.getBody()
            );
        } catch (HttpStatusCodeException ex) {
            return new PredictionSubmitResponse(
                    false,
                    "SUBMIT_FAILED",
                    "Prediction module returned HTTP " + ex.getStatusCode().value(),
                    ex.getResponseHeaders() == null ? null : ex.getResponseHeaders().getFirst("X-Trace-Id"),
                    request,
                    ex.getResponseBodyAsString()
            );
        } catch (ResourceAccessException ex) {
            return new PredictionSubmitResponse(
                    false,
                    "PREDICTION_MODULE_UNAVAILABLE",
                    ex.getMessage(),
                    null,
                    request,
                    null
            );
        }
    }
}