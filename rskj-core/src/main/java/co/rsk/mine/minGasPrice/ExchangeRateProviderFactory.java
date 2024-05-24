package co.rsk.mine.minGasPrice;

import co.rsk.config.StableMinGasPriceSourceConfig;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ExchangeRateProviderFactory {
    private static final Logger logger = LoggerFactory.getLogger(ExchangeRateProviderFactory.class);

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

    public static @Nonnull List<ExchangeRateProvider> getProvidersFromSourceConfig(ConfigList minerStableGasPriceSources) {
        return minerStableGasPriceSources
                .stream()
                .map(source -> createProvider((ConfigObject) source))
                .collect(Collectors.toList());
    }

    public static ExchangeRateProvider createProvider(ConfigObject sourceConfigObject) {
        StableMinGasPriceSourceConfig sourceConfig = new StableMinGasPriceSourceConfig(
                sourceConfigObject
        );

        String type = sourceConfig.sourceType();
        if (type == null) {
            throw new IllegalArgumentException("Missing 'type' for a source in miner stableGasPrice");
        }
        Class<? extends ExchangeRateProvider> providerClass = XRSourceType.get(type);
        if (providerClass == null) {
            logger.error("Unknown 'type' in miner stableGasPrice providers: {}", type);

            return null;
        }

        return createProvider(providerClass, sourceConfig);
    }

    private static ExchangeRateProvider createProvider(Class<? extends ExchangeRateProvider> providerClass, StableMinGasPriceSourceConfig sourceConfig) {
        try {
            return providerClass
                    .getConstructor(StableMinGasPriceSourceConfig.class)
                    .newInstance(sourceConfig);
        } catch (Exception e) {
            logger.error("Error creating provider", e);

            return null;
        }
    }
}
