/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.mine.gas.provider;

import co.rsk.config.mining.StableMinGasPriceSystemConfig;
import co.rsk.rpc.modules.eth.EthModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;


public class MinGasPriceProviderFactory {

    private static final Logger logger = LoggerFactory.getLogger("StableMinGasPrice");

    private MinGasPriceProviderFactory() {
    }

    public static MinGasPriceProvider create(long fixedMinGasPrice, StableMinGasPriceSystemConfig stableMinGasPriceSystemConfig, Supplier<EthModule> ethModuleSupplier) {
        FixedMinGasPriceProvider fixedMinGasPriceProvider = new FixedMinGasPriceProvider(fixedMinGasPrice);

        if (stableMinGasPriceSystemConfig == null) {
            logger.warn("Could not find stable min gas price system config, using {} provider", fixedMinGasPriceProvider.getType().name());
            return fixedMinGasPriceProvider;
        }
        if (!stableMinGasPriceSystemConfig.isEnabled()) {
            return fixedMinGasPriceProvider;
        }

        MinGasPriceProviderType method = stableMinGasPriceSystemConfig.getMethod();
        if (method == null) {
            logger.error("Could not find valid method in config, using fallback provider: {}", fixedMinGasPriceProvider.getType().name());
            return fixedMinGasPriceProvider;
        }

        switch (method) {
            case HTTP_GET:
                return new HttpGetMinGasPriceProvider(stableMinGasPriceSystemConfig, fixedMinGasPriceProvider);
            case ETH_CALL:
                return new EthCallMinGasPriceProvider(fixedMinGasPriceProvider, stableMinGasPriceSystemConfig, ethModuleSupplier);
            case FIXED:
                return fixedMinGasPriceProvider;
            default:
                logger.debug("Could not find a valid implementation for the method {}. Returning fallback provider {}", method, fixedMinGasPriceProvider.getType().name());
                return fixedMinGasPriceProvider;
        }
    }
}
