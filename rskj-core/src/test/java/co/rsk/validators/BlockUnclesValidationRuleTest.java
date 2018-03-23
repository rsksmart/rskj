package co.rsk.validators;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.BlockDifficulty;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.core.bc.BlockChainImplTest;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.db.BlockStore;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by ajlopez on 18/08/2017.
 */
public class BlockUnclesValidationRuleTest {

    private final TestSystemProperties config = new TestSystemProperties();

    @Test
    public void rejectBlockWithSiblingUncle() {
        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();

        Block block1 = blockGenerator.createChildBlock(genesis);
        Block uncle = blockGenerator.createChildBlock(block1);
        List<BlockHeader> uncles = new ArrayList<>();
        uncles.add(uncle.getHeader());

        Block block = blockGenerator.createChildBlock(block1, null, uncles, 1, null);

        BlockChainImpl blockChain = BlockChainImplTest.createBlockChain();
        BlockStore store = blockChain.getBlockStore();

        store.saveBlock(genesis, new BlockDifficulty(BigInteger.valueOf(1)), true);
        store.saveBlock(block1, new BlockDifficulty(BigInteger.valueOf(2)), true);

        BlockUnclesValidationRule rule = new BlockUnclesValidationRule(config, store, 10, 10, new BlockCompositeRule(), new BlockParentCompositeRule());

        Assert.assertFalse(rule.isValid(block));
    }

    @Test
    public void rejectBlockWithUncleHavingHigherNumber() {
        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();

        Block block1 = blockGenerator.createChildBlock(genesis);
        Block uncle1 = blockGenerator.createChildBlock(block1);
        Block uncle2 = blockGenerator.createChildBlock(uncle1);
        List<BlockHeader> uncles = new ArrayList<>();
        uncles.add(uncle2.getHeader());

        Block block = blockGenerator.createChildBlock(block1, null, uncles, 1, null);

        BlockChainImpl blockChain = BlockChainImplTest.createBlockChain();
        BlockStore store = blockChain.getBlockStore();

        store.saveBlock(genesis, new BlockDifficulty(BigInteger.valueOf(1)), true);
        store.saveBlock(block1, new BlockDifficulty(BigInteger.valueOf(2)), true);

        BlockUnclesValidationRule rule = new BlockUnclesValidationRule(config, store, 10, 10, new BlockCompositeRule(), new BlockParentCompositeRule());

        Assert.assertFalse(rule.isValid(block));
    }
}
