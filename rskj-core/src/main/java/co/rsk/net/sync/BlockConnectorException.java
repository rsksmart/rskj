package co.rsk.net.sync;

public class BlockConnectorException extends RuntimeException{
    private final long blockNumber;
    private final long childBlockNumber;
    public BlockConnectorException(final long blockNumber, final long childBlockNumber) {
        super(String.format("Block is not parent of child. Block number: %s Child number: %s", blockNumber, childBlockNumber));
        this.blockNumber = blockNumber;
        this.childBlockNumber = childBlockNumber;
    }

    public long getBlockNumber() {
        return blockNumber;
    }

    public long getChildBlockNumber() {
        return childBlockNumber;
    }
}
