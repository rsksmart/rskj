package co.rsk.validators;

import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedList;
import java.util.List;

import static org.mockito.AdditionalMatchers.geq;
import static org.mockito.ArgumentMatchers.any;

class ValidTxExecutionSublistsEdgesTest {

    private BlockHeader blockHeader;
    private Block block;
    private List txList;
    private ValidTxExecutionSublistsEdgesRule rule;
    private ActivationConfig activationConfig;
    private long blockNumber = 1L;

    @BeforeEach
    public void setUp() {
        blockHeader = Mockito.mock(org.ethereum.core.BlockHeader.class);
        block = Mockito.mock(Block.class);
        txList = Mockito.mock(LinkedList.class);
        activationConfig = Mockito.mock(ActivationConfig.class);
        Mockito.when(block.getHeader()).thenReturn(blockHeader);
        Mockito.when(block.getTransactionsList()).thenReturn(txList);
        Mockito.when(blockHeader.getNumber()).thenReturn(blockNumber);
        Mockito.when(txList.size()).thenReturn(10);

        rule = new ValidTxExecutionSublistsEdgesRule(activationConfig);
    }

    private void mockGetTxExecutionListsEdges (short[] edges, boolean rskip144Activated) {
        Mockito.when(blockHeader.getTxExecutionSublistsEdges()).thenReturn(edges);
        Mockito.when(activationConfig.isActive(ConsensusRule.RSKIP144, blockNumber)).thenReturn(rskip144Activated);
    }

    // valid cases
    @Test
    void blockWithRSKIP144Deactivated() {
        mockGetTxExecutionListsEdges(null, false);

        Assertions.assertTrue(rule.isValid(block));
    }

    @Test
    void blockWithValidEdges() {
        mockGetTxExecutionListsEdges(new short[]{2, 5}, true);

        Assertions.assertTrue(rule.isValid(block));
    }

    @Test
    void blockWithEmptyEdges() {
        mockGetTxExecutionListsEdges(new short[0], true);

        Assertions.assertTrue(rule.isValid(block));
    }

    // invalid cases
    @Test
    void blockWithTooManyEdges() {
        mockGetTxExecutionListsEdges(new short[]{1, 2, 3, 4, 5}, true);

        Assertions.assertFalse(rule.isValid(block));
    }

    @Test
    void blockWithOutOfBoundsEdgesBecauseOfRemascTx() {
        // include the last tx in a parallelized thread
        // shouldn't be valid because the last transaction
        // is the remasc transaction and cannot be parallelized
        mockGetTxExecutionListsEdges(new short[]{10}, true);

        Assertions.assertFalse(rule.isValid(block));
    }

    @Test
    void blockWithOutOfBoundsEdges() {
        mockGetTxExecutionListsEdges(new short[]{12}, true);

        Assertions.assertFalse(rule.isValid(block));
    }

    @Test
    void blockWithNegativeEdge() {
        mockGetTxExecutionListsEdges(new short[]{-2}, true);

        Assertions.assertFalse(rule.isValid(block));
    }

    @Test
    void blockWithEdgeZero() {
        mockGetTxExecutionListsEdges(new short[]{0, 2}, true);

        Assertions.assertFalse(rule.isValid(block));
    }

    @Test
    void blockWithRepeatedEdge() {
        mockGetTxExecutionListsEdges(new short[]{2, 2}, true);

        Assertions.assertFalse(rule.isValid(block));
    }

    @Test
    void blockWithEdgesNotInOrder() {
        mockGetTxExecutionListsEdges(new short[]{2, 4, 3}, true);

        Assertions.assertFalse(rule.isValid(block));
    }
}
