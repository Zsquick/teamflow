package com.teamflow.common.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 分页查询参数测试。 */
class PageQueryTest {

    @Test
    void shouldCreateValidPageQuery() {
        PageQuery query = new PageQuery(3, 20);

        assertAll(
                () -> assertEquals(3, query.page()),
                () -> assertEquals(20, query.size()),
                () -> assertEquals(40L, query.offset())
        );
    }

    @Test
    void shouldCreateDefaultPageQuery() {
        PageQuery query = PageQuery.defaults();

        assertAll(
                () -> assertEquals(PageQuery.DEFAULT_PAGE, query.page()),
                () -> assertEquals(PageQuery.DEFAULT_SIZE, query.size()),
                () -> assertEquals(0L, query.offset())
        );
    }

    @Test
    void shouldRejectInvalidPageOrSize() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new PageQuery(0, 20)),
                () -> assertThrows(IllegalArgumentException.class, () -> new PageQuery(-1, 20)),
                () -> assertThrows(IllegalArgumentException.class, () -> new PageQuery(1, 0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PageQuery(1, PageQuery.MAX_SIZE + 1))
        );
    }

    @Test
    void shouldCalculateLargeOffsetWithoutIntegerOverflow() {
        PageQuery query = new PageQuery(Integer.MAX_VALUE, PageQuery.MAX_SIZE);

        assertEquals(214_748_364_600L, query.offset());
    }
}
