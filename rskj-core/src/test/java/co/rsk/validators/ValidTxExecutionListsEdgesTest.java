package co.rsk.validators;

import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;

import java.util.LinkedList;
import java.util.List;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ValidTxExecutionListsEdgesTest {

    private BlockHeader blockHeader;
    private Block block;

    @BeforeAll
    public void setUp() {
        blockHeader = Mockito.mock(org.ethereum.core.BlockHeader.class);
        block = Mockito.mock(Block.class);
        List txList = Mockito.mock(LinkedList.class);
        Mockito.when(block.getHeader()).thenReturn(blockHeader);
        Mockito.when(block.getTransactionsList()).thenReturn(txList);
        Mockito.when(txList.size()).thenReturn(10);
    }

    @Test
    public void blockWithValidEdges() {
        Mockito.when(blockHeader.getTxExecutionListsEdges()).thenReturn(new short[]{2, 5, 6});

        ValidTxExecutionListsEdgesRule rule = new ValidTxExecutionListsEdgesRule();
        Assertions.assertTrue(rule.isValid(block));
    }

    @Test
    public void blockWithNullEdges() {
        Mockito.when(blockHeader.getTxExecutionListsEdges()).thenReturn(null);

        ValidTxExecutionListsEdgesRule rule = new ValidTxExecutionListsEdgesRule();
        Assertions.assertTrue(rule.isValid(block));
    }

    @Test
    public void blockWithEmptyEdges() {
        Mockito.when(blockHeader.getTxExecutionListsEdges()).thenReturn(new short[0]);

        ValidTxExecutionListsEdgesRule rule = new ValidTxExecutionListsEdgesRule();
        Assertions.assertTrue(rule.isValid(block));
    }

    @Test
    public void blockWithTooManyEdges() {
        Mockito.when(blockHeader.getTxExecutionListsEdges()).thenReturn(new short[]{2, 5, 6, 8, 10, 12, 14});

        ValidTxExecutionListsEdgesRule rule = new ValidTxExecutionListsEdgesRule();
        Assertions.assertFalse(rule.isValid(block));
    }

    @Test
    public void blockWithOutOfBoundsEdges() {
        Mockito.when(blockHeader.getTxExecutionListsEdges()).thenReturn(new short[]{12});

        ValidTxExecutionListsEdgesRule rule = new ValidTxExecutionListsEdgesRule();
        Assertions.assertFalse(rule.isValid(block));
    }

    @Test
    public void blockWithNegativeEdge() {
        Mockito.when(blockHeader.getTxExecutionListsEdges()).thenReturn(new short[]{-2});

        ValidTxExecutionListsEdgesRule rule = new ValidTxExecutionListsEdgesRule();
        Assertions.assertFalse(rule.isValid(block));
    }

    @Test
    public void blockWithEmptyListDefined() {
        Mockito.when(blockHeader.getTxExecutionListsEdges()).thenReturn(new short[]{2, 2});

        ValidTxExecutionListsEdgesRule rule = new ValidTxExecutionListsEdgesRule();
        Assertions.assertFalse(rule.isValid(block));
    }

    @Test
    public void blockWithEdgesNotInOrder() {
        Mockito.when(blockHeader.getTxExecutionListsEdges()).thenReturn(new short[]{2, 4, 3});

        ValidTxExecutionListsEdgesRule rule = new ValidTxExecutionListsEdgesRule();
        Assertions.assertFalse(rule.isValid(block));
    }
}
