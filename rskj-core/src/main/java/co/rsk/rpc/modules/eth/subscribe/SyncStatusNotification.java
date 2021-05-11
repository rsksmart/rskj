package co.rsk.rpc.modules.eth.subscribe;

public class SyncStatusNotification {
    private final long startingBlock;
    private final long currentBlock;
    private final long highestBlock;

    public SyncStatusNotification(long startingBlock, long currentBlock, long highestBlock) {
        this.startingBlock = startingBlock;
        this.currentBlock = currentBlock;
        this.highestBlock = highestBlock;
    }

    public long getStartingBlock() {
        return startingBlock;
    }

    public long getCurrentBlock() {
        return currentBlock;
    }

    public long getHighestBlock() {
        return highestBlock;
    }
}
