package co.rsk.net.messages;

import org.ethereum.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

public class SnapStateChunkRequestMessageTest {
    @Test
    void createMessage() {
        long someId = 42;
        SnapStateChunkRequestMessage message = new SnapStateChunkRequestMessage(someId, 0L, 0L, 0L);

        Assertions.assertEquals(someId, message.getId());
        Assertions.assertEquals(MessageType.STATE_CHUNK_REQUEST_MESSAGE, message.getMessageType());
    }

    @Test
    void accept() {
        long someId = 42;
        byte[] someHash = TestUtils.generateBytes("msg",32);

        SnapStateChunkRequestMessage message = new SnapStateChunkRequestMessage(someId, 0L, 0L, 0L);

        MessageVisitor visitor = mock(MessageVisitor.class);

        message.accept(visitor);

        verify(visitor, times(1)).apply(message);
    }
}
