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

import org.ethereum.crypto.signature.Secp256k1Service;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.exception.VMException;

import java.util.Optional;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

public abstract class Secp256k1PrecompiledContract extends PrecompiledContracts.PrecompiledContract {
    protected final Secp256k1Service secp256k1Service;

    protected Secp256k1PrecompiledContract(Secp256k1Service secp256k1Service) {
        this.secp256k1Service = secp256k1Service;
    }

    @Override
    public byte[] execute(byte[] data) throws VMException {
        final var validatedData = Optional.ofNullable(data).orElse(EMPTY_BYTE_ARRAY);

        return executeOperation(validatedData);
    }

    protected abstract byte[] executeOperation(byte[] data) throws VMException;
}
