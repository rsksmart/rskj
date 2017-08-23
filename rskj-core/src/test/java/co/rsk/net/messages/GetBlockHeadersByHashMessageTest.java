package co.rsk.net.messages;

import org.ethereum.crypto.HashUtil;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created by ajlopez on 23/08/2017.
 */
public class GetBlockHeadersByHashMessageTest {
    @Test
    public void createMessage() {
        byte[] hash = HashUtil.randomHash();
        GetBlockHeadersByHashMessage message = new GetBlockHeadersByHashMessage(1, hash, 100);

        Assert.assertEquals(1, message.getId());
        Assert.assertArrayEquals(hash, message.getHash());
        Assert.assertEquals(100, message.getCount());
        Assert.assertEquals(MessageType.GET_BLOCK_HEADERS_BY_HASH_MESSAGE, message.getMessageType());
    }
}
