package co.rsk.net.sync;

import co.rsk.core.BlockDifficulty;
import co.rsk.crypto.Keccak256;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.ethereum.core.Block;
import org.ethereum.db.BlockStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BlockConnectorHelperTest {

    @Mock
    private BlockStore blockStore;
    @Captor
    ArgumentCaptor<Block> blockCaptor;
    @Captor
    ArgumentCaptor<BlockDifficulty> difficultyCaptor;
    private BlockConnectorHelper blockConnectorHelper;
    List<Pair<Block,BlockDifficulty>> blockAndDifficultiesList;

    @BeforeEach
    void setUp() {
        blockAndDifficultiesList = Arrays.asList(mock(Pair.class), mock(Pair.class), mock(Pair.class));
    }

    @Test
    void testStartConnectingWhenBlockListIsEmpty() {
        blockConnectorHelper = new BlockConnectorHelper(blockStore);
        blockConnectorHelper.startConnecting(Collections.emptyList());
        verify(blockStore, never()).saveBlock(any(), any(), anyBoolean());
    }

    @Test
    void testStartConnectingWhenBlockListIsNotEmpty() {
        Block block1 = mockBlock( new byte[] { 1 });
        Block block2 = mockBlock(new byte[] { 2 });

        blockAndDifficultiesList = buildBlockDifficulties(Arrays.asList(block1, block2), Arrays.asList(mock(BlockDifficulty.class), mock(BlockDifficulty.class)));
        blockConnectorHelper = new BlockConnectorHelper(blockStore);

        blockConnectorHelper.startConnecting(blockAndDifficultiesList);
        verify(blockStore, times(2)).saveBlock(any(), any(), anyBoolean());
    }

    private Block mockBlock(byte[] hashBytes) {
        Block block = mock(Block.class);
        Keccak256 hash = mock(Keccak256.class);
        when(hash.getBytes()).thenReturn(hashBytes);
        when(block.getHash()).thenReturn(hash);
        return block;
    }

    private List<Pair<Block,BlockDifficulty>> buildBlockDifficulties(List<Block> blocks, List<BlockDifficulty> difficulties) {
        int i = 0;
        List<Pair<Block,BlockDifficulty>> list = new ArrayList<>();
        for (Block block : blocks) {
            list.add(new ImmutablePair<>(block, difficulties.get(i)));
            i++;
        }
        return list;
    }
}
