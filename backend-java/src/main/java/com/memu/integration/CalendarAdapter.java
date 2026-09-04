package com.memu.integration;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 日历适配器占位。P2 接入系统日历（macOS EventKit / Windows Graph / iCal URL），
 * 把日程项转成事件进入图谱。
 */
@Component
public class CalendarAdapter implements IntegrationAdapter {

    @Override
    public String source() {
        return "calendar";
    }

    @Override
    public SyncResult fetchIncremental(String fromCursor) {
        // TODO(P2): 接入系统日历
        return new SyncResult(List.of(), fromCursor);
    }
}
