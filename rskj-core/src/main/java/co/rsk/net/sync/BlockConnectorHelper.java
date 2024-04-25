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
    private List<BlockAndDiff> blockDiffList;

    private Block child;

    public BlockConnectorHelper(BlockStore blockStore, List<BlockAndDiff> blockDiffList) {
        this.blockStore = blockStore;
        this.blockDiffList = blockDiffList;
        blockDiffList.sort(new BlockAndDiffComparator());
    }

    public void startConnecting() {
        logger.info("Start connecting Blocks");
        if (blockDiffList.isEmpty()) {
            return;
        }
        int blockIndex = blockDiffList.size() - 1;
        if (blockStore.isEmpty()) {
            child = blockDiffList.get(blockIndex).getBlock();
            logger.info("BlockStore is empty, setting child block number the last block from the list: {}");
            blockIndex--;
            blockStore.saveBlock(child, blockDiffList.get(blockIndex).getDifficulty(), true);
        } else {
            logger.info("BlockStore is not empty, getting best block");
            child = blockStore.getBestBlock();
            logger.info("Best block number: {}", child.getNumber());
        }
        while (blockIndex >= 0) {
            Block block = blockDiffList.get(blockIndex).getBlock();
            logger.info("Connecting block number: {}", block.getNumber());

            if (!block.isParentOf(child)) {
                logger.error("Block is not parent of child");
                throw new BlockConnectorException("Block is not parent of child. Block number: " + block.getNumber() + " Child number: " + child.getNumber());
            }
            BlockDifficulty blockDifficulty = blockDiffList.get(blockIndex).getDifficulty();
            blockStore.saveBlock(block, blockDifficulty, true);
            child = block;
            blockIndex--;
        }
        logger.info("Finished connecting blocks");
    }

    class BlockAndDiffComparator implements java.util.Comparator<BlockAndDiff> {
        @Override
        public int compare(BlockAndDiff o1, BlockAndDiff o2) {
            return Long.compare(o1.getBlock().getNumber(),o2.getBlock().getNumber());
        }
    }

    public static class BlockAndDiff {
        public Block block;
        public BlockDifficulty difficulty;
        public BlockAndDiff(Block block, BlockDifficulty difficulty){
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

