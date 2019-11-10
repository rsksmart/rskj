package org.ethereum.core;

import java.util.List;

public class BlockBody {

    private final List<Transaction> transactions;

    private final List<BlockHeader> uncles;

    public BlockBody(List<Transaction> transactions, List<BlockHeader> uncles) {
        this.transactions = transactions;
        this.uncles = uncles;
    }

    public List<Transaction> getTransactionsList() {
        return transactions;
    }

    public List<BlockHeader> getUncleList() {
        return uncles;
    }
}
