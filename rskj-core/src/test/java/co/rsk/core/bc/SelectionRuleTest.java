package co.rsk.core.bc;

import co.rsk.blockchain.utils.BlockGenerator;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.util.RskTestFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectionRuleTest {

    @Test
    void smallerBlockHashTest() {
        byte[] lowerHash = new byte[]{0};
        byte[] biggerHash = new byte[]{1};

        assertTrue(SelectionRule.isThisBlockHashSmaller(lowerHash, biggerHash));
        assertFalse(SelectionRule.isThisBlockHashSmaller(biggerHash, lowerHash));
    }

    @Test
    void addBlockTest() {
        Blockchain blockchain = createBlockchain();


        BlockGenerator blockGenerator = new BlockGenerator();
        Block lowDifficultyBlock = blockGenerator.createChildBlock(blockchain.getBestBlock(), 0, 1);
        Block highDifficultyBlock = blockGenerator.createChildBlock(lowDifficultyBlock, 0, 5);
        Block highDifficultyBlockWithMoreFees = blockGenerator.createChildBlock(lowDifficultyBlock, 10L, new ArrayList<>(), highDifficultyBlock.getDifficulty().getBytes());

        //diff test
        assertFalse(SelectionRule.shouldWeAddThisBlock(lowDifficultyBlock.getDifficulty(),
                highDifficultyBlock.getDifficulty(), lowDifficultyBlock, highDifficultyBlock));
        assertTrue(SelectionRule.shouldWeAddThisBlock(highDifficultyBlock.getDifficulty(),
                lowDifficultyBlock.getDifficulty(), highDifficultyBlock, lowDifficultyBlock));
        // At same difficulty, more fees
        assertTrue(SelectionRule.shouldWeAddThisBlock(highDifficultyBlockWithMoreFees.getDifficulty(),
                highDifficultyBlock.getDifficulty(), highDifficultyBlockWithMoreFees, highDifficultyBlock));
        //Low hash is proved in smallerBlockHashTest
    }

    private static Blockchain createBlockchain() {
        RskTestFactory factory = new RskTestFactory();
        return factory.getBlockchain();
    }
}
