package co.rsk.net.utils;

import co.rsk.net.sync.ConnectionPointFinder;
import co.rsk.net.sync.SyncConfiguration;

public final class SyncUtils {
    public static int syncSetupRequests(long bestBlock, long currentBestBlock, SyncConfiguration syncConfiguration) {
        if (currentBestBlock >= bestBlock) {
            return 1;
        }

        // 192 and 5 are configuration values that are hardcoded for testing
        // 192: chunk size
        // 5: max chunks to download in skeleton
        float chunkSize = (float)syncConfiguration.getChunkSize();
        int maxSkeletonChunks = syncConfiguration.getMaxSkeletonChunks();
        int skippedChunks = (int) Math.floor(currentBestBlock / chunkSize);
        int chunksToBestBlock = (int) Math.ceil(bestBlock / chunkSize);
        int chunksToDownload = Math.max(1, Math.min(maxSkeletonChunks, chunksToBestBlock - skippedChunks));
        return 1 + // get status
                1 + // check best header
                binarySearchExpectedRequests(bestBlock, currentBestBlock) + // find connection point
                1 + // get skeleton
                chunksToDownload; // get headers chunks
    }

    private static int binarySearchExpectedRequests(long bestBlock, long currentBestBlock) {
        ConnectionPointFinder connectionPointFinder = new ConnectionPointFinder(bestBlock);
        int i = 0;
        while (!connectionPointFinder.getConnectionPoint().isPresent()) {
            i++;
            if (connectionPointFinder.getFindingHeight() <= currentBestBlock)
                connectionPointFinder.updateFound();
            else
                connectionPointFinder.updateNotFound();
        }

        return i;
    }
}
