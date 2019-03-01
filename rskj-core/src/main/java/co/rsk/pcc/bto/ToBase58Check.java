/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package co.rsk.pcc.bto;

import co.rsk.pcc.ExecutionEnvironment;
import co.rsk.pcc.NativeContractIllegalArgumentException;
import co.rsk.pcc.NativeMethod;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.CallTransaction;

import java.math.BigInteger;

/**
 * This implements the "toBase58Check" method
 * that belongs to the BTOUtils native contract.
 *
 * @author Ariel Mendelzon
 */
public class ToBase58Check extends NativeMethod {
    /**
     * This is here since the bitcoinj Address class performs version
     * validation depending on the actual network passed in,
     * and the given constructor within VersionedChecksummedBytes is protected.
     */
    private static class VersionedChecksummedBytes extends co.rsk.bitcoinj.core.VersionedChecksummedBytes {
        public VersionedChecksummedBytes(int version, byte[] bytes) {
            super(version, bytes);
        }
    }

    private final CallTransaction.Function function = CallTransaction.Function.fromSignature(
            "toBase58Check",
            new String[]{"bytes", "uint8"},
            new String[]{"string"}
    );

    public ToBase58Check(ExecutionEnvironment executionEnvironment) {
        super(executionEnvironment);
    }

    @Override
    public CallTransaction.Function getFunction() {
        return function;
    }

    @Override
    public Object execute(Object[] arguments) {
        byte[] hash = (byte[]) arguments[0];
        if (hash.length != 20) {
            throw new NativeContractIllegalArgumentException(String.format(
                    "Invalid hash160 '%s' (should be 20 bytes and is %d bytes)",
                    Hex.toHexString(hash), hash.length
            ));
        }

        int version = ((BigInteger) arguments[1]).intValueExact();

        return new VersionedChecksummedBytes(version, hash).toBase58();
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean onlyAllowsLocalCalls() {
        return false;
    }
}