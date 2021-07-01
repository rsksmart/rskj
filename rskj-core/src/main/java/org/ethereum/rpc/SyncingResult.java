package org.ethereum.rpc;

public class SyncingResult {
    private String startingBlock;
    private String currentBlock;
    private String highestBlock;

    public String getStartingBlock() {
        return startingBlock;
    }

    public void setStartingBlock(String startingBlock) {
        this.startingBlock = startingBlock;
    }

    public String getCurrentBlock() {
        return currentBlock;
    }

    public void setCurrentBlock(String currentBlock) {
        this.currentBlock = currentBlock;
    }

    public String getHighestBlock() {
        return highestBlock;
    }

    public void setHighestBlock(String highestBlock) {
        this.highestBlock = highestBlock;
    }
}
