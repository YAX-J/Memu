package com.memu.integration;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 邮件适配器占位。P2 接入本地邮件（IMAP / 系统邮件客户端），
 * 抽取联系人、会议邀约等进入图谱。
 */
@Component
public class MailAdapter implements IntegrationAdapter {

    @Override
    public String source() {
        return "mail";
    }

    @Override
    public SyncResult fetchIncremental(String fromCursor) {
        // TODO(P2): 接入 IMAP / 系统邮件
        return new SyncResult(List.of(), fromCursor);
    }
}
