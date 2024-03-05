package co.rsk.net.messages;

import co.rsk.blockchain.utils.BlockGenerator;
import org.ethereum.core.Block;
import org.ethereum.util.RLP;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class SnapStateChunkResponseMessageTest {

    @Test
    void getMessageType_returnCorrectMessageType() {
        //given
        Block block = new BlockGenerator().getBlock(1);
        long id4Test = 42L;
        String trieValue = "any random data";
        SnapStateChunkResponseMessage message = new SnapStateChunkResponseMessage(id4Test, trieValue.getBytes(), block.getNumber(), 0L, 0L, true);

        //when
        MessageType messageType = message.getMessageType();

        //then
        assertThat(messageType).isEqualTo(MessageType.STATE_CHUNK_RESPONSE_MESSAGE);
    }

    @Test
    void givenParameters4Test_assureExpectedValues() {
        //given
        Block block = new BlockGenerator().getBlock(1);
        long id4Test = 42L;
        byte[] trieValueBytes = "any random data".getBytes();
        long from = 5L;
        long to = 20L;
        boolean complete = true;

        //when
        SnapStateChunkResponseMessage message = new SnapStateChunkResponseMessage(id4Test, trieValueBytes, block.getNumber(), from, to, complete);

        //then
        assertThat(message).extracting(SnapStateChunkResponseMessage::getId,
                        SnapStateChunkResponseMessage::getChunkOfTrieKeyValue,
                        SnapStateChunkResponseMessage::getBlockNumber,
                        SnapStateChunkResponseMessage::getFrom,
                        SnapStateChunkResponseMessage::getTo,
                        SnapStateChunkResponseMessage::isComplete)
                .containsExactly(id4Test, trieValueBytes, block.getNumber(), from, to, complete);
    }


    @Test
    void getEncodedMessageWithoutId_returnExpectedByteArray() {
        //given
        long blockNumber = 1L;
        long id4Test = 42L;
        byte[] trieValueBytes = "any random data".getBytes();
        long from = 5L;
        long to = 20L;
        boolean complete = true;

        byte[] expectedEncodedMessage = RLP.encodeList(
                trieValueBytes,
                RLP.encodeBigInteger(BigInteger.valueOf(blockNumber)),
                RLP.encodeBigInteger(BigInteger.valueOf(from)),
                RLP.encodeBigInteger(BigInteger.valueOf(to)),
                new byte[]{(byte) 1});

        SnapStateChunkResponseMessage message = new SnapStateChunkResponseMessage(id4Test, trieValueBytes, blockNumber, from, to, complete);

        //when
        byte[] encodedMessage = message.getEncodedMessageWithoutId();

        //then
        assertThat(encodedMessage)
                .isEqualTo(expectedEncodedMessage);
    }

    @Test
    void getEncodedMessageWithId_returnExpectedByteArray() {
        //given
        long blockNumber = 1L;
        long id4Test = 42L;
        byte[] trieValueBytes = "any random data".getBytes();
        long from = 5L;
        long to = 20L;
        boolean complete = true;

        SnapStateChunkResponseMessage message = new SnapStateChunkResponseMessage(id4Test, trieValueBytes, blockNumber, from, to, complete);
        byte[] expectedEncodedMessage = RLP.encodeList(
                RLP.encodeBigInteger(BigInteger.valueOf(id4Test)), message.getEncodedMessageWithoutId());

        //when
        byte[] encodedMessage = message.getEncodedMessage();

        //then
        assertThat(encodedMessage)
                .isEqualTo(expectedEncodedMessage);
    }

    @Test
    void givenAcceptIsCalled_messageVisitorIsAppliedForMessage() {
        //given
        long blockNumber = 1L;
        long id4Test = 42L;
        byte[] trieValueBytes = "any random data".getBytes();
        long from = 5L;
        long to = 20L;
        boolean complete = true;
        SnapStateChunkResponseMessage message = new SnapStateChunkResponseMessage(id4Test, trieValueBytes, blockNumber, from, to, complete);
        MessageVisitor visitor = mock(MessageVisitor.class);

        //when
        message.accept(visitor);

        //then
        verify(visitor, times(1)).apply(message);
    }
}
