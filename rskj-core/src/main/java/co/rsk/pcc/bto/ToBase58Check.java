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

import co.rsk.core.types.bytes.Bytes;
import co.rsk.pcc.ExecutionEnvironment;
import co.rsk.pcc.NativeMethod;
import co.rsk.pcc.exception.NativeContractIllegalArgumentException;
import org.ethereum.core.CallTransaction;

import java.math.BigInteger;

/**
 * This implements the "toBase58Check" method
 * that belongs to the HDWalletUtils native contract.
 *
 * @author Ariel Mendelzon
 */
public class ToBase58Check extends NativeMethod {
    /**
     * This is here since the bitcoinj Address class performs version
     * validation depending on the actual network passed in,
     * and the given constructor within VersionedChecksummedBytes is protected.
     */
    private static class ExtendedVersionedChecksummedBytes extends co.rsk.bitcoinj.core.VersionedChecksummedBytes {
        private static final long serialVersionUID = 3721215336363685421L;

        public ExtendedVersionedChecksummedBytes(int version, byte[] bytes) {
            super(version, bytes);
        }
    }

    private final CallTransaction.Function function = CallTransaction.Function.fromSignature(
            "toBase58Check",
            new String[]{"bytes", "int256"},
            new String[]{"string"}
    );

    private final static String HASH_NOT_PRESENT = "hash160 must be present";
    private final static String HASH_INVALID = "Invalid hash160 '%s' (should be 20 bytes and is %d bytes)";
    private final static String INVALID_VERSION = "version must be a numeric value between 0 and 255";

    public ToBase58Check(ExecutionEnvironment executionEnvironment) {
        super(executionEnvironment);
    }

    @Override
    public CallTransaction.Function getFunction() {
        return function;
    }

    @Override
    public Object execute(Object[] arguments) throws NativeContractIllegalArgumentException {
        if (arguments == null) {
            throw new NativeContractIllegalArgumentException(HASH_NOT_PRESENT);
        }
        byte[] hash = (byte[]) arguments[0];
        if (hash == null) {
            throw new NativeContractIllegalArgumentException(HASH_NOT_PRESENT);
        }
        if (hash.length != 20) {
            throw new NativeContractIllegalArgumentException(String.format(
                    HASH_INVALID,
                    Bytes.of(hash), hash.length
            ));
        }

        int version = ((BigInteger) arguments[1]).intValueExact();
        if (version < 0 || version >= 256) {
            throw new NativeContractIllegalArgumentException(INVALID_VERSION);
        }

        return new ExtendedVersionedChecksummedBytes(version, hash).toBase58();
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean onlyAllowsLocalCalls() {
        return false;
    }

    @Override
    public long getGas(Object[] parsedArguments, byte[] originalData) {
        return 13_000L;
    }
}