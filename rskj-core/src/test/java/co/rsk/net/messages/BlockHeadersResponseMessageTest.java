package co.rsk.net.messages;

import org.ethereum.core.BlockHeader;
import org.junit.jupiter.api.Test;

import java.util.LinkedList;
import java.util.List;

import static org.mockito.Mockito.*;

public class BlockHeadersResponseMessageTest {


    @Test
    public void accept() {
        BlockHeader blockHeader = mock(BlockHeader.class);
        List<BlockHeader> headers = new LinkedList<>();
        headers.add(blockHeader);

        BlockHeadersResponseMessage message = new BlockHeadersResponseMessage(1, headers);

        MessageVisitor visitor = mock(MessageVisitor.class);

        message.accept(visitor);

        verify(visitor, times(1)).apply(message);
    }
}
