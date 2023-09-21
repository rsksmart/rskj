/*
 * This file is part of RskJ
 * Copyright (C) 2023 RSK Labs Ltd.
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
package org.ethereum.rpc.parameters;

import co.rsk.crypto.Keccak256;
import co.rsk.util.HexUtils;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;

import static co.rsk.util.HexUtils.stringHexToByteArray;

public abstract class HashParam32 {
    private static final int HASH_BYTE_LENGTH = 32;
    private final Keccak256 hash;

    HashParam32(String hashType, String hash) {
        if (hash == null || hash.isEmpty()) {
            throw RskJsonRpcRequestException.invalidParamError("Invalid " + hashType + ": empty or null.");
        }

        byte[] hashBytes;

        try {
            hashBytes = HexUtils.stringHexToByteArray(hash);
        } catch (Exception e) {
            throw RskJsonRpcRequestException.invalidParamError("Invalid " + hashType + " format: invalid hex value");
        }

        if (HASH_BYTE_LENGTH != hashBytes.length) {
            throw RskJsonRpcRequestException.invalidParamError("Invalid " + hashType + ": incorrect length.");
        }

        this.hash = new Keccak256(stringHexToByteArray(hash));
    }

    public Keccak256 getHash() {
        return hash;
    }
}
