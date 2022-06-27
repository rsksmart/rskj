package co.rsk.validators;

import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.LinkedList;
import java.util.List;

public class ValidTxExecutionSublistsEdgesTest {

    private BlockHeader blockHeader;
    private Block block;
    private List txSublist;

    @Before
    public void setUp() {
        blockHeader = Mockito.mock(org.ethereum.core.BlockHeader.class);
        block = Mockito.mock(Block.class);
        txSublist = Mockito.mock(LinkedList.class);
        Mockito.when(block.getHeader()).thenReturn(blockHeader);
        Mockito.when(block.getTransactionsList()).thenReturn(txSublist);
        Mockito.when(txSublist.size()).thenReturn(10);
    }

    @Test
    public void blockWithValidEdges() {
        Mockito.when(blockHeader.getTxExecutionSublistsEdges()).thenReturn(new short[]{2, 5, 6});

        ValidTxExecutionSublistsEdgesRule rule = new ValidTxExecutionSublistsEdgesRule();
        Assert.assertTrue(rule.isValid(block));
    }

    @Test
    public void blockWithNullEdges() {
        Mockito.when(blockHeader.getTxExecutionSublistsEdges()).thenReturn(null);

        ValidTxExecutionSublistsEdgesRule rule = new ValidTxExecutionSublistsEdgesRule();
        Assert.assertTrue(rule.isValid(block));
    }

    @Test
    public void blockWithEmptyEdges() {
        Mockito.when(blockHeader.getTxExecutionSublistsEdges()).thenReturn(new short[0]);

        ValidTxExecutionSublistsEdgesRule rule = new ValidTxExecutionSublistsEdgesRule();
        Assert.assertTrue(rule.isValid(block));
    }

    @Test
    public void blockWithTooManyEdges() {
        Mockito.when(blockHeader.getTxExecutionSublistsEdges()).thenReturn(new short[]{2, 5, 6, 8, 10, 12, 14});

        ValidTxExecutionSublistsEdgesRule rule = new ValidTxExecutionSublistsEdgesRule();
        Assert.assertFalse(rule.isValid(block));
    }

    @Test
    public void blockWithOutOfBoundsEdges() {
        Mockito.when(blockHeader.getTxExecutionSublistsEdges()).thenReturn(new short[]{12});

        ValidTxExecutionSublistsEdgesRule rule = new ValidTxExecutionSublistsEdgesRule();
        Assert.assertFalse(rule.isValid(block));
    }

    @Test
    public void blockWithNegativeEdge() {
        Mockito.when(blockHeader.getTxExecutionSublistsEdges()).thenReturn(new short[]{-2});

        ValidTxExecutionSublistsEdgesRule rule = new ValidTxExecutionSublistsEdgesRule();
        Assert.assertFalse(rule.isValid(block));
    }

    @Test
    public void blockWithEmptyListDefined() {
        Mockito.when(blockHeader.getTxExecutionSublistsEdges()).thenReturn(new short[]{2, 2});

        ValidTxExecutionSublistsEdgesRule rule = new ValidTxExecutionSublistsEdgesRule();
        Assert.assertFalse(rule.isValid(block));
    }

    @Test
    public void blockWithEdgesNotInOrder() {
        Mockito.when(blockHeader.getTxExecutionSublistsEdges()).thenReturn(new short[]{2, 4, 3});

        ValidTxExecutionSublistsEdgesRule rule = new ValidTxExecutionSublistsEdgesRule();
        Assert.assertFalse(rule.isValid(block));
    }
}
