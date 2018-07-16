package org.ethereum.config.blockchain.mainnet;

public class MainNetOrchidConfig extends MainNetAfterBridgeSyncConfig {
    @Override
    public boolean isRskip90() {
        return true;
    }

    @Override
    public boolean isRskip89() {
        return true;
    }

    @Override
    public boolean isRskip88() {
        return true;
    }

    @Override
    public boolean isRcs230() {
        return true;
    }

    @Override
    public boolean isRskip87() { return true; }
}
