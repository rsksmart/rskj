package co.rsk.core.bc;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.test.World;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Blockchain;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

        Block lowDifficultyBlock = BlockGenerator.createChildBlock(blockchain.getBestBlock());
        Block highDifficultyBlock = BlockGenerator.createChildBlock(lowDifficultyBlock, 0, 5);
        Block highDifficultyBlockWithMoreFees = BlockGenerator.createChildBlock(lowDifficultyBlock, 10l, new ArrayList<>(), highDifficultyBlock.getDifficulty());
        Block highDifficultyBlock2 = BlockGenerator.createChildBlock(lowDifficultyBlock, 0, 5);

        //diff test
        assertFalse(SelectionRule.shouldWeAddThisBlock(lowDifficultyBlock.getDifficultyBI(),
                highDifficultyBlock.getDifficultyBI(), lowDifficultyBlock, highDifficultyBlock));
        assertTrue(SelectionRule.shouldWeAddThisBlock(highDifficultyBlock.getDifficultyBI(),
                lowDifficultyBlock.getDifficultyBI(), highDifficultyBlock, lowDifficultyBlock));
        // At same difficulty, more fees
        assertFalse(SelectionRule.shouldWeAddThisBlock(highDifficultyBlockWithMoreFees.getDifficultyBI(),
                highDifficultyBlock.getDifficultyBI(), highDifficultyBlockWithMoreFees, highDifficultyBlock));
        //Low hash is proved in smallerBlockHashTest
    }

    private static BlockChainImpl createBlockchain() {
        World world = new World();

        return world.getBlockChain();
    }
}
