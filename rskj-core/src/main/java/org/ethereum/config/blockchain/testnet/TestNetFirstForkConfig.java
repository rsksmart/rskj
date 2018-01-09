package org.ethereum.config.blockchain.testnet;

public class TestNetFirstForkConfig extends TestNetAfterBridgeSyncConfig {
    @Override
    public boolean isRfs55() {
        return true;
    }

    @Override
    public boolean isRfs90() {
        return true;
    }
}
