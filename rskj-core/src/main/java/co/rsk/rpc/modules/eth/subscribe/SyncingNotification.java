package co.rsk.rpc.modules.eth.subscribe;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

public class SyncingNotification implements EthSubscriptionNotificationDTO{
    private final boolean syncing;
    final Map<String,String> status;

    public SyncingNotification(final boolean syncing, final Map<String, String> status) {
        this.syncing = syncing;
        this.status = status;
    }

    public boolean isSyncing() {
        return syncing;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Map<String, String> getStatus() {
        return status;
    }
}
