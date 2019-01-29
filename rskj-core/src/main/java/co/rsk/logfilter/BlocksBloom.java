package co.rsk.logfilter;

import org.ethereum.core.Bloom;

/**
 * Created by ajlopez on 29/01/2019.
 */
public class BlocksBloom {
    private final Bloom bloom = new Bloom();
    private long fromBlock = -1;
    private long toBlock = -1;

    public Bloom getBloom() { return this.bloom; }

    public long fromBlock() { return this.fromBlock; }

    public long toBlock() { return this.toBlock; }

    public long size() {
        if (this.fromBlock == -1) {
            return 0;
        }

        return this.toBlock - this.fromBlock + 1;
    }

    public void addBlockBloom(long blockNumber, Bloom blockBloom) {
        if (fromBlock == -1) {
            fromBlock = blockNumber;
            toBlock = blockNumber;
        }
        else if (blockNumber == toBlock + 1) {
            toBlock = blockNumber;
        }
        else {
            throw new UnsupportedOperationException("Block out of sequence");
        }

        this.bloom.or(blockBloom);
    }
}
