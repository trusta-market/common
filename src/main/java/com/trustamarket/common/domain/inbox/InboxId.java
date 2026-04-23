package com.trustamarket.common.domain.inbox;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

// Inbox 복합 PK. (messageId, messageGroup) 조합으로 컨슈머 그룹별 독립 멱등성 보장.
// 같은 message_id 라도 다른 그룹이면 각 그룹이 독립적으로 한 번씩 처리한다.
public class InboxId implements Serializable {

    private UUID messageId;
    private String messageGroup;

    protected InboxId() {}

    public InboxId(UUID messageId, String messageGroup) {
        this.messageId = messageId;
        this.messageGroup = messageGroup;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InboxId that)) return false;
        return Objects.equals(messageId, that.messageId)
                && Objects.equals(messageGroup, that.messageGroup);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageId, messageGroup);
    }
}
