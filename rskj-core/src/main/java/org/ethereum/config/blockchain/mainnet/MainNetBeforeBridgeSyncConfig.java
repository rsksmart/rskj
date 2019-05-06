package org.ethereum.config.blockchain.mainnet;

public class MainNetBeforeBridgeSyncConfig extends MainNetAfterBridgeSyncConfig {
    @Override
    public boolean areBridgeTxsFree() {
        return true;
    }
}
