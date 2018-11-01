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
import org.ethereum.config.blockchain.devnet.DevNetGenesisConfig;
import org.ethereum.config.blockchain.devnet.DevNetOrchid060Config;
import org.ethereum.config.blockchain.devnet.DevNetOrchidConfig;

import java.util.List;


/**
 * Created by Anton Nashatyrev on 25.02.2016.
 */
public class DevNetConfig extends AbstractNetConfig {

    /**
     * By default DevNetConfig should activate every fork at height 0
     * @return a config with all the available forks activated
     */
    public static DevNetConfig getDefaultDevNetConfig() {
        DevNetConfig config = new DevNetConfig();
        config.add(0, new DevNetOrchidConfig());
        return config;
    }

    public static DevNetConfig getFromConfig(HardForkActivationConfig hardForkActivationConfig, List<BtcECKey> genesisFederatorsPublicKeys) {
        DevNetConfig customConfig = getHardForkConfig(hardForkActivationConfig);
        customConfig.setGenesisFederationPublicKeys(genesisFederatorsPublicKeys);
        return customConfig;
    }

    public static DevNetConfig getHardForkConfig(HardForkActivationConfig hardForkActivationConfig) {
        if (hardForkActivationConfig == null) {
            return getDefaultDevNetConfig();
        }
        DevNetConfig customConfig = new DevNetConfig();
        if (hardForkActivationConfig.getOrchid060ActivationHeight() != 0) {
            if (hardForkActivationConfig.getOrchidActivationHeight() != 0) {
                customConfig.add(0, new DevNetGenesisConfig());
            }

            customConfig.add(hardForkActivationConfig.getOrchidActivationHeight(), new DevNetOrchidConfig());
        }

        customConfig.add(hardForkActivationConfig.getOrchid060ActivationHeight(), new DevNetOrchid060Config());
        return customConfig;
    }
}
