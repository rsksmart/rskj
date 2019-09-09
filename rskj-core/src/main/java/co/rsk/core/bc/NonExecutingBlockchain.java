package co.rsk.core.bc;

import co.rsk.core.BlockDifficulty;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.ImportResult;
import org.ethereum.db.BlockInformation;
import org.ethereum.db.BlockStore;
import org.ethereum.db.TransactionInfo;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Non executing blockchain is a simple blockchain implementation. It only validates that the connected block is
 * continuous to the current best block.
 * <p>
 * It assumes there are no forks in the blockchain.
 * <p>
 * It does not execute the connected block.
 * <p>
 * It does not generate receipts.
 */
public class NonExecutingBlockchain implements Blockchain {

    private final BlockStore blockStore;
    private BlockDifficulty totalDifficulty;
    private Block bestBlock;

    public NonExecutingBlockchain(BlockStore blockStore) {

        this.blockStore = blockStore;

        Block bestBlock = blockStore.getBestBlock();
        if (bestBlock == null) {
            throw new IllegalStateException("Blockstore has no best block");
        }

        this.bestBlock = bestBlock;
        this.totalDifficulty = blockStore
                .getTotalDifficultyForHash(this.bestBlock.getHash().getBytes());
    }

    @Override
    public Block getBlockByNumber(long number) {
        return blockStore.getChainBlockByNumber(number);
    }

    @Override
    public Block getBlockByHash(byte[] hash) {
        return blockStore.getBlockByHash(hash);
    }

    @Override
    public BlockDifficulty getTotalDifficulty() {
        return totalDifficulty;
    }

    @Override
    public Block getBestBlock() {
        return bestBlock;
    }

    @Override
    public long getSize() {
        return bestBlock.getNumber() + 1;
    }

    @Override
    public long getFirstBlockNumber() {
        return blockStore.getMinNumber();
    }

    /**
     * Connects a block to the current best block.
     * @param newBestBlock Must be a children of best block.
     * @return The import result indicating the outcome of the connection.
     */
    @Override
    public synchronized ImportResult tryToConnect(Block newBestBlock) {
        if (!bestBlock.isParentOf(newBestBlock)) {
            return ImportResult.NO_PARENT;
        }

        BlockDifficulty newDifficulty = totalDifficulty.add(newBestBlock.getCumulativeDifficulty());

        totalDifficulty = newDifficulty;
        bestBlock = newBestBlock;
        blockStore.saveBlock(newBestBlock, newDifficulty, true);
        blockStore.flush();
        return ImportResult.IMPORTED_BEST;
    }

    /**
     * One cannot set an arbitrary blockchain status. Use tryToConnect. This method does nothing.
     * @param block
     * @param totalDifficulty
     */
    @Override
    public void setStatus(Block block, BlockDifficulty totalDifficulty) {
    }

    @Override
    public BlockChainStatus getStatus() {
        return new BlockChainStatus(bestBlock, totalDifficulty);
    }


    /**
     * This blockchain does not execute blocks and does not generate receipts.
     * @param hash
     * @return Nothing
     */
    @Nullable
    @Override
    public TransactionInfo getTransactionInfo(byte[] hash) {
        return null;
    }

    @Override
    public byte[] getBestBlockHash() {
        return bestBlock.getHash().getBytes();
    }

    @Override
    public List<Block> getBlocksByNumber(long blockNr) {
        return blockStore.getChainBlocksByNumber(blockNr);
    }

    /**
     * Remove blocks is not supported.
     * @param blockNr
     */
    @Override
    public void removeBlocksByNumber(long blockNr) {
    }

    @Override
    public List<BlockInformation> getBlocksInformationByNumber(long number) {
        return blockStore.getBlocksInformationByNumber(number);
    }

    @Override
    public boolean hasBlockInSomeBlockchain(byte[] hash) {
        return blockStore.isBlockExist(hash);
    }
}
