package org.ethereum.db;

import java.math.BigInteger;

/**
 * Created by usuario on 07/06/2017.
 */
public class BlockInformation {
    private byte[] hash;
    private BigInteger totalDifficulty;
    private boolean inBlockChain;

    public BlockInformation(byte[] hash, BigInteger totalDifficulty, boolean inBlockChain) {
        this.hash = hash;
        this.totalDifficulty = totalDifficulty;
        this.inBlockChain = inBlockChain;
    }

    public byte[] getHash() {
        return this.hash;
    }

    public BigInteger getTotalDifficulty() {
        return this.totalDifficulty;
    }

    public boolean isInBlockChain() {
        return this.inBlockChain;
    }
}
