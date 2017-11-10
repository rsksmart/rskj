package co.rsk.core.bc;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.test.World;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SelectionRuleTest {

    @Test
    public void smallerBlockTest() {
        byte[] lowerHash = new byte[]{0};
        byte[] biggerHash = new byte[]{1};

        assertTrue(SelectionRule.isThisBlockHashSmaller(lowerHash, biggerHash));
        assertFalse(SelectionRule.isThisBlockHashSmaller(biggerHash, lowerHash));
    }

    @Test
    public void addBlockTest() {
        Blockchain blockchain = createBlockchain();

        Block block1 = BlockGenerator.createChildBlock(blockchain.getBestBlock());
        Block block2 = BlockGenerator.createChildBlock(block1, 0, 5);
        assertTrue(SelectionRule.shouldWeAddThisBlock(block1.getDifficultyBI(),
                block2.getDifficultyBI(), block1, block2));
    }

    private static BlockChainImpl createBlockchain() {
        World world = new World();

        return world.getBlockChain();
    }
}
