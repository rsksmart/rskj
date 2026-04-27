package org.ethereum.db;

import co.rsk.core.BlockDifficulty;

/**
 * Created by usuario on 07/06/2017.
 */
public class BlockInformation {
    private byte[] hash;
    private BlockDifficulty totalDifficulty;
    private boolean inMainChain;

    public BlockInformation(byte[] hash, BlockDifficulty totalDifficulty, boolean inMainChain) {
        this.hash = hash;
        this.totalDifficulty = totalDifficulty;
        this.inMainChain = inMainChain;
    }

    public byte[] getHash() {
        return this.hash;
    }

    public BlockDifficulty getTotalDifficulty() {
        return this.totalDifficulty;
    }

    public boolean isInMainChain() {
        return this.inMainChain;
    }
}
