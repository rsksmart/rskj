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
import org.ethereum.core.Repository;
import org.ethereum.vm.DataWord;

import java.math.BigInteger;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

import static org.ethereum.rpc.exception.RskJsonRpcRequestException.invalidParamError;
import static org.ethereum.rpc.exception.RskJsonRpcRequestException.unimplemented;

public class DefaultStateOverrideApplier implements StateOverrideApplier {

    private final int maxOverridableCodeSize;
    private final int maxStateOverrideChanges;

    public DefaultStateOverrideApplier(int maxOverridableCodeSize, int maxStateOverrideChanges) {
        this.maxOverridableCodeSize = maxOverridableCodeSize;
        this.maxStateOverrideChanges = maxStateOverrideChanges;
    }

    @Override
    public void applyToRepository(Repository repository, AccountOverride accountOverride) {

        RskAddress address = accountOverride.getAddress();

        if (address == null) {
            throw invalidParamError("Address cannot be null");
        }

        if (accountOverride.getStateDiff() != null && accountOverride.getState() != null) {
            throw invalidParamError("AccountOverride.stateDiff and AccountOverride.state cannot be set at the same time");
        }

        applyBalanceOverride(repository, address, accountOverride.getBalance());
        applyNonceOverride(repository, address, accountOverride.getNonce());
        applyCodeOverride(repository, address, accountOverride.getCode());
        applyStateOverride(repository, address, accountOverride.getState());
        applyStateDiff(repository, address, accountOverride.getStateDiff());
        applyMovePrecompileToAddress(repository, address, accountOverride.getMovePrecompileToAddress());

    }

    private void applyBalanceOverride(Repository repository, RskAddress address, BigInteger newBalance) {
        if (newBalance != null) {

            if (newBalance.compareTo(BigInteger.ZERO) < 0) {
                throw invalidParamError("Balance must be equal or bigger than zero");
            }

            Coin storedValue = Optional.ofNullable(repository.getBalance(address)).orElse(Coin.ZERO);
            repository.addBalance(address, new Coin(newBalance).subtract(storedValue));
        }
    }

    private void applyNonceOverride(Repository repository, RskAddress address, Long newNonce) {
        if (newNonce != null) {
            if (newNonce < 0) {
                throw invalidParamError("Nonce must be equal or bigger than zero");
            }
            repository.setNonce(address, BigInteger.valueOf(newNonce));
        }
    }

    private void applyCodeOverride(Repository repository, RskAddress address, byte[] newCode) {
        if (newCode != null) {
            if (newCode.length > maxOverridableCodeSize) {
                throw invalidParamError("Code length in bytes exceeded. Max " + maxOverridableCodeSize);
            }
            repository.saveCode(address, newCode);
        }
    }

    private void applyStateOverride(Repository repository, RskAddress address, Map<DataWord, DataWord> newState) {
        if (newState != null) {
            if (newState.size() > maxStateOverrideChanges) {
                throw invalidParamError("Number of state changes exceeded. Max " + maxStateOverrideChanges);
            }

            Iterator<DataWord> keys = repository.getStorageKeys(address);
            while (keys.hasNext()) {
                repository.addStorageRow(address, keys.next(), DataWord.ZERO);
            }
            for (Map.Entry<DataWord, DataWord> entry : newState.entrySet()) {
                repository.addStorageRow(address, entry.getKey(), entry.getValue());
            }
        }
    }

    private void applyStateDiff(Repository repository, RskAddress address, Map<DataWord, DataWord> stateDiff) {
        if (stateDiff != null) {
            for (Map.Entry<DataWord, DataWord> entry : stateDiff.entrySet()) {
                repository.addStorageRow(address, entry.getKey(), entry.getValue());
            }
        }
    }

    private void applyMovePrecompileToAddress(Repository repository, RskAddress address, RskAddress destinationAddress) {
        if (destinationAddress != null) {
            throw unimplemented("Move precompile to address is not supported yet");
        }
    }

}
