package org.ethereum.config.blockchain.mainnet;

public class MainNetFirstForkConfig extends MainNetAfterBridgeSyncConfig {
    @Override
    public boolean isRfs55() {
        return true;
    }

    @Override
    public boolean isRfs90() {
        return true;
    }
}
