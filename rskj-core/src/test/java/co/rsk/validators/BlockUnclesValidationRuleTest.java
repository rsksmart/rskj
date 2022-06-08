package co.rsk.validators;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.core.BlockDifficulty;
import co.rsk.core.bc.FamilyUtils;
import co.rsk.db.HashMapBlocksIndex;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.BlockStore;
import org.ethereum.db.IndexedBlockStore;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by ajlopez on 18/08/2017.
 */
public class BlockUnclesValidationRuleTest {

    private final FamilyUtils familyUtils = FamilyUtils.getInstance();

    @Test
    public void rejectBlockWithSiblingUncle() {
        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();

        Block block1 = blockGenerator.createChildBlock(genesis);
        Block uncle = blockGenerator.createChildBlock(block1);
        List<BlockHeader> uncles = new ArrayList<>();
        uncles.add(uncle.getHeader());

        Block block = blockGenerator.createChildBlock(block1, null, uncles, 1, null);
        BlockStore blockStore = new IndexedBlockStore(null, new HashMapDB(), new HashMapBlocksIndex());

        blockStore.saveBlock(genesis, new BlockDifficulty(BigInteger.valueOf(1)), true);
        blockStore.saveBlock(block1, new BlockDifficulty(BigInteger.valueOf(2)), true);
        BlockUnclesValidationRule rule = new BlockUnclesValidationRule(blockStore,10, 10, new BlockHeaderCompositeRule(), new BlockHeaderParentCompositeRule(), familyUtils);

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
        BlockStore blockStore = new IndexedBlockStore(null, new HashMapDB(), new HashMapBlocksIndex());
        blockStore.saveBlock(genesis, new BlockDifficulty(BigInteger.valueOf(1)), true);
        blockStore.saveBlock(block1, new BlockDifficulty(BigInteger.valueOf(2)), true);
        BlockUnclesValidationRule rule = new BlockUnclesValidationRule(blockStore, 10, 10, new BlockHeaderCompositeRule(), new BlockHeaderParentCompositeRule(), familyUtils);

        Assert.assertFalse(rule.isValid(block));
    }
}
