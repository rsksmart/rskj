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
package co.rsk.config.mining;

import co.rsk.mine.gas.provider.MinGasPriceProviderType;
import com.typesafe.config.Config;

public class StableMinGasPriceSystemConfig {
    public static final String STABLE_GAS_PRICE_CONFIG_PATH_PROPERTY = "miner.stableGasPrice";
    private static final String ENABLED_PROPERTY = "enabled";
    private static final String REFRESH_RATE_PROPERTY = "refreshRate";
    private static final String MIN_STABLE_GAS_PRICE_PROPERTY = "minStableGasPrice";
    private static final String METHOD_PROPERTY = "method";

    private final Integer refreshRate;
    private final Long minStableGasPrice;
    private final boolean enabled;
    private final MinGasPriceProviderType method;
    private final Config config;

    public StableMinGasPriceSystemConfig(Config config) {
        enabled = config.getBoolean(ENABLED_PROPERTY);
        refreshRate = config.getInt(REFRESH_RATE_PROPERTY);
        minStableGasPrice = config.getLong(MIN_STABLE_GAS_PRICE_PROPERTY);
        method = config.getEnum(MinGasPriceProviderType.class, METHOD_PROPERTY);
        this.config = config;
    }

    public boolean isValid() {
        return true;
    }

    public int getRefreshRate() {
        return refreshRate;
    }

    public Long getMinStableGasPrice() {
        return minStableGasPrice;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public WebStableMinGasSystemConfig getWebConfig() {
        return new WebStableMinGasSystemConfig(config.getConfig(WebStableMinGasSystemConfig.WEB_STABLE_GAS_PRICE_CONFIG_PATH));
    }

    public OnChainMinGasPriceSystemConfig getOnChainConfig() {
        return new OnChainMinGasPriceSystemConfig(config.getConfig(OnChainMinGasPriceSystemConfig.ONCHAIN_STABLE_GAS_PRICE_CONFIG_PATH));
    }



    public MinGasPriceProviderType getMethod() {
        return method;
    }
}
