package co.rsk.mine.minGasPrice;

import co.rsk.config.StableMinGasPriceSourceConfig;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.stream.Collectors;

public class ExchangeRateProviderFactory {
    private static final Logger logger = LoggerFactory.getLogger(ExchangeRateProviderFactory.class);

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
            throw new IllegalArgumentException("A stableGasPrice miner source requires specifying the 'type' attribute in configuration");
        }
        Class<? extends ExchangeRateProvider> providerClass = ExchangeRateProvider.XRSourceType.get(type);
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
