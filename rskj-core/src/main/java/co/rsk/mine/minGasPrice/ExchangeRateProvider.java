package co.rsk.mine.minGasPrice;

import java.util.Arrays;

public abstract class ExchangeRateProvider {
    public enum XRSourceType {
        HTTP_GET("HTTP_GET", HttpGetXRProvider.class),
        ETH_CALL("ETH_CALL", EthCallXRProvider.class);

        private final String type;
        private final Class<? extends ExchangeRateProvider> providerClass;

        XRSourceType(String type, Class<? extends ExchangeRateProvider> providerClass) {
            this.type = type;
            this.providerClass = providerClass;
        }

        public static Class<? extends ExchangeRateProvider> get(String sourceType) {
            return Arrays.stream(XRSourceType.values())
                    .filter(type -> type.type.equalsIgnoreCase(sourceType))
                    .findFirst()
                    .map(type -> type.providerClass)
                    .orElse(null);
        }
    }

    private final XRSourceType type;


    public ExchangeRateProvider(XRSourceType type) {
        this.type = type;
    }

    public XRSourceType getType() {
        return type;
    }

    public abstract long getPrice(MinGasPriceProvider.ProviderContext context);

}
