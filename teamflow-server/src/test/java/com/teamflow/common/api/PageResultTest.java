package com.teamflow.common.api;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 分页响应测试。 */
class PageResultTest {

    @Test
    void shouldCreatePageResultFromPageQuery() {
        PageQuery query = new PageQuery(2, 2);
        List<String> source = new ArrayList<>(List.of("TASK-3", "TASK-4"));

        PageResult<String> result = PageResult.of(source, query, 5L);
        source.clear();

        assertAll(
                () -> assertEquals(List.of("TASK-3", "TASK-4"), result.items()),
                () -> assertEquals(2, result.page()),
                () -> assertEquals(2, result.size()),
                () -> assertEquals(5L, result.total()),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> result.items().add("TASK-5"))
        );
    }

    @Test
    void shouldCalculateNavigationMetadata() {
        PageResult<String> middlePage =
                PageResult.of(List.of("TASK-21"), new PageQuery(2, 20), 45L);
        PageResult<String> firstPage =
                PageResult.of(List.of("TASK-1"), new PageQuery(1, 20), 45L);
        PageResult<String> lastPage =
                PageResult.of(List.of("TASK-41"), new PageQuery(3, 20), 45L);

        assertAll(
                () -> assertEquals(3L, middlePage.totalPages()),
                () -> assertTrue(middlePage.hasPrevious()),
                () -> assertTrue(middlePage.hasNext()),
                () -> assertFalse(firstPage.hasPrevious()),
                () -> assertFalse(lastPage.hasNext())
        );
    }

    @Test
    void shouldCreateEmptyPageResult() {
        PageResult<String> result = PageResult.empty(PageQuery.defaults());

        assertAll(
                () -> assertTrue(result.items().isEmpty()),
                () -> assertEquals(0L, result.total()),
                () -> assertEquals(0L, result.totalPages()),
                () -> assertFalse(result.hasPrevious()),
                () -> assertFalse(result.hasNext())
        );
    }

    @Test
    void shouldRejectInvalidPageResult() {
        assertAll(
                () -> assertThrows(NullPointerException.class,
                        () -> new PageResult<>(null, 1, 20, 0L)),
                () -> assertThrows(NullPointerException.class,
                        () -> PageResult.of(List.of(), null, 0L)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PageResult<>(List.of(), 0, 20, 0L)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PageResult<>(List.of(), 1, 0, 0L)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PageResult<>(List.of(), 1, 20, -1L)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PageResult<>(List.of("A", "B"), 1, 1, 2L))
        );
    }

    @Test
    void shouldCalculateTotalPagesWithoutLongOverflow() {
        PageResult<String> result =
                new PageResult<>(List.of(), 1, 100, Long.MAX_VALUE);

        assertEquals(92_233_720_368_547_759L, result.totalPages());
    }
}
