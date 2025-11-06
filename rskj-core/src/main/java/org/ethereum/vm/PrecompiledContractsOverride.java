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
package org.ethereum.vm;

import co.rsk.core.RskAddress;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;

import java.util.HashMap;
import java.util.Map;

public class PrecompiledContractsOverride extends PrecompiledContracts {
    private final Map<RskAddress, PrecompiledContract> precompileContractsOverrideMap = new HashMap<>();

    public PrecompiledContractsOverride(PrecompiledContracts precompiledContracts) {
        super(precompiledContracts.getConfig(), precompiledContracts.getBridgeSupportFactory(), precompiledContracts.getSignatureCache());
    }

    @Override
    public PrecompiledContract getContractForAddress(ActivationConfig.ForBlock activations, DataWord address) {
        final var rskAddress = new RskAddress(address);

        return precompileContractsOverrideMap.getOrDefault(rskAddress, super.getContractForAddress(activations, address));
    }

    public void addOverride(RskAddress movePrecompileTo, PrecompiledContract precompiledContract) {
        if (contains(movePrecompileTo)) {
            return;
        }

        if (precompiledContract != null) {
            precompileContractsOverrideMap.put(movePrecompileTo, precompiledContract);
        }
    }

    public boolean contains(RskAddress address) {
        return precompileContractsOverrideMap.containsKey(address);
    }
}