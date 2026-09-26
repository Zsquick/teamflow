package com.teamflow.common.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 资源 ID 响应测试。 */
class IdResponseTest {

    private static final String ID = "s001";

    @Test
    void shouldCreateResponseWithReadableStringId() {
        IdResponse response = new IdResponse(ID);

        assertEquals(ID, response.id());
    }

    @Test
    void shouldRejectNullId() {
        assertThrows(
                NullPointerException.class,
                () -> new IdResponse(null)
        );
    }

    @Test
    void shouldRejectInvalidStringId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new IdResponse("")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new IdResponse("not-a-uuid")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new IdResponse("S001")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new IdResponse("s000")
        );
    }

    @Test
    void shouldSerializeAsObjectWithIdField() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        JsonNode json = objectMapper.readTree(
                objectMapper.writeValueAsString(new IdResponse(ID))
        );

        assertEquals(ID, json.get("id").asText());
    }
}
