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

package org.ethereum.config.blockchain;

import co.rsk.peg.Federation;
import org.ethereum.config.Constants;

/**
 * Created by Anton Nashatyrev on 25.02.2016.
 */
public class GenesisConfig extends AbstractConfig {

    // IMPORTANT NOTICE
    // This class contains two methods "NEW_*" containing the latest hard-forks in Ethereum
    // Before launch, they must be removed or exchanged with the current non-prefixed methods.

    public GenesisConfig() {
        this(Constants.regtest());
    }

    public GenesisConfig(Constants constants) {
        super(constants);
    }

    @Override
    public Federation getGenesisFederation() {
        return getConstants().getBridgeConstants().getGenesisFederation();
    }
}
