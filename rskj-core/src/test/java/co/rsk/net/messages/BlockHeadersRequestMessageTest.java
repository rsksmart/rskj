package co.rsk.net.messages;

import org.ethereum.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Created by ajlopez on 23/08/2017.
 */
class BlockHeadersRequestMessageTest {
    @Test
    void createMessage() {
        byte[] hash = TestUtils.generateBytes("hash",32);
        BlockHeadersRequestMessage message = new BlockHeadersRequestMessage(1, hash, 100);

        Assertions.assertEquals(1, message.getId());
        Assertions.assertArrayEquals(hash, message.getHash());
        Assertions.assertEquals(100, message.getCount());
        Assertions.assertEquals(MessageType.BLOCK_HEADERS_REQUEST_MESSAGE, message.getMessageType());
    }

    @Test
    void accept() {
        byte[] hash = TestUtils.generateBytes("hash",32);
        BlockHeadersRequestMessage message = new BlockHeadersRequestMessage(1, hash, 100);

        MessageVisitor visitor = mock(MessageVisitor.class);

        message.accept(visitor);

        verify(visitor, times(1)).apply(message);
    }
}
