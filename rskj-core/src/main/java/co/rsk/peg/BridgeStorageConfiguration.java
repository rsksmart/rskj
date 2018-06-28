package co.rsk.peg;

import org.ethereum.config.BlockchainConfig;

public class BridgeStorageConfiguration {
    private Boolean isUnlimitedWhitelistEnabled;

    public BridgeStorageConfiguration() {
        this.isUnlimitedWhitelistEnabled = Boolean.FALSE;
    }

    public void setIsUnlimitedWhitelistEnabled(Boolean value) {
        this.isUnlimitedWhitelistEnabled = value;
    }

    public Boolean getUnlimitedWhitelistEnabled() {
        return isUnlimitedWhitelistEnabled;
    }

    public static BridgeStorageConfiguration fromBlockchainConfig(BlockchainConfig config) {
        BridgeStorageConfiguration bridgeStorageConfiguration = new BridgeStorageConfiguration();
        bridgeStorageConfiguration.setIsUnlimitedWhitelistEnabled(config.isRfs170());
        return bridgeStorageConfiguration;
    }
}
