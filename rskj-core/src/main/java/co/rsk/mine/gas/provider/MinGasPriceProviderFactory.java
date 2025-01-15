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
            logger.warn("Could not find stable min gas price config. Falling back to {} with fixedMinGasPrice: {}", fixedMinGasPriceProvider.getType(), fixedMinGasPrice);
            return fixedMinGasPriceProvider;
        }
        if (!stableMinGasPriceSystemConfig.isEnabled()) {
            logger.info("Stable min gas price is disabled. Falling back to {} with fixedMinGasPrice: {}", fixedMinGasPriceProvider.getType(), fixedMinGasPrice);
            return fixedMinGasPriceProvider;
        }

        MinGasPriceProviderType method = stableMinGasPriceSystemConfig.getMethod();
        if (method == null) {
            logger.warn("Could not find valid method in config. Falling back to {} with fixedMinGasPrice: {}", fixedMinGasPriceProvider.getType(), fixedMinGasPrice);
            return fixedMinGasPriceProvider;
        }

        switch (method) {
            case HTTP_GET:
                logger.info("Creating 'Http-Get' stable min gas price provider");
                return new HttpGetMinGasPriceProvider(stableMinGasPriceSystemConfig, fixedMinGasPriceProvider);
            case ETH_CALL:
                logger.info("Creating 'Eth-Call' stable min gas price provider");
                return new EthCallMinGasPriceProvider(fixedMinGasPriceProvider, stableMinGasPriceSystemConfig, ethModuleSupplier);
            case FIXED:
                logger.info("Creating 'Fixed' min gas price provider with fixedMinGasPrice: {}", fixedMinGasPrice);
                return fixedMinGasPriceProvider;
            default:
                logger.warn("Could not find a valid implementation for the method {}. Creating {} fallback provider with fixedMinGasPrice: {}", method, fixedMinGasPriceProvider.getType(), fixedMinGasPrice);
                return fixedMinGasPriceProvider;
        }
    }
}
