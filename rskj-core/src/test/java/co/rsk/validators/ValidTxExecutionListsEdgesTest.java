package co.rsk.validators;

import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.LinkedList;
import java.util.List;

public class ValidTxExecutionListsEdgesTest {

    private BlockHeader blockHeader;
    private Block block;
    private List txList;
    private ValidTxExecutionListsEdgesRule rule;

    @Before
    public void setUp() {
        blockHeader = Mockito.mock(org.ethereum.core.BlockHeader.class);
        block = Mockito.mock(Block.class);
        txList = Mockito.mock(LinkedList.class);
        Mockito.when(block.getHeader()).thenReturn(blockHeader);
        Mockito.when(block.getTransactionsList()).thenReturn(txList);
        Mockito.when(txList.size()).thenReturn(10);

        rule = new ValidTxExecutionListsEdgesRule();
    }

    private void mockGetTxExecutionListsEdges (short[] edges) {
        Mockito.when(blockHeader.getTxExecutionListsEdges()).thenReturn(edges);
    }

    // valid cases
    @Test
    public void blockWithValidEdges() {
        mockGetTxExecutionListsEdges(new short[]{2, 5, 6});

        Assert.assertTrue(rule.isValid(block));
    }

    @Test
    public void blockWithNullEdges() {
        mockGetTxExecutionListsEdges(null);

        Assert.assertTrue(rule.isValid(block));
    }

    @Test
    public void blockWithEmptyEdges() {
        mockGetTxExecutionListsEdges(new short[0]);

        Assert.assertTrue(rule.isValid(block));
    }

    // invalid cases
    @Test
    public void blockWithTooManyEdges() {
        mockGetTxExecutionListsEdges(new short[]{1, 2, 3, 4, 5});

        Assert.assertFalse(rule.isValid(block));
    }

    @Test
    public void blockWithOutOfBoundsEdges() {
        mockGetTxExecutionListsEdges(new short[]{12});

        Assert.assertFalse(rule.isValid(block));
    }

    @Test
    public void blockWithNegativeEdge() {
        mockGetTxExecutionListsEdges(new short[]{-2});

        Assert.assertFalse(rule.isValid(block));
    }

    @Test
    public void blockWithEdgeZero() {
        mockGetTxExecutionListsEdges(new short[]{0, 2});

        Assert.assertFalse(rule.isValid(block));
    }

    @Test
    public void blockWithRepeatedEdge() {
        mockGetTxExecutionListsEdges(new short[]{2, 2});

        Assert.assertFalse(rule.isValid(block));
    }

    @Test
    public void blockWithEdgesNotInOrder() {
        mockGetTxExecutionListsEdges(new short[]{2, 4, 3});

        Assert.assertFalse(rule.isValid(block));
    }
}
