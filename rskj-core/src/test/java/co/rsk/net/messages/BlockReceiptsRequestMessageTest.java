package co.rsk.net.messages;

import org.ethereum.crypto.HashUtil;
import org.junit.Assert;
import org.junit.Test;

import static org.mockito.Mockito.*;

public class BlockReceiptsRequestMessageTest {
    @Test
    public void createMessage() {
        byte[] hash = HashUtil.randomHash();
        BlockReceiptsRequestMessage message = new BlockReceiptsRequestMessage(1, hash);
        Assert.assertEquals(1, message.getId());
        Assert.assertArrayEquals(hash, message.getBlockHash());
        Assert.assertEquals(MessageType.BLOCK_RECEIPTS_REQUEST_MESSAGE, message.getMessageType());
    }

    @Test
    public void accept() {
        byte[] hash = HashUtil.randomHash();
        BlockReceiptsRequestMessage message = new BlockReceiptsRequestMessage(1, hash);

        MessageVisitor visitor = mock(MessageVisitor.class);

        message.accept(visitor);

        verify(visitor, times(1)).apply(message);
    }
}
