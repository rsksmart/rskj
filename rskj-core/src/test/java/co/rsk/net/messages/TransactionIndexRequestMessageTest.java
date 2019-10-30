package co.rsk.net.messages;

import org.ethereum.crypto.HashUtil;
import org.junit.Assert;
import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.*;

public class TransactionIndexRequestMessageTest {

    @Test
    public void createMessage() {
        long id = 0;
        byte[] hash = HashUtil.randomHash();

        TransactionIndexRequestMessage message = new TransactionIndexRequestMessage(id, hash);

        Assert.assertThat(message.getId(), is(id));
        Assert.assertThat(message.getTransactionHash(), is(hash));
        Assert.assertThat(message.getMessageType(), is(MessageType.TRANSACTION_INDEX_REQUEST_MESSAGE));
    }

    @Test
    public void accept() {
        byte[] hash = new byte[]{0x0F};
        long id = 100;
        MessageVisitor visitor = mock(MessageVisitor.class);

        TransactionIndexRequestMessage message = new TransactionIndexRequestMessage(id, hash);
        message.accept(visitor);

        verify(visitor, times(1)).apply(message);
    }

}
