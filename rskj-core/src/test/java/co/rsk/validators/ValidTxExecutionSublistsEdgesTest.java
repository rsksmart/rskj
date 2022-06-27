package co.rsk.validators;

import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedList;
import java.util.List;

public class ValidTxExecutionSublistsEdgesTest {

    private BlockHeader blockHeader;
    private Block block;
    private List txSublist;

    @BeforeEach
    public void setUp() {
        blockHeader = Mockito.mock(org.ethereum.core.BlockHeader.class);
        block = Mockito.mock(Block.class);
        txSublist = Mockito.mock(LinkedList.class);
        Mockito.when(block.getHeader()).thenReturn(blockHeader);
        Mockito.when(block.getTransactionsList()).thenReturn(txSublist);
        Mockito.when(txSublist.size()).thenReturn(10);
    }

    @Test
    void blockWithValidEdges() {
        Mockito.when(blockHeader.getTxExecutionSublistsEdges()).thenReturn(new short[]{2, 5, 6});

        ValidTxExecutionSublistsEdgesRule rule = new ValidTxExecutionSublistsEdgesRule();
        Assertions.assertTrue(rule.isValid(block));
    }

    @Test
    void blockWithNullEdges() {
        Mockito.when(blockHeader.getTxExecutionSublistsEdges()).thenReturn(null);

        ValidTxExecutionSublistsEdgesRule rule = new ValidTxExecutionSublistsEdgesRule();
        Assertions.assertTrue(rule.isValid(block));
    }

    @Test
    void blockWithEmptyEdges() {
        Mockito.when(blockHeader.getTxExecutionSublistsEdges()).thenReturn(new short[0]);

        ValidTxExecutionSublistsEdgesRule rule = new ValidTxExecutionSublistsEdgesRule();
        Assertions.assertTrue(rule.isValid(block));
    }

    @Test
    void blockWithTooManyEdges() {
        Mockito.when(blockHeader.getTxExecutionSublistsEdges()).thenReturn(new short[]{2, 5, 6, 8, 10, 12, 14});

        ValidTxExecutionSublistsEdgesRule rule = new ValidTxExecutionSublistsEdgesRule();
        Assertions.assertFalse(rule.isValid(block));
    }

    @Test
    void blockWithOutOfBoundsEdges() {
        Mockito.when(blockHeader.getTxExecutionSublistsEdges()).thenReturn(new short[]{12});

        ValidTxExecutionSublistsEdgesRule rule = new ValidTxExecutionSublistsEdgesRule();
        Assertions.assertFalse(rule.isValid(block));
    }

    @Test
    void blockWithNegativeEdge() {
        Mockito.when(blockHeader.getTxExecutionSublistsEdges()).thenReturn(new short[]{-2});

        ValidTxExecutionSublistsEdgesRule rule = new ValidTxExecutionSublistsEdgesRule();
        Assertions.assertFalse(rule.isValid(block));
    }

    @Test
    void blockWithEmptyListDefined() {
        Mockito.when(blockHeader.getTxExecutionSublistsEdges()).thenReturn(new short[]{2, 2});

        ValidTxExecutionSublistsEdgesRule rule = new ValidTxExecutionSublistsEdgesRule();
        Assertions.assertFalse(rule.isValid(block));
    }

    @Test
    void blockWithEdgesNotInOrder() {
        Mockito.when(blockHeader.getTxExecutionSublistsEdges()).thenReturn(new short[]{2, 4, 3});

        ValidTxExecutionSublistsEdgesRule rule = new ValidTxExecutionSublistsEdgesRule();
        Assertions.assertFalse(rule.isValid(block));
    }
}
