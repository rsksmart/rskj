package co.rsk.net.messages;

import org.ethereum.crypto.HashUtil;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created by ajlopez on 23/08/2017.
 */
public class BlockHeadersRequestMessageTest {
    @Test
    public void createMessage() {
        byte[] hash = HashUtil.randomHash();
        BlockHeadersRequestMessage message = new BlockHeadersRequestMessage(1, hash, 100);

        Assert.assertEquals(1, message.getId());
        Assert.assertArrayEquals(hash, message.getHash());
        Assert.assertEquals(100, message.getCount());
        Assert.assertEquals(MessageType.BLOCK_HEADERS_REQUEST_MESSAGE, message.getMessageType());
    }
}
