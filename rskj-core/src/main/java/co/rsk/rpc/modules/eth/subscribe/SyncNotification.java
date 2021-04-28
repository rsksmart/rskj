package co.rsk.rpc.modules.eth.subscribe;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SyncNotification implements EthSubscriptionNotificationDTO {
    private final boolean syncing;
    private final SyncStatusNotification status;

    public SyncNotification(boolean syncing, SyncStatusNotification status) {
        this.syncing = syncing;
        this.status = status;
    }

    public boolean isSyncing() {
        return syncing;
    }

    public SyncStatusNotification getStatus() {
        return status;
    }
}
