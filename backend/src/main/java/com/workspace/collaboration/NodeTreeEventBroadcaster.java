package com.workspace.collaboration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 只在节点事务成功提交后广播，避免客户端看到最终被回滚的文件树状态。
 */
@Component
public class NodeTreeEventBroadcaster {

    private static final Logger log =
            LoggerFactory.getLogger(NodeTreeEventBroadcaster.class);

    private final SimpMessagingTemplate messagingTemplate;

    public NodeTreeEventBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void broadcast(NodeTreeEvent event) {
        String destination = "/topic/spaces/" + event.spaceId() + "/nodes";
        try {
            messagingTemplate.convertAndSend(destination, event);
        } catch (RuntimeException error) {
            // 数据库已经提交，广播失败不能把成功的 REST 操作伪装成失败。
            log.error(
                    "Failed to broadcast node tree event {} to {}",
                    event.eventId(),
                    destination,
                    error
            );
        }
    }
}
