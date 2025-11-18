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

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class OverrideablePrecompiledContracts extends PrecompiledContracts {
    private final Map<RskAddress, PrecompiledContract> overriddenPrecompileContracts = new HashMap<>();
    private final Set<RskAddress> overriddenContracts = new HashSet<>();

    public OverrideablePrecompiledContracts(PrecompiledContracts basePrecompiledContracts) {
        super(basePrecompiledContracts.getConfig(), basePrecompiledContracts.getBridgeSupportFactory(), basePrecompiledContracts.getSignatureCache());
    }

    @Override
    public PrecompiledContract getContractForAddress(ActivationConfig.ForBlock activations, DataWord address) {
        final var rskAddress = new RskAddress(address);

        return overriddenPrecompileContracts.getOrDefault(rskAddress, super.getContractForAddress(activations, address));
    }

    public void addOverride(RskAddress originalAddress, RskAddress targetAddress, ActivationConfig.ForBlock blockActivations) {
        final var precompiledContract = super.getContractForAddress(blockActivations, DataWord.valueFromHex(originalAddress.toHexString()));

        if (precompiledContract == null) {
            throw new IllegalStateException(String.format("Account %s is not a precompiled contract", originalAddress.toHexString()));
        }

        if (overriddenPrecompileContracts.containsKey(targetAddress)) {
            throw new IllegalStateException(String.format("Account %s is already overridden", targetAddress.toHexString()));
        }

        overriddenPrecompileContracts.put(targetAddress, precompiledContract);
        overriddenContracts.add(originalAddress);
    }

    public boolean isOverridden(RskAddress address) {
        return overriddenContracts.stream().anyMatch(a -> a.equals(address));
    }

    public boolean isMovableContract(RskAddress contractAddress) {
        byte[] addressBytes = contractAddress.getBytes();
        return !Arrays.equals(BRIDGE_ADDR.getBytes(), addressBytes) && !Arrays.equals(REMASC_ADDR.getBytes(), addressBytes);
    }

}
