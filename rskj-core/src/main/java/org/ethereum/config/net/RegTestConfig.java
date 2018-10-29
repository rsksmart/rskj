/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package org.ethereum.config.net;

import co.rsk.bitcoinj.core.BtcECKey;
import org.ethereum.config.blockchain.HardForkActivationConfig;
import org.ethereum.config.blockchain.regtest.RegTestOrchidConfig;
import org.ethereum.config.blockchain.regtest.RegTestSecondForkConfig;

import java.util.List;

/**
 * Created by Anton Nashatyrev on 25.02.2016.
 */
public class RegTestConfig extends AbstractNetConfig {

    /**
     * By default RegTestConfig should activate every fork at height 0
     * @return a config with all the available forks activated
     */
    public static RegTestConfig getDefaultRegTestConfig() {
        RegTestConfig config = new RegTestConfig();
        config.add(0, new RegTestOrchidConfig());
        config.add(0, new RegTestSecondForkConfig());
        return config;
    }

    public static RegTestConfig getFromConfig(HardForkActivationConfig hardForkActivationConfig, List<BtcECKey> genesisFederatorsPublicKeys) {
        RegTestConfig customConfig = getHardForkConfig(hardForkActivationConfig);
        customConfig.setGenesisFederationPublicKeys(genesisFederatorsPublicKeys);
        return customConfig;
    }

    private static RegTestConfig getHardForkConfig(HardForkActivationConfig hardForkActivationConfig) {

        if (hardForkActivationConfig == null) {
            return getDefaultRegTestConfig();
        }
        RegTestConfig customConfig = new RegTestConfig();

        if (hardForkActivationConfig.getSecondForkActivationHeight() > 0) {
            if (hardForkActivationConfig.getOrchidActivationHeight() > 0) {
                // Only add genesis config if orchid config is set
                customConfig.add(0, new org.ethereum.config.blockchain.regtest.RegTestGenesisConfig());
            }

            // Only add orchid config if second fork config is set
            customConfig.add(hardForkActivationConfig.getOrchidActivationHeight(), new RegTestOrchidConfig());
        }

        customConfig.add(hardForkActivationConfig.getSecondForkActivationHeight(), new RegTestSecondForkConfig());
        return customConfig;
    }
}
