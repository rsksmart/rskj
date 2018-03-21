package co.rsk.db;

/**
 * Created by ajlopez on 23/04/2018.
 */
public class PruneConfiguration {
    private long noBlocksToCopy;
    private long noBlocksToAvoidForks;
    private long noBlocksToWait;

    public PruneConfiguration(long noBlocksToCopy, long noBlocksToAvoidForks, long noBlocksToWait) {
        this.noBlocksToCopy = noBlocksToCopy;
        this.noBlocksToAvoidForks = noBlocksToAvoidForks;
        this.noBlocksToWait = noBlocksToWait;
    }

    public long getNoBlocksToCopy() {
        return this.noBlocksToCopy;
    }

    public long getNoBlocksToAvoidForks() {
        return this.noBlocksToAvoidForks;
    }

    public long getNoBlocksToWait() {
        return this.noBlocksToWait;
    }
}
