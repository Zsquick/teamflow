package com.teamflow.scheduling;

import com.teamflow.core.common.time.UtcTimeText;
import com.teamflow.core.notification.domain.NotificationType;
import com.teamflow.core.notification.dto.CreateNotificationCommand;
import com.teamflow.core.notification.service.NotificationService;
import com.teamflow.core.task.domain.Task;
import com.teamflow.core.task.mapper.TaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 分批扫描即将到期任务，并幂等创建站内提醒。 */
@Component
public class DueTaskScheduler {

    private static final Logger log = LoggerFactory.getLogger(
            DueTaskScheduler.class
    );
    private static final String LOCK_KEY =
            "teamflow:lock:due-task-reminder";
    /**
     * 单轮最多处理十批。默认配置下至多扫描两千条任务，既限制内存，
     * 也避免极端积压让一次调度长期占用分布式锁。
     */
    static final int MAX_BATCHES_PER_RUN = 10;
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('get', KEYS[1]) == ARGV[1] "
                            + "then return redis.call('del', KEYS[1]) "
                            + "else return 0 end",
                    Long.class
            );

    private final TaskMapper taskMapper;
    private final NotificationService notificationService;
    private final StringRedisTemplate redisTemplate;
    private final OperationsProperties properties;
    private final Clock clock;

    public DueTaskScheduler(
            TaskMapper taskMapper,
            NotificationService notificationService,
            StringRedisTemplate redisTemplate,
            OperationsProperties properties,
            Clock clock
    ) {
        this.taskMapper = Objects.requireNonNull(
                taskMapper,
                "任务 Mapper 不能为 null"
        );
        this.notificationService = Objects.requireNonNull(
                notificationService,
                "通知服务不能为 null"
        );
        this.redisTemplate = Objects.requireNonNull(
                redisTemplate,
                "Redis 模板不能为 null"
        );
        this.properties = Objects.requireNonNull(
                properties,
                "运维配置不能为 null"
        );
        this.clock = Objects.requireNonNull(clock, "时钟不能为 null");
    }

    @Scheduled(
            fixedDelayString =
                    "${teamflow.operations.due-scan-interval:PT5M}"
    )
    public void notifyDueTasks() {
        String owner = UUID.randomUUID().toString();
        if (!acquireLock(owner)) {
            return;
        }
        try {
            scanAndNotify();
        } finally {
            releaseLock(owner);
        }
    }

    private void scanAndNotify() {
        Instant now = clock.instant();
        String from = UtcTimeText.format(now);
        String to = UtcTimeText.format(
                now.plus(properties.dueReminderWindow())
        );
        int scanned = 0;
        int notified = 0;
        int failed = 0;
        long offset = 0;
        boolean batchLimitReached = false;

        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            List<Task> tasks = Objects.requireNonNull(
                    taskMapper.findDueBetween(
                            from,
                            to,
                            properties.dueBatchSize(),
                            offset
                    ),
                    "到期任务查询结果不能为 null"
            );
            for (Task task : tasks) {
                scanned++;
                if (task.getAssigneeId() == null) {
                    continue;
                }
                try {
                    notificationService.create(
                            new CreateNotificationCommand(
                                    task.getAssigneeId(),
                                    NotificationType.TASK_DUE_SOON,
                                    "任务即将到期",
                                    "任务 " + task.getId()
                                            + " 将在 " + task.getDueAt()
                                            + " 到期",
                                    "TASK_DUE_SOON:"
                                            + task.getId()
                                            + ":"
                                            + task.getAssigneeId()
                                            + ":"
                                            + task.getDueAt()
                            )
                    );
                    notified++;
                } catch (RuntimeException exception) {
                    failed++;
                    log.warn(
                            "创建到期提醒失败 taskId={} assigneeId={}",
                            task.getId(),
                            task.getAssigneeId(),
                            exception
                    );
                }
            }
            if (tasks.size() < properties.dueBatchSize()) {
                break;
            }
            offset += tasks.size();
            batchLimitReached = batch == MAX_BATCHES_PER_RUN - 1;
        }

        if (batchLimitReached) {
            log.warn(
                    "到期任务扫描达到单轮批次上限 maxBatches={} "
                            + "batchSize={}，剩余任务留待下一轮",
                    MAX_BATCHES_PER_RUN,
                    properties.dueBatchSize()
            );
        }

        log.info(
                "到期任务扫描完成 from={} to={} scanned={} notified={} failed={}",
                from,
                to,
                scanned,
                notified,
                failed
        );
    }

    private boolean acquireLock(String owner) {
        try {
            return Boolean.TRUE.equals(
                    redisTemplate.opsForValue().setIfAbsent(
                            LOCK_KEY,
                            owner,
                            properties.dueLockTtl()
                    )
            );
        } catch (RuntimeException exception) {
            log.error("无法取得到期任务扫描锁，本轮跳过", exception);
            return false;
        }
    }

    private void releaseLock(String owner) {
        try {
            redisTemplate.execute(
                    RELEASE_LOCK_SCRIPT,
                    List.of(LOCK_KEY),
                    owner
            );
        } catch (RuntimeException exception) {
            log.warn("释放到期任务扫描锁失败，等待租期自动失效", exception);
        }
    }
}
