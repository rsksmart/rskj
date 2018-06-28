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

import org.ethereum.config.blockchain.devnet.DevNetFirstForkConfig;
import org.ethereum.config.blockchain.devnet.DevNetGenesisConfig;


/**
 * Created by Anton Nashatyrev on 25.02.2016.
 */
public class DevNetConfig extends AbstractNetConfig {
    public DevNetConfig() {
    }

    /**
     * By default DevNetConfig should activate every fork at height 0
     * @return a config with all the available forks activated
     */
    public static DevNetConfig getDefaultDevNetConfig() {
        DevNetConfig config = new DevNetConfig();

        config.add(0, new DevNetFirstForkConfig());
        return config;
    }
}
