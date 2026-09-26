package com.teamflow.core.common.transaction;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;

/** 当前事务生命周期内需要延后执行的通用回调。 */
public final class TransactionCallbacks {

    private TransactionCallbacks() {
    }

    /**
     * 在当前事务成功提交后执行回调；没有活动事务时立即执行。
     *
     * @param callback 提交成功后执行的动作
     */
    public static void afterCommit(Runnable callback) {
        Runnable validCallback = Objects.requireNonNull(
                callback,
                "事务提交回调不能为 null"
        );
        if (!TransactionSynchronizationManager
                .isActualTransactionActive()) {
            validCallback.run();
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException(
                    "当前事务未启用同步，无法注册提交后回调"
            );
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        validCallback.run();
                    }
                }
        );
    }
}
