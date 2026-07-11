package com.transfer.prediction;

import java.util.List;

/**
 * 算法 A（事故类型识别）的输出结果。
 */
public record AccidentTypeResult(
        String accidentType,
        Double confidence,
        String modelVersion,
        List<String> imageEvidence,
        String evidenceSummary
) {
}
