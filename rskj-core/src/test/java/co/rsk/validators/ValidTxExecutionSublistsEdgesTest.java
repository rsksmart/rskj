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
    private List txList;
    private ValidTxExecutionSublistsEdgesRule rule;

    @BeforeEach
    public void setUp() {
        blockHeader = Mockito.mock(org.ethereum.core.BlockHeader.class);
        block = Mockito.mock(Block.class);
        txList = Mockito.mock(LinkedList.class);
        Mockito.when(block.getHeader()).thenReturn(blockHeader);
        Mockito.when(block.getTransactionsList()).thenReturn(txList);
        Mockito.when(txList.size()).thenReturn(10);

        rule = new ValidTxExecutionSublistsEdgesRule();
    }

    private void mockGetTxExecutionListsEdges (short[] edges) {
        Mockito.when(blockHeader.getTxExecutionSublistsEdges()).thenReturn(edges);
    }

    // valid cases
    @Test
    void blockWithValidEdges() {
        mockGetTxExecutionListsEdges(new short[]{2, 5, 6});

        Assertions.assertTrue(rule.isValid(block));
    }

    @Test
    void blockWithNullEdges() {
        mockGetTxExecutionListsEdges(null);

        Assertions.assertTrue(rule.isValid(block));
    }

    @Test
    void blockWithEmptyEdges() {
        mockGetTxExecutionListsEdges(new short[0]);

        Assertions.assertTrue(rule.isValid(block));
    }

    // invalid cases
    @Test
    void blockWithTooManyEdges() {
        mockGetTxExecutionListsEdges(new short[]{1, 2, 3, 4, 5});

        Assertions.assertFalse(rule.isValid(block));
    }

    @Test
    void blockWithOutOfBoundsEdgesBecauseOfRemascTx() {
        // include the last tx in a parallelized thread
        // shouldn't be valid because the last transaction
        // is the remasc transaction and cannot be parallelized
        mockGetTxExecutionListsEdges(new short[]{10});

        Assertions.assertFalse(rule.isValid(block));
    }

    @Test
    void blockWithOutOfBoundsEdges() {
        mockGetTxExecutionListsEdges(new short[]{12});

        Assertions.assertFalse(rule.isValid(block));
    }

    @Test
    void blockWithNegativeEdge() {
        mockGetTxExecutionListsEdges(new short[]{-2});

        Assertions.assertFalse(rule.isValid(block));
    }

    @Test
    void blockWithEdgeZero() {
        mockGetTxExecutionListsEdges(new short[]{0, 2});

        Assertions.assertFalse(rule.isValid(block));
    }

    @Test
    void blockWithRepeatedEdge() {
        mockGetTxExecutionListsEdges(new short[]{2, 2});

        Assertions.assertFalse(rule.isValid(block));
    }

    @Test
    void blockWithEdgesNotInOrder() {
        mockGetTxExecutionListsEdges(new short[]{2, 4, 3});

        Assertions.assertFalse(rule.isValid(block));
    }
}
