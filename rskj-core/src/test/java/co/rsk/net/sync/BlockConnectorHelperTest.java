package co.rsk.net.sync;

import co.rsk.core.BlockDifficulty;
import org.ethereum.core.Block;
import org.ethereum.db.BlockStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

class BlockConnectorHelperTest {

    private BlockStore blockStore;
    private BlockConnectorHelper blockConnectorHelper;
    List<BlockConnectorHelper.BlockAndDiff> blockList;
    @BeforeEach
    void setUp() {
        blockStore = mock(BlockStore.class);
        blockList = Arrays.asList(mock(BlockConnectorHelper.BlockAndDiff.class), mock(BlockConnectorHelper.BlockAndDiff.class),mock(BlockConnectorHelper.BlockAndDiff.class));
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
        when(block1.getNumber()).thenReturn(1L);
        when(block2.getNumber()).thenReturn(2L);
        when(block1.isParentOf(block2)).thenReturn(true);
        blockList = buildBlockDifficulties(Arrays.asList(block1, block2));
        blockConnectorHelper = new BlockConnectorHelper(blockStore, blockList);
        blockConnectorHelper.startConnecting();
        verify(blockStore, times(2)).saveBlock(any(), any(), anyBoolean());
        verify(blockStore, times(0)).getBestBlock();
    }

    @Test
    void testStartConnectingWhenBlockStoreIsEmptyAndNotOrderedList() {
        when(blockStore.isEmpty()).thenReturn(true);
        Block block1 = mock(Block.class);
        Block block2 = mock(Block.class);
        when(block1.getNumber()).thenReturn(1L);
        when(block2.getNumber()).thenReturn(2L);
        when(block1.isParentOf(block2)).thenReturn(true);
        blockList = buildBlockDifficulties(Arrays.asList(block2,block1));
        blockConnectorHelper = new BlockConnectorHelper(blockStore, blockList);
        blockConnectorHelper.startConnecting();
        verify(blockStore, times(2)).saveBlock(any(), any(), anyBoolean());
        verify(blockStore, times(0)).getBestBlock();
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

        blockList = buildBlockDifficulties(Arrays.asList(block1, block2));
        blockConnectorHelper = new BlockConnectorHelper(blockStore, blockList);

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
        blockList = buildBlockDifficulties(Collections.singletonList(block2));

        blockConnectorHelper = new BlockConnectorHelper(blockStore, blockList);

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
        blockList = buildBlockDifficulties(Arrays.asList(block1, block2));
        blockConnectorHelper = new BlockConnectorHelper(blockStore, blockList);

        assertThrows(BlockConnectorException.class, () -> blockConnectorHelper.startConnecting());
    }

    List<BlockConnectorHelper.BlockAndDiff> buildBlockDifficulties(List<Block> blocks) {
        return blocks.stream().map(block -> new BlockConnectorHelper.BlockAndDiff(block, mock(BlockDifficulty.class))).collect(Collectors.toList());
    }

}