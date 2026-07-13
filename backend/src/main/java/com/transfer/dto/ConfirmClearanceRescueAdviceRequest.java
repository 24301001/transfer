package com.transfer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ConfirmClearanceRescueAdviceRequest(

        /**
         * 本次确认的是哪一条 AI 建议。
         */
        @NotNull
        Long adviceId,

        /**
         * 最终确认内容。
         *
         * 可以是 AI 原文，也可以是人工修改后的文本。
         */
        @NotBlank
        @Size(max = 4000)
        String finalAdvice,

        /**
         * 当前指挥人员 ID。
         */
        @NotNull
        Long confirmedByUserId
) {
}
