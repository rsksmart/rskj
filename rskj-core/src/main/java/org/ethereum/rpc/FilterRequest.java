package org.ethereum.rpc;

import java.util.Arrays;

public class FilterRequest {

    private String fromBlock;
    private String toBlock;
    private Object address;
    private Object[] topics;
    private String blockHash;

    @Override
    public String toString() {
        return "FilterRequest{" +
                "fromBlock='" + fromBlock + '\'' +
                ", toBlock='" + toBlock + '\'' +
                ", address=" + address +
                ", topics=" + Arrays.toString(topics) +
                ", blockHash='" + blockHash + '\'' +
                '}';
    }

    public String getFromBlock() {
        return fromBlock;
    }

    public void setFromBlock(String fromBlock) {
        this.fromBlock = fromBlock;
    }

    public String getToBlock() {
        return toBlock;
    }

    public void setToBlock(String toBlock) {
        this.toBlock = toBlock;
    }

    public Object getAddress() {
        return address;
    }

    public void setAddress(Object address) {
        this.address = address;
    }

    public Object[] getTopics() {
        return topics;
    }

    public void setTopics(Object[] topics) {
        this.topics = topics;
    }

    public String getBlockHash() {
        return blockHash;
    }

    public void setBlockHash(String blockHash) {
        this.blockHash = blockHash;
    }
}
