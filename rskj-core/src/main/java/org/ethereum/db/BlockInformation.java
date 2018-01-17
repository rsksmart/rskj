package org.ethereum.db;

import co.rsk.crypto.Sha3Hash;

import java.math.BigInteger;

/**
 * Created by usuario on 07/06/2017.
 */
public class BlockInformation {
    private Sha3Hash hash;
    private BigInteger totalDifficulty;
    private boolean inMainChain;

    public BlockInformation(Sha3Hash hash, BigInteger totalDifficulty, boolean inMainChain) {
        this.hash = hash;
        this.totalDifficulty = totalDifficulty;
        this.inMainChain = inMainChain;
    }

    public Sha3Hash getHash() {
        return this.hash;
    }

    public BigInteger getTotalDifficulty() {
        return this.totalDifficulty;
    }

    public boolean isInMainChain() {
        return this.inMainChain;
    }
}
