package org.ethereum.config.blockchain;

import org.ethereum.config.Constants;

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
