package org.ethereum.config.blockchain.mainnet;

import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.mainnet.MainNetAfterBridgeSyncConfig;

public class MainNetBeforeBridgeSyncConfig extends MainNetAfterBridgeSyncConfig {

    public MainNetBeforeBridgeSyncConfig() {
        super(new MainNetAfterBridgeSyncConfig.MainNetConstants());
    }

    protected MainNetBeforeBridgeSyncConfig(Constants constants) {
        super(constants);
    }

    @Override
    public boolean areBridgeTxsFree() {
        return true;
    }
}
