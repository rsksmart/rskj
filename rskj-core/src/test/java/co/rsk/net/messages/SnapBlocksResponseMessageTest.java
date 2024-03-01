package co.rsk.net.messages;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.BlockDifficulty;
import co.rsk.db.HashMapBlocksIndex;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.BlockStore;
import org.ethereum.db.IndexedBlockStore;
import org.ethereum.util.RLP;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class SnapBlocksResponseMessageTest {

    private final TestSystemProperties config = new TestSystemProperties();
    private final BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());
    private final BlockStore indexedBlockStore = new IndexedBlockStore(blockFactory, new HashMapDB(), new HashMapBlocksIndex());

    private final Block block4Test = new BlockGenerator().getBlock(1);
    private List<Block> blockList = Arrays.asList(new BlockGenerator().getBlock(1));
    private List<BlockDifficulty> blockDifficulties = Arrays.asList(indexedBlockStore.getTotalDifficultyForHash(block4Test.getHash().getBytes()));
    private SnapBlocksResponseMessage underTest = new SnapBlocksResponseMessage(blockList, blockDifficulties);


    @Test
    void getMessageType_returnCorrectMessageType() {
        //given-when
        MessageType messageType = underTest.getMessageType();

        //then
        assertEquals(MessageType.SNAP_BLOCKS_RESPONSE_MESSAGE, messageType);
    }

    @Test
    void getEncodedMessage_returnExpectedByteArray() {
        //given default block 4 test
        byte[] expectedEncodedMessage = RLP.encodeList(
                RLP.encodeList(RLP.encode(block4Test.getEncoded())),
                RLP.encodeList(RLP.encode(blockDifficulties.get(0).getBytes())));
        //when
        byte[] encodedMessage = underTest.getEncodedMessage();

        //then
        assertThat(encodedMessage)
                .isEqualTo(expectedEncodedMessage);
    }

    @Test
    void getDifficulties_returnTheExpectedValue() {
        //given default block 4 test

        //when
        List<BlockDifficulty> difficultiesReturned = underTest.getDifficulties();
        //then
        assertThat(difficultiesReturned)
                .isEqualTo(blockDifficulties);
    }

    @Test
    void getBlocks_returnTheExpectedValue() {
        //given default block 4 test

        //when
        List<Block> blocksReturned = underTest.getBlocks();
        //then
        assertThat(blocksReturned)
                .isEqualTo(blockList);
    }

    @Test
    void givenAcceptIsCalled_messageVisitorIsAppliedForMessage() {
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