package com.transfer.event;

/**
 * 市民事故上报事务提交后触发的后台处理事件。
 */
public record PublicIncidentSubmittedEvent(
        Long incidentId,
        Long operatorUserId
) {
}
