package org.ethereum.config.blockchain.mainnet;

// TODO: find a proper name for the "FirstFork"
public class MainNetFirstForkConfig extends MainNetAfterBridgeSyncConfig {
    @Override
    public boolean isRfs55() {
        return true;
    }

    @Override
    public boolean isRfs90() {
        return true;
    }

    @Override
    public boolean isRfs94() {
        return true;
    }
}
