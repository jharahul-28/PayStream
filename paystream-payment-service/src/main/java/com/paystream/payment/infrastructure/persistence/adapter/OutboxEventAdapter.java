package com.paystream.payment.infrastructure.persistence.adapter;

import com.paystream.common.util.IdGenerator;
import com.paystream.payment.application.port.out.OutboxEventPort;
import com.paystream.payment.infrastructure.persistence.entity.OutboxEventEntity;
import com.paystream.payment.infrastructure.persistence.repository.OutboxEventJpaRepository;
import org.springframework.stereotype.Component;

@Component
public class OutboxEventAdapter implements OutboxEventPort {

    private final OutboxEventJpaRepository outboxRepo;

    public OutboxEventAdapter(OutboxEventJpaRepository outboxRepo) {
        this.outboxRepo = outboxRepo;
    }

    @Override
    public void save(String aggregateId, String aggregateType, String eventType, String payloadJson) {
        OutboxEventEntity entity = new OutboxEventEntity(
                IdGenerator.generate(), aggregateId, aggregateType, eventType, payloadJson);
        outboxRepo.save(entity);
    }
}
