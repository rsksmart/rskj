package co.rsk.mine.gas.provider.example;

import com.typesafe.config.Config;

public class ExampleProviderConfig {
    public static final String EXAMPLE_PROVIDER_CONFIG_PATH = "example";
    private static final String FIXED_PRICE_PROPERTY = "fixedPrice";
    private final long fixedPrice;

    public ExampleProviderConfig(Config config) {
        fixedPrice = config.getLong(FIXED_PRICE_PROPERTY);
        //TODO: validate fixedPrice or more complex logic
    }
    public long getFixedPrice() {
        return fixedPrice;
    }
}
