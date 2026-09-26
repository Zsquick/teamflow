package com.teamflow.core.comment.service;

import com.teamflow.common.error.BusinessException;
import com.teamflow.core.comment.domain.TaskComment;
import com.teamflow.core.comment.dto.CommentResponse;
import com.teamflow.core.comment.dto.CreateCommentRequest;
import com.teamflow.core.comment.error.CommentErrorCode;
import com.teamflow.core.comment.mapper.TaskCommentMapper;
import com.teamflow.core.comment.service.impl.TaskCommentServiceImpl;
import com.teamflow.core.common.id.ReadableIdGenerator;
import com.teamflow.core.common.id.ResourceType;
import com.teamflow.core.task.error.TaskErrorCode;
import com.teamflow.core.task.service.TaskAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 评论访问边界、联表查询和作者删除测试。 */
@ExtendWith(MockitoExtension.class)
class TaskCommentServiceImplTest {

    private static final String USER_ID = "u001";
    private static final String OTHER_USER_ID = "u002";
    private static final String TASK_ID = "t001";
    private static final String COMMENT_ID = "c001";
    private static final String NOW = "2026-09-18T01:02:03.456Z";

    @Mock
    private TaskCommentMapper commentMapper;
    @Mock
    private TaskAccessService taskAccessService;
    @Mock
    private ReadableIdGenerator idGenerator;

    private TaskCommentServiceImpl commentService;

    @BeforeEach
    void setUp() {
        commentService = new TaskCommentServiceImpl(
                commentMapper,
                taskAccessService,
                idGenerator,
                Clock.fixed(Instant.parse(NOW), ZoneOffset.UTC)
        );
    }

    @Test
    void shouldRequireEveryDependency() {
        Clock clock = Clock.systemUTC();
        assertAll(
                () -> assertThrows(NullPointerException.class, () ->
                        new TaskCommentServiceImpl(
                                null, taskAccessService, idGenerator, clock
                        )),
                () -> assertThrows(NullPointerException.class, () ->
                        new TaskCommentServiceImpl(
                                commentMapper, null, idGenerator, clock
                        )),
                () -> assertThrows(NullPointerException.class, () ->
                        new TaskCommentServiceImpl(
                                commentMapper, taskAccessService, null, clock
                        )),
                () -> assertThrows(NullPointerException.class, () ->
                        new TaskCommentServiceImpl(
                                commentMapper, taskAccessService,
                                idGenerator, null
                        ))
        );
    }

    @Test
    void shouldCreateAfterWriteAccessAndReturnJoinedDetail() {
        CreateCommentRequest request = new CreateCommentRequest(
                "  请检查这部分  "
        );
        CommentResponse savedResponse = response(USER_ID, "小周");
        when(idGenerator.nextId(ResourceType.TASK_COMMENT))
                .thenReturn(COMMENT_ID);
        when(commentMapper.insert(any(TaskComment.class))).thenReturn(1);
        when(commentMapper.findDetailById(COMMENT_ID))
                .thenReturn(Optional.of(savedResponse));

        CommentResponse result = commentService.create(
                USER_ID,
                TASK_ID,
                request
        );

        ArgumentCaptor<TaskComment> captor =
                ArgumentCaptor.forClass(TaskComment.class);
        InOrder order = inOrder(
                taskAccessService,
                idGenerator,
                commentMapper
        );
        order.verify(taskAccessService)
                .requireTaskMemberForUpdate(USER_ID, TASK_ID);
        order.verify(idGenerator).nextId(ResourceType.TASK_COMMENT);
        order.verify(commentMapper).insert(captor.capture());
        order.verify(commentMapper).findDetailById(COMMENT_ID);

        TaskComment inserted = captor.getValue();
        assertAll(
                () -> assertEquals(COMMENT_ID, inserted.getId()),
                () -> assertEquals(TASK_ID, inserted.getTaskId()),
                () -> assertEquals(USER_ID, inserted.getAuthorId()),
                () -> assertEquals("请检查这部分", inserted.getContent()),
                () -> assertEquals(NOW, inserted.getCreatedAt()),
                () -> assertEquals(savedResponse, result)
        );
    }

    @Test
    void shouldStopCreateWhenTaskIsNotVisibleOrWasSoftDeleted() {
        when(taskAccessService.requireTaskMemberForUpdate(
                USER_ID,
                TASK_ID
        )).thenThrow(new BusinessException(TaskErrorCode.TASK_NOT_FOUND));

        assertBusinessError(
                TaskErrorCode.TASK_NOT_FOUND,
                () -> commentService.create(
                        USER_ID,
                        TASK_ID,
                        new CreateCommentRequest("评论")
                )
        );

        verifyNoInteractions(idGenerator, commentMapper);
    }

    @Test
    void shouldRejectImpossibleInsertCountWithoutReadingDetail() {
        when(idGenerator.nextId(ResourceType.TASK_COMMENT))
                .thenReturn(COMMENT_ID);
        when(commentMapper.insert(any(TaskComment.class))).thenReturn(0);

        assertThrows(
                IllegalStateException.class,
                () -> commentService.create(
                        USER_ID,
                        TASK_ID,
                        new CreateCommentRequest("评论")
                )
        );

        verify(commentMapper, never()).findDetailById(COMMENT_ID);
    }

    @Test
    void shouldFailIfInsertedCommentCannotBeReloaded() {
        when(idGenerator.nextId(ResourceType.TASK_COMMENT))
                .thenReturn(COMMENT_ID);
        when(commentMapper.insert(any(TaskComment.class))).thenReturn(1);
        when(commentMapper.findDetailById(COMMENT_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                IllegalStateException.class,
                () -> commentService.create(
                        USER_ID,
                        TASK_ID,
                        new CreateCommentRequest("评论")
                )
        );
    }

    @Test
    void shouldListJoinedDetailsAfterReadAccessWithoutNPlusOne() {
        List<CommentResponse> mapperResult = new ArrayList<>(List.of(
                response(USER_ID, "小周"),
                new CommentResponse(
                        "c002", TASK_ID, OTHER_USER_ID, "小李",
                        "已处理", NOW
                )
        ));
        when(commentMapper.findDetailsByTaskId(TASK_ID))
                .thenReturn(mapperResult);

        List<CommentResponse> result = commentService.listByTask(
                USER_ID,
                TASK_ID
        );

        InOrder order = inOrder(taskAccessService, commentMapper);
        order.verify(taskAccessService).requireTaskMember(USER_ID, TASK_ID);
        order.verify(commentMapper).findDetailsByTaskId(TASK_ID);
        assertAll(
                () -> assertEquals(mapperResult, result),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> result.add(response(USER_ID, "小周"))
                )
        );
    }

    @Test
    void shouldNotQueryCommentsWhenTaskReadAccessFails() {
        when(taskAccessService.requireTaskMember(USER_ID, TASK_ID))
                .thenThrow(new BusinessException(
                        TaskErrorCode.TASK_NOT_FOUND
                ));

        assertBusinessError(
                TaskErrorCode.TASK_NOT_FOUND,
                () -> commentService.listByTask(USER_ID, TASK_ID)
        );

        verifyNoInteractions(commentMapper);
    }

    @Test
    void shouldDeleteOwnCommentAfterTaskWriteAccess() {
        when(commentMapper.findById(COMMENT_ID))
                .thenReturn(Optional.of(comment(USER_ID)));
        when(commentMapper.deleteByIdAndAuthor(COMMENT_ID, USER_ID))
                .thenReturn(1);

        commentService.delete(USER_ID, COMMENT_ID);

        InOrder order = inOrder(commentMapper, taskAccessService);
        order.verify(commentMapper).findById(COMMENT_ID);
        order.verify(taskAccessService)
                .requireTaskMemberForUpdate(USER_ID, TASK_ID);
        order.verify(commentMapper)
                .deleteByIdAndAuthor(COMMENT_ID, USER_ID);
    }

    @Test
    void shouldRejectMissingCommentBeforeLookingUpTask() {
        when(commentMapper.findById(COMMENT_ID))
                .thenReturn(Optional.empty());

        assertBusinessError(
                CommentErrorCode.COMMENT_NOT_FOUND,
                () -> commentService.delete(USER_ID, COMMENT_ID)
        );

        verifyNoInteractions(taskAccessService);
        verify(commentMapper, never()).deleteByIdAndAuthor(any(), any());
    }

    @Test
    void shouldCheckTaskAccessBeforeRejectingDifferentAuthor() {
        when(commentMapper.findById(COMMENT_ID))
                .thenReturn(Optional.of(comment(OTHER_USER_ID)));

        assertBusinessError(
                CommentErrorCode.COMMENT_DELETE_FORBIDDEN,
                () -> commentService.delete(USER_ID, COMMENT_ID)
        );

        InOrder order = inOrder(commentMapper, taskAccessService);
        order.verify(commentMapper).findById(COMMENT_ID);
        order.verify(taskAccessService)
                .requireTaskMemberForUpdate(USER_ID, TASK_ID);
        verify(commentMapper, never()).deleteByIdAndAuthor(any(), any());
    }

    @Test
    void shouldHideInaccessibleTaskAsMissingCommentWithoutDeleting() {
        when(commentMapper.findById(COMMENT_ID))
                .thenReturn(Optional.of(comment(OTHER_USER_ID)));
        when(taskAccessService.requireTaskMemberForUpdate(
                USER_ID,
                TASK_ID
        )).thenThrow(new BusinessException(TaskErrorCode.TASK_NOT_FOUND));

        assertBusinessError(
                CommentErrorCode.COMMENT_NOT_FOUND,
                () -> commentService.delete(USER_ID, COMMENT_ID)
        );

        verify(commentMapper, never()).deleteByIdAndAuthor(any(), any());
    }

    @Test
    void shouldReportNotFoundWhenConcurrentDeleteWins() {
        when(commentMapper.findById(COMMENT_ID))
                .thenReturn(Optional.of(comment(USER_ID)));
        when(commentMapper.deleteByIdAndAuthor(COMMENT_ID, USER_ID))
                .thenReturn(0);

        assertBusinessError(
                CommentErrorCode.COMMENT_NOT_FOUND,
                () -> commentService.delete(USER_ID, COMMENT_ID)
        );
    }

    @Test
    void shouldRejectImpossibleDeleteCount() {
        when(commentMapper.findById(COMMENT_ID))
                .thenReturn(Optional.of(comment(USER_ID)));
        when(commentMapper.deleteByIdAndAuthor(COMMENT_ID, USER_ID))
                .thenReturn(2);

        assertThrows(
                IllegalStateException.class,
                () -> commentService.delete(USER_ID, COMMENT_ID)
        );
    }

    private TaskComment comment(String authorId) {
        return new TaskComment(
                COMMENT_ID,
                TASK_ID,
                authorId,
                "评论",
                NOW
        );
    }

    private CommentResponse response(String authorId, String authorName) {
        return new CommentResponse(
                COMMENT_ID,
                TASK_ID,
                authorId,
                authorName,
                "请检查这部分",
                NOW
        );
    }

    private static void assertBusinessError(
            Object expectedErrorCode,
            org.junit.jupiter.api.function.Executable executable
    ) {
        BusinessException exception = assertThrows(
                BusinessException.class,
                executable
        );
        assertEquals(expectedErrorCode, exception.getErrorCode());
    }
}
