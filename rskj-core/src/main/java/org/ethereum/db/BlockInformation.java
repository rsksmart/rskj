package org.ethereum.db;

import co.rsk.crypto.Keccak256;

import java.math.BigInteger;

/**
 * Created by usuario on 07/06/2017.
 */
public class BlockInformation {
    private Keccak256 hash;
    private BigInteger totalDifficulty;
    private boolean inMainChain;

    public BlockInformation(Keccak256 hash, BigInteger totalDifficulty, boolean inMainChain) {
        this.hash = hash;
        this.totalDifficulty = totalDifficulty;
        this.inMainChain = inMainChain;
    }

    public Keccak256 getHash() {
        return this.hash;
    }

    public BigInteger getTotalDifficulty() {
        return this.totalDifficulty;
    }

    public boolean isInMainChain() {
        return this.inMainChain;
    }
}
