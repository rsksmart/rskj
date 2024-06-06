package co.rsk.config.mining;

import com.typesafe.config.Config;

public class OnChainMinGasPriceSystemConfig {
    public static final String ONCHAIN_STABLE_GAS_PRICE_CONFIG_PATH = "onChain";
    //TODO property example
    private static final String ADDRESS_PROPERTY = "address";
    private static final String FROM_PROPERTY = "from";
    private static final String DATA_PROPERTY = "data";
    private final String address;
    private final String from;
    private final String data;

    public OnChainMinGasPriceSystemConfig(Config config) {
        address = config.getString(ADDRESS_PROPERTY);
        from = config.getString(FROM_PROPERTY);
        data = config.getString(DATA_PROPERTY);
    }

    public String address() {
        return address;
    }

    public String from() {
        return from;
    }

    public String data() {
        return data;
    }
}
