package co.rsk.net.sync;

import co.rsk.core.BlockDifficulty;
import org.ethereum.core.Block;
import org.ethereum.db.BlockStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    List<BlockConnectorHelper.BlockAndDifficulty> blockAndDifficultiesList;

    @BeforeEach
    void setUp() {
        blockAndDifficultiesList = Arrays.asList(mock(BlockConnectorHelper.BlockAndDifficulty.class), mock(BlockConnectorHelper.BlockAndDifficulty.class), mock(BlockConnectorHelper.BlockAndDifficulty.class));
    }

    @Test
    void testStartConnectingWhenBlockListIsEmpty() {
        blockConnectorHelper = new BlockConnectorHelper(blockStore, Collections.emptyList());
        blockConnectorHelper.startConnecting();
        verify(blockStore, never()).saveBlock(any(), any(), anyBoolean());
    }

    @Test
    void testStartConnectingWhenBlockStoreIsEmpty() {
        when(blockStore.isEmpty()).thenReturn(true);

        Block block1 = mock(Block.class);
        Block block2 = mock(Block.class);
        Block block3 = mock(Block.class);
        when(block1.getNumber()).thenReturn(1L);
        when(block2.getNumber()).thenReturn(2L);
        when(block3.getNumber()).thenReturn(3L);
        when(block1.isParentOf(block2)).thenReturn(true);
        when(block2.isParentOf(block3)).thenReturn(true);


        BlockDifficulty diff1 = new BlockDifficulty(BigInteger.valueOf(1));
        BlockDifficulty diff2 = new BlockDifficulty(BigInteger.valueOf(2));
        BlockDifficulty diff3 = new BlockDifficulty(BigInteger.valueOf(3));
        blockAndDifficultiesList = buildBlockDifficulties(Arrays.asList(block1, block2,block3),
                Arrays.asList(diff1, diff2,diff3));

        blockConnectorHelper = new BlockConnectorHelper(blockStore, blockAndDifficultiesList);

        blockConnectorHelper.startConnecting();

        verify(blockStore, times(3)).saveBlock(blockCaptor.capture(), difficultyCaptor.capture(), anyBoolean());
        verify(blockStore, times(0)).getBestBlock();
        List<Block> savedBlocks = blockCaptor.getAllValues();
        List<BlockDifficulty> savedDifficulties = difficultyCaptor.getAllValues();
        assertEquals(block3, savedBlocks.get(0));
        assertEquals(diff3, savedDifficulties.get(0));
        assertEquals(block2, savedBlocks.get(1));
        assertEquals(diff2, savedDifficulties.get(1));
        assertEquals(block1, savedBlocks.get(2));
        assertEquals(diff1, savedDifficulties.get(2));

    }

    @Test
    void testStartConnectingWhenBlockStoreIsEmptyAndNotOrderedList() {
        when(blockStore.isEmpty()).thenReturn(true);

        Block block1 = mock(Block.class);
        Block block2 = mock(Block.class);
        when(block1.getNumber()).thenReturn(1L);
        when(block2.getNumber()).thenReturn(2L);
        when(block1.isParentOf(block2)).thenReturn(true);

        BlockDifficulty diff1 = new BlockDifficulty(BigInteger.valueOf(1));
        BlockDifficulty diff2 = new BlockDifficulty(BigInteger.valueOf(2));
        blockAndDifficultiesList = buildBlockDifficulties(Arrays.asList(block2, block1),
                Arrays.asList(diff2, diff1));

        blockConnectorHelper = new BlockConnectorHelper(blockStore, blockAndDifficultiesList);

        blockConnectorHelper.startConnecting();

        verify(blockStore, times(2)).saveBlock(blockCaptor.capture(), difficultyCaptor.capture(), anyBoolean());
        verify(blockStore, times(0)).getBestBlock();
        List<Block> savedBlocks = blockCaptor.getAllValues();
        List<BlockDifficulty> savedDifficulties = difficultyCaptor.getAllValues();
        assertEquals(block2, savedBlocks.get(0));
        assertEquals(diff2, savedDifficulties.get(0));
        assertEquals(block1, savedBlocks.get(1));
        assertEquals(diff1, savedDifficulties.get(1));
    }

    @Test
    void testStartConnectingWhenBlockStoreIsNotEmpty() {
        Block block1 = mock(Block.class);
        Block block2 = mock(Block.class);
        Block block3 = mock(Block.class);

        when(block1.getNumber()).thenReturn(1L);
        when(block2.getNumber()).thenReturn(2L);
        when(block3.getNumber()).thenReturn(3L);
        when(block1.isParentOf(block2)).thenReturn(true);
        when(block2.isParentOf(block3)).thenReturn(true);

        when(blockStore.isEmpty()).thenReturn(false);
        when(blockStore.getBestBlock()).thenReturn(block3);

        blockAndDifficultiesList = buildBlockDifficulties(Arrays.asList(block1, block2), Arrays.asList(mock(BlockDifficulty.class), mock(BlockDifficulty.class)));
        blockConnectorHelper = new BlockConnectorHelper(blockStore, blockAndDifficultiesList);

        blockConnectorHelper.startConnecting();
        verify(blockStore, times(1)).getBestBlock();
        verify(blockStore, times(2)).saveBlock(any(), any(), anyBoolean());
    }

    @Test
    void whenBlockIsNotParentOfExistingBestBlock() {
        Block block2 = mock(Block.class);
        Block block3 = mock(Block.class);
        when(block2.getNumber()).thenReturn(2L);
        when(block3.getNumber()).thenReturn(3L);
        when(block2.isParentOf(block3)).thenReturn(false);
        blockAndDifficultiesList = buildBlockDifficulties(Collections.singletonList(block2),
                Collections.singletonList(mock(BlockDifficulty.class)));

        blockConnectorHelper = new BlockConnectorHelper(blockStore, blockAndDifficultiesList);

        when(blockStore.isEmpty()).thenReturn(false);
        when(blockStore.getBestBlock()).thenReturn(block3);

        assertThrows(BlockConnectorException.class, () -> blockConnectorHelper.startConnecting());
    }

    @Test
    void testStartConnectingWhenBlockIsNotParentOfChild() {
        Block block1 = mock(Block.class);
        Block block2 = mock(Block.class);
        when(block1.getNumber()).thenReturn(1L);
        when(block2.getNumber()).thenReturn(2L);
        when(block1.isParentOf(block2)).thenReturn(false);
        when(blockStore.isEmpty()).thenReturn(true);
        blockAndDifficultiesList = buildBlockDifficulties(Arrays.asList(block1, block2),
                Arrays.asList(mock(BlockDifficulty.class), mock(BlockDifficulty.class)));
        blockConnectorHelper = new BlockConnectorHelper(blockStore, blockAndDifficultiesList);

        assertThrows(BlockConnectorException.class, () -> blockConnectorHelper.startConnecting());
    }

    List<BlockConnectorHelper.BlockAndDifficulty> buildBlockDifficulties(List<Block> blocks, List<BlockDifficulty> difficulties) {
        int i = 0;
        List<BlockConnectorHelper.BlockAndDifficulty> list = new ArrayList<>();
        for (Block block : blocks) {
            list.add(new BlockConnectorHelper.BlockAndDifficulty(block, difficulties.get(i)));
            i++;
        }
        return list;
    }

}