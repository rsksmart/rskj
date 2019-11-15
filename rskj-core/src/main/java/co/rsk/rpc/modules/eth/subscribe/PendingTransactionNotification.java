package co.rsk.rpc.modules.eth.subscribe;

import co.rsk.crypto.Keccak256;
import com.fasterxml.jackson.annotation.JsonValue;

public class PendingTransactionNotification implements EthSubscriptionNotificationDTO {

    private final Keccak256 transactionHash;

    public PendingTransactionNotification(Keccak256 transactionHash) {
        this.transactionHash = transactionHash;
    }

    @JsonValue
    public String getTransactionHash() {
        return transactionHash.toJsonString();
    }
}
