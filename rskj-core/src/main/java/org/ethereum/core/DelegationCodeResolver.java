/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
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
package org.ethereum.core;

import co.rsk.core.RskAddress;

import java.util.Arrays;

public class DelegationCodeResolver {

    public static final byte[] DELEGATION_PREFIX = new byte[] {(byte) 0xef, 0x01, 0x00};
    private static final int PREFIX_SIZE = DELEGATION_PREFIX.length;
    private static final int ADDRESS_SIZE = 20;
    private static final int DELEGATION_CODE_SIZE = PREFIX_SIZE + ADDRESS_SIZE;

    private DelegationCodeResolver() { }

    public static boolean isDelegatedCode(byte[] code) {
        return code != null
                && code.length == DELEGATION_CODE_SIZE
                && code[0] == DELEGATION_PREFIX[0]
                && code[1] == DELEGATION_PREFIX[1]
                && code[2] == DELEGATION_PREFIX[2];
    }

    public static byte[] createDelegatedCode(RskAddress delegatedAddress) {
        if (delegatedAddress.equals(RskAddress.nullAddress()) || delegatedAddress.equals(RskAddress.ZERO_ADDRESS)) {
            throw new IllegalStateException("Delegated address can not be empty");
        }
        byte[] delegatedAddressBytes = delegatedAddress.getBytes();
        byte[] codeToSet = new byte[DELEGATION_CODE_SIZE];

        System.arraycopy(DELEGATION_PREFIX, 0, codeToSet, 0, PREFIX_SIZE);
        System.arraycopy(delegatedAddressBytes, 0, codeToSet, PREFIX_SIZE, ADDRESS_SIZE);
        return codeToSet;
    }

    public static RskAddress extractDelegatedAddress(byte[] code) {
        byte[] addressBytes = Arrays.copyOfRange(code, PREFIX_SIZE, DELEGATION_CODE_SIZE);
        return new RskAddress(addressBytes);
    }
}
