package org.ethereum.rpc;

public class BlockInformationResult {
    private String hash;
    private String totalDifficulty;
    private boolean inMainChain;

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getTotalDifficulty() {
        return totalDifficulty;
    }

    public void setTotalDifficulty(String totalDifficulty) {
        this.totalDifficulty = totalDifficulty;
    }

    public boolean isInMainChain() {
        return inMainChain;
    }

    public void setInMainChain(boolean inMainChain) {
        this.inMainChain = inMainChain;
    }
}
