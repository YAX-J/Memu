package com.memu.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 对齐内核 /kernel/ingest 响应中的关系。
 * 注意：契约字段名为 from / to，"from" 是 Java 关键字，用 @JsonProperty 映射。
 */
public record RelationOut(
        @JsonProperty("from") String fromId,
        @JsonProperty("to") String toId,
        String type
) {
}
