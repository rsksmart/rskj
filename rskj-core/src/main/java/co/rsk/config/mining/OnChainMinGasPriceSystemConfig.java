package co.rsk.config.mining;

import com.typesafe.config.Config;

public class OnChainMinGasPriceSystemConfig {
    public static final String ONCHAIN_STABLE_GAS_PRICE_CONFIG_PATH = "onChain";
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

    public String getAddress() {
        return address;
    }

    public String getFrom() {
        return from;
    }

    public String getData() {
        return data;
    }
}
