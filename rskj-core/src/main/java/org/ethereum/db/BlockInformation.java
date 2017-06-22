package org.ethereum.db;

import java.math.BigInteger;

/**
 * Created by usuario on 07/06/2017.
 */
public class BlockInformation {
    private byte[] hash;
    private BigInteger totalDifficulty;
    private boolean inMainChain;

    public BlockInformation(byte[] hash, BigInteger totalDifficulty, boolean inMainChain) {
        this.hash = hash;
        this.totalDifficulty = totalDifficulty;
        this.inMainChain = inMainChain;
    }

    public byte[] getHash() {
        return this.hash;
    }

    public BigInteger getTotalDifficulty() {
        return this.totalDifficulty;
    }

    public boolean isInMainChain() {
        return this.inMainChain;
    }
}
