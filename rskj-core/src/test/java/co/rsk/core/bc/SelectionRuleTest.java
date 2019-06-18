package co.rsk.core.bc;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import co.rsk.blockchain.utils.BlockGenerator;
import java.util.ArrayList;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.util.RskTestFactory;
import org.junit.Test;

public class SelectionRuleTest {

    @Test
    public void smallerBlockHashTest() {
        byte[] lowerHash = new byte[]{0};
        byte[] biggerHash = new byte[]{1};

        assertTrue(SelectionRule.isThisBlockHashSmaller(lowerHash, biggerHash));
        assertFalse(SelectionRule.isThisBlockHashSmaller(biggerHash, lowerHash));
    }

    @Test
    public void addBlockTest() {
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
