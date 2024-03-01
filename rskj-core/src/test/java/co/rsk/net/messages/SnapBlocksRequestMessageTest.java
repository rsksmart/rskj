package co.rsk.net.messages;

import co.rsk.blockchain.utils.BlockGenerator;
import org.ethereum.core.Block;
import org.ethereum.util.RLP;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class SnapBlocksRequestMessageTest {

    private Block block4Test = new BlockGenerator().getBlock(1);
    private SnapBlocksRequestMessage underTest = new SnapBlocksRequestMessage(block4Test.getNumber());


    @Test
    void getMessageType_returnCorrectMessageType() {
        //given-when
        MessageType messageType = underTest.getMessageType();

        //then
        assertEquals(MessageType.SNAP_BLOCKS_REQUEST_MESSAGE, messageType);
    }

    @Test
    void getEncodedMessage_returnExpectedByteArray() {
        //given default block 4 test

        //when
        byte[] encodedMessage = underTest.getEncodedMessage();

        //then
        assertThat(encodedMessage)
                .isEqualTo(RLP.encodeList(RLP.encodeBigInteger(BigInteger.ONE)));
    }

    @Test
    void getBlockNumber_returnTheExpectedValue() {
        //given default block 4 test

        //when
        long blockNumber = underTest.getBlockNumber();

        //then
        assertThat(blockNumber)
                .isEqualTo(block4Test.getNumber());
    }

    @Test
    void givenAcceptIsCalled_messageVisitorIsAppliedFormessage() {
        //given
        Block block = new BlockGenerator().getBlock(1);
        SnapBlocksRequestMessage message = new SnapBlocksRequestMessage(block.getNumber());
        MessageVisitor visitor = mock(MessageVisitor.class);

        //when
        message.accept(visitor);

        //then
        verify(visitor, times(1)).apply(message);
    }
}