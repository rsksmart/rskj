package co.rsk.net.utils;

import co.rsk.crypto.Keccak256;
import co.rsk.net.AsyncNodeBlockProcessor;
import co.rsk.net.BlockProcessResult;
import co.rsk.net.Peer;
import org.ethereum.core.Block;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Utility class that allows to wait for some particular block to be processed.
 */
public class AsyncNodeBlockProcessorListener implements AsyncNodeBlockProcessor.Listener {

    private final BlockingQueue<Block> processedBlocks = new LinkedBlockingQueue<>();

    @Override
    public void onBlockProcessed(@Nonnull AsyncNodeBlockProcessor blockProcessor,
                                 @Nullable Peer sender, @Nonnull Block block,
                                 @Nonnull BlockProcessResult blockProcessResult) {
        processedBlocks.add(block);
    }

    public void waitForBlock(@Nonnull Keccak256 hash) throws InterruptedException {
        Block block;
        do {
            block = processedBlocks.take();
        } while (!block.getHash().equals(hash));
    }
}
