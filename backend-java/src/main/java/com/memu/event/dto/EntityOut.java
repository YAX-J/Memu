package com.memu.event.dto;

/**
 * 对齐内核 /kernel/ingest 响应中的实体。
 */
public record EntityOut(String id, String type, String name) {
}
