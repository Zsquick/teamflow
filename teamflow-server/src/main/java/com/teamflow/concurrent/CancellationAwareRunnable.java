package com.teamflow.concurrent;

/** 在线程池关闭时为尚未开始的任务提供业务补偿回调。 */
public interface CancellationAwareRunnable extends Runnable {

    /** 任务从等待队列移除、确定不会执行时调用。 */
    void cancelled();
}
