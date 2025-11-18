/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
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
package co.rsk.rpc.modules.eth;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.OverrideablePrecompiledContracts;

import java.math.BigInteger;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.ethereum.rpc.exception.RskJsonRpcRequestException.invalidParamError;

public class DefaultStateOverrideApplier implements StateOverrideApplier {

    private final ActivationConfig activationConfig;

    public DefaultStateOverrideApplier(ActivationConfig activationConfig) {
        this.activationConfig = activationConfig;
    }

    @Override
    public void validateOperationOverPrecompiledContract(AccountOverride accountOverride, List<RskAddress> dirtyAddresses) {
        // If there's state changes, then is an invalid operation
        if (containsStateChange(accountOverride)) {
            throw invalidParamError("Precompiled contracts can not be overridden");
        }

        // If the destination is dirty, then is an invalid operation
        if (dirtyAddresses.stream().anyMatch(address -> address.equals(accountOverride.getMovePrecompileToAddress()))) {
            throw invalidParamError("Precompiled contracts can not be moved to an overridden address");
        }
    }

    @Override
    public void applyToRepository(Block block, Repository repository, AccountOverride accountOverride, OverrideablePrecompiledContracts overrideablePrecompiledContracts) {

        if (accountOverride.getBalance() != null) {
            Coin storedValue = Optional.ofNullable(repository.getBalance(accountOverride.getAddress())).orElse(Coin.ZERO);
            repository.addBalance(accountOverride.getAddress(), new Coin(accountOverride.getBalance()).subtract(storedValue));
        }

        if (accountOverride.getNonce() != null) {
            repository.setNonce(accountOverride.getAddress(), BigInteger.valueOf(accountOverride.getNonce()));
        }

        if (accountOverride.getCode() != null) {
            repository.saveCode(accountOverride.getAddress(), accountOverride.getCode());
        }

        if (accountOverride.getStateDiff() != null && accountOverride.getState() != null) {
            throw new IllegalStateException("AccountOverride.stateDiff and AccountOverride.state cannot be set at the same time");
        }

        if (accountOverride.getState() != null) {
            Iterator<DataWord> keys = repository.getStorageKeys(accountOverride.getAddress());
            while (keys.hasNext()) {
                repository.addStorageRow(accountOverride.getAddress(), keys.next(), DataWord.ZERO);
            }
            for (Map.Entry<DataWord, DataWord> entry : accountOverride.getState().entrySet()) {
                repository.addStorageRow(accountOverride.getAddress(), entry.getKey(), entry.getValue());
            }
        }

        if (accountOverride.getStateDiff() != null) {
            for (Map.Entry<DataWord, DataWord> entry : accountOverride.getStateDiff().entrySet()) {
                repository.addStorageRow(accountOverride.getAddress(), entry.getKey(), entry.getValue());
            }
        }

        if (overrideablePrecompiledContracts != null && accountOverride.getMovePrecompileToAddress() != null) {
            if (!overrideablePrecompiledContracts.isMovableContract(accountOverride.getAddress())) {
                throw new IllegalStateException(String.format("Account %s can not be moved", accountOverride.getAddress().toHexString()));
            }

            if (overrideablePrecompiledContracts.isOverridden(accountOverride.getAddress())) {
                throw new IllegalStateException(String.format("Account %s has already been overridden by a precompile", accountOverride.getAddress().toHexString()));
            }

            ActivationConfig.ForBlock blockActivations = activationConfig.forBlock(block.getNumber());
            overrideablePrecompiledContracts.addOverride(accountOverride.getAddress(), accountOverride.getMovePrecompileToAddress(), blockActivations);
        }

    }

    private boolean containsStateChange(AccountOverride accountOverride) {
        boolean containsStateChange = accountOverride.getBalance() != null || accountOverride.getNonce() != null || accountOverride.getCode() != null;
        boolean containsStateDiff = accountOverride.getState() != null || accountOverride.getStateDiff() != null;
        return containsStateChange || containsStateDiff;
    }

}
