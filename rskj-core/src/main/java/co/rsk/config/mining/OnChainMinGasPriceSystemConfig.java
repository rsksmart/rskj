package co.rsk.config.mining;

import com.typesafe.config.Config;

public class OnChainMinGasPriceSystemConfig {
    public static final String ONCHAIN_STABLE_GAS_PRICE_CONFIG_PATH = "onChain";
    //TODO property example
    private static final String ADDRESS_PROPERTY = "address";
    private final String address;

    public OnChainMinGasPriceSystemConfig(Config config) {
        this.address = config.getString(ADDRESS_PROPERTY);
    }

    public String getAddress() {
        return address;
    }
}
