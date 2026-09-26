package com.teamflow.core.common.id;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 数据库可读编号生成器测试。 */
@ExtendWith(MockitoExtension.class)
class DatabaseReadableIdGeneratorTest {

    @Mock
    private IdSequenceMapper idSequenceMapper;

    private DatabaseReadableIdGenerator idGenerator;

    @BeforeEach
    void setUp() {
        idGenerator = new DatabaseReadableIdGenerator(idSequenceMapper);
    }

    @Test
    void shouldGenerateCurrentIdAndAdvanceSequence() {
        when(idSequenceMapper.findNextValueForUpdate("USER")).thenReturn(1);
        when(idSequenceMapper.updateNextValue("USER", 2)).thenReturn(1);

        String id = idGenerator.nextId(ResourceType.USER);

        assertEquals("u001", id);
        verify(idSequenceMapper).findNextValueForUpdate("USER");
        verify(idSequenceMapper).updateNextValue("USER", 2);
    }

    @Test
    void shouldRejectNullResourceType() {
        assertThrows(
                NullPointerException.class,
                () -> idGenerator.nextId(null)
        );

        verify(idSequenceMapper, never()).findNextValueForUpdate("USER");
    }

    @Test
    void shouldFailWhenSequenceIsMissing() {
        when(idSequenceMapper.findNextValueForUpdate("TASK")).thenReturn(null);

        assertThrows(
                IllegalStateException.class,
                () -> idGenerator.nextId(ResourceType.TASK)
        );

        verify(idSequenceMapper, never()).updateNextValue("TASK", 1);
    }

    @Test
    void shouldFailWhenStoredSequenceIsInvalid() {
        when(idSequenceMapper.findNextValueForUpdate("PROJECT")).thenReturn(0);

        assertThrows(
                IllegalStateException.class,
                () -> idGenerator.nextId(ResourceType.PROJECT)
        );

        verify(idSequenceMapper, never()).updateNextValue("PROJECT", 1);
    }

    @Test
    void shouldFailWhenSequenceUpdateDoesNotAffectOneRow() {
        when(idSequenceMapper.findNextValueForUpdate("USER")).thenReturn(7);
        when(idSequenceMapper.updateNextValue("USER", 8)).thenReturn(0);

        assertThrows(
                IllegalStateException.class,
                () -> idGenerator.nextId(ResourceType.USER)
        );
    }

    @Test
    void shouldFailWhenIntegerRangeIsExhausted() {
        when(idSequenceMapper.findNextValueForUpdate("USER"))
                .thenReturn(Integer.MAX_VALUE);

        assertThrows(
                IllegalStateException.class,
                () -> idGenerator.nextId(ResourceType.USER)
        );

        verify(idSequenceMapper, never())
                .updateNextValue("USER", Integer.MIN_VALUE);
    }
}
