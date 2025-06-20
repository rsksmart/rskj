/*
 * This file is part of RskJ
 * Copyright (C) 2021 RSK Labs Ltd.
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
package co.rsk.pcc.secp256k1;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

import java.util.Optional;

import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.crypto.signature.Secp256k1Service;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.exception.VMException;

public abstract class Secp256k1PrecompiledContract extends PrecompiledContracts.PrecompiledContract {
    private final ActivationConfig.ForBlock activations;
    protected final Secp256k1Service secp256k1Service;

    public Secp256k1PrecompiledContract(ActivationConfig.ForBlock activations, Secp256k1Service secp256k1Service) {
        this.activations = activations;
        this.secp256k1Service = secp256k1Service;
    }

    @Override
    public byte[] execute(byte[] data) throws VMException {
        if (activations.isActive(ConsensusRule.RSKIP516)) {
            final var validatedData = Optional.ofNullable(data).orElse(EMPTY_BYTE_ARRAY);
            return executeOperation(validatedData);
        } else {
            throw new VMException("RSKIP516 is not active");
        }
    }

    protected abstract byte[] executeOperation(byte[] data);
}
