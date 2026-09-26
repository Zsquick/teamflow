package com.teamflow.core.common.transaction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 提交后回调的立即、延迟和非法事务状态测试。 */
class TransactionCallbacksTest {

    @AfterEach
    void clearTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void shouldRunImmediatelyWithoutTransaction() {
        AtomicInteger calls = new AtomicInteger();

        TransactionCallbacks.afterCommit(calls::incrementAndGet);

        assertEquals(1, calls.get());
    }

    @Test
    void shouldDelayUntilActiveTransactionCommits() {
        AtomicInteger calls = new AtomicInteger();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        TransactionCallbacks.afterCommit(calls::incrementAndGet);
        assertEquals(0, calls.get());

        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
        assertEquals(1, calls.get());
    }

    @Test
    void shouldRejectActiveTransactionWithoutSynchronization() {
        TransactionSynchronizationManager.setActualTransactionActive(true);

        assertThrows(
                IllegalStateException.class,
                () -> TransactionCallbacks.afterCommit(() -> {
                })
        );
    }
}
