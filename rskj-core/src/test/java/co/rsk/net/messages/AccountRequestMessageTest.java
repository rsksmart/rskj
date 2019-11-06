package co.rsk.net.messages;

import org.ethereum.crypto.HashUtil;
import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

public class AccountRequestMessageTest {

    @Test
    public void createMessage() {
        long id = 0;
        byte[] blockHash = HashUtil.randomHash();
        byte[] addressHash = HashUtil.randomHash();

        AccountRequestMessage message = new AccountRequestMessage(id, blockHash, addressHash);

        assertThat(message.getId(), is(id));
        assertThat(message.getBlockHash(), is(blockHash));
        assertThat(message.getAddress(), is(addressHash));
        assertThat(message.getMessageType(), is(MessageType.ACCOUNT_REQUEST_MESSAGE));
    }

    @Test
    public void accept() {
        long id = 0;
        byte[] blockHash = HashUtil.randomHash();
        byte[] addressHash = HashUtil.randomHash();

        MessageVisitor visitor = mock(MessageVisitor.class);

        AccountRequestMessage message = new AccountRequestMessage(id, blockHash, addressHash);

        message.accept(visitor);

        verify(visitor, times(1)).apply(message);
    }

}

