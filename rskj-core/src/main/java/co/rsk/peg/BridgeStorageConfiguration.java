package co.rsk.peg;

import org.ethereum.config.BlockchainConfig;

public class BridgeStorageConfiguration {
    private final Boolean isUnlimitedWhitelistEnabled;

    public BridgeStorageConfiguration(Boolean isUnlimitedWhitelistEnabled) {
        this.isUnlimitedWhitelistEnabled = isUnlimitedWhitelistEnabled;
    }

    public Boolean isUnlimitedWhitelistEnabled() {
        return this.isUnlimitedWhitelistEnabled;
    }

    public static BridgeStorageConfiguration fromBlockchainConfig(BlockchainConfig config) {
        return new BridgeStorageConfiguration(config.isRfs170());
    }
}
