package com.teamflow.core.outbox.service;

import com.teamflow.core.common.id.ReadableIdGenerator;
import com.teamflow.core.common.id.ResourceType;
import com.teamflow.core.outbox.domain.OutboxEvent;
import com.teamflow.core.outbox.mapper.OutboxEventMapper;
import com.teamflow.core.task.event.TaskAssignmentChanged;
import com.teamflow.core.task.service.TaskEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** 把任务领域事件与业务变更原子地写入同一数据库。 */
@Service
public class OutboxTaskEventPublisher implements TaskEventPublisher {

    private final OutboxEventMapper mapper;
    private final ReadableIdGenerator idGenerator;

    public OutboxTaskEventPublisher(
            OutboxEventMapper mapper,
            ReadableIdGenerator idGenerator
    ) {
        this.mapper = Objects.requireNonNull(mapper, "Outbox Mapper 不能为 null");
        this.idGenerator = Objects.requireNonNull(
                idGenerator,
                "编号生成器不能为 null"
        );
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishAssignmentChanged(TaskAssignmentChanged event) {
        OutboxEvent outboxEvent = OutboxEvent.pending(
                idGenerator.nextId(ResourceType.OUTBOX_EVENT),
                event
        );
        if (mapper.insert(outboxEvent) != 1) {
            throw new IllegalStateException(
                    "新增 Outbox 事件时受影响行数必须为 1"
            );
        }
    }
}
