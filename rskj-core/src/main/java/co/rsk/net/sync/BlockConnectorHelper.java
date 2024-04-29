package co.rsk.net.sync;

import co.rsk.core.BlockDifficulty;
import org.ethereum.core.Block;
import org.ethereum.db.BlockStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class BlockConnectorHelper {
    private static final Logger logger = LoggerFactory.getLogger("SnapBlockConnector");
    private final BlockStore blockStore;
    private final List<BlockAndDifficulty> blockAndDifficultiesList;
    public BlockConnectorHelper(BlockStore blockStore, List<BlockAndDifficulty> blockAndDifficultiesList) {
        this.blockStore = blockStore;
        this.blockAndDifficultiesList = blockAndDifficultiesList;
        blockAndDifficultiesList.sort(new BlockAndDiffComparator());
    }

    public void startConnecting() {
        Block child = null;
        logger.info("Start connecting Blocks");
        if (blockAndDifficultiesList.isEmpty()) {
            return;
        }
        int blockIndex = blockAndDifficultiesList.size() - 1;
        if (blockStore.isEmpty()) {
            BlockAndDifficulty blockAndDifficulty = blockAndDifficultiesList.get(blockIndex);
            child = blockAndDifficulty.getBlock();
            logger.debug("BlockStore is empty, setting child block number the last block from the list: {}",child.getNumber());
            blockStore.saveBlock(child, blockAndDifficulty.getDifficulty(), true);
            blockIndex--;
        } else {
            logger.debug("BlockStore is not empty, getting best block");
            child = blockStore.getBestBlock();
            logger.debug("Best block number: {}", child.getNumber());
        }
        while (blockIndex >= 0) {
            BlockAndDifficulty currentBlockAndDifficulty = blockAndDifficultiesList.get(blockIndex);
            Block currentBlock = currentBlockAndDifficulty.getBlock();
            logger.info("Connecting block number: {}", currentBlock.getNumber());

            if (!currentBlock.isParentOf(child)) {
                logger.error("Block is not parent of child");
                throw new BlockConnectorException(currentBlock.getNumber(), child.getNumber());
            }
            blockStore.saveBlock(currentBlock, currentBlockAndDifficulty.getDifficulty(), true);
            child = currentBlock;
            blockIndex--;
        }
        logger.debug("Finished connecting blocks");
    }

    static class BlockAndDiffComparator implements java.util.Comparator<BlockAndDifficulty> {
        @Override
        public int compare(BlockAndDifficulty o1, BlockAndDifficulty o2) {
            return Long.compare(o1.getBlock().getNumber(),o2.getBlock().getNumber());
        }
    }

    public static class BlockAndDifficulty {
        private final Block block;
        private final BlockDifficulty difficulty;
        public BlockAndDifficulty(Block block, BlockDifficulty difficulty){
            this.block = block;
            this.difficulty = difficulty;
        }

        public Block getBlock() {
            return block;
        }

        public BlockDifficulty getDifficulty() {
            return difficulty;
        }
    }
}

