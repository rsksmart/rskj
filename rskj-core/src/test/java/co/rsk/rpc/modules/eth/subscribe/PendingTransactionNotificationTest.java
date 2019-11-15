package co.rsk.rpc.modules.eth.subscribe;

import co.rsk.crypto.Keccak256;
import org.ethereum.TestUtils;
import org.junit.Assert;
import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

public class PendingTransactionNotificationTest {

    @Test
    public void getTransactionHash() {
        Keccak256 transactionHash = TestUtils.randomHash();
        PendingTransactionNotification pendingTransactionNotification = new PendingTransactionNotification(transactionHash);

        Assert.assertThat(pendingTransactionNotification.getTransactionHash(), is(transactionHash.toJsonString()));
    }
}