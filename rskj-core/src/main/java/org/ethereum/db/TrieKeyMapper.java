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

package org.ethereum.db;

import co.rsk.core.RskAddress;
import co.rsk.remasc.RemascTransaction;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;

import java.util.Arrays;

public class TrieKeyMapper {
    public static final int SECURE_KEY_SIZE = 10;
    public static final int REMASC_ACCOUNT_KEY_SIZE = SECURE_KEY_SIZE + RemascTransaction.REMASC_ADDRESS.getBytes().length;
    public static final int ACCOUNT_KEY_SIZE = RskAddress.LENGTH_IN_BYTES;
    public static final int SECURE_ACCOUNT_KEY_SIZE = SECURE_KEY_SIZE + ACCOUNT_KEY_SIZE;
    public static final int STORAGE_KEY_SIZE = SECURE_ACCOUNT_KEY_SIZE + Byte.BYTES + SECURE_KEY_SIZE + DataWord.BYTES;
    public static final byte[] DOMAIN_PREFIX = new byte[] {0x00};
    public static final byte[] STORAGE_PREFIX = new byte[] {0x00}; // This makes the MSB 0 be branching
    public static final byte[] CODE_PREFIX = new byte[] {(byte) 0x80}; // This makes the MSB 1 be branching

    // This is a performance enhancement. When multiple storage rows for the same
    // contract are stored, the same RskAddress is hashed over and over.
    // We don't need to re-hash it if was hashed last time.
    // The reduction we get is about 50% (2x efficiency)
    private RskAddress lastAddr;
    private byte[] lastAccountKey;

    public synchronized byte[] getAccountKey(RskAddress addr) {
        if (addr.equals(lastAddr)) {
            return lastAccountKey;
        }

        byte[] secureKey = secureKeyPrefix(addr.getBytes());
        lastAccountKey = ByteUtil.merge(DOMAIN_PREFIX, secureKey, addr.getBytes());
        lastAddr = addr;
        return lastAccountKey;
    }

    public byte[] getCodeKey(RskAddress addr) {
        return ByteUtil.merge(getAccountKey(addr), CODE_PREFIX);
    }

    public byte[] getAccountStoragePrefixKey(RskAddress addr) {
        return ByteUtil.merge(getAccountKey(addr), STORAGE_PREFIX);
    }

    public byte[] getAccountStorageKey(RskAddress addr, byte[] subkey) {
        // TODO(SDL) should we hash the full subkey or the stripped one?
        byte[] secureKeyPrefix = secureKeyPrefix(subkey);
        byte[] storageKey = ByteUtil.merge(secureKeyPrefix, ByteUtil.stripLeadingZeroes(subkey));
        return ByteUtil.merge(getAccountStoragePrefixKey(addr), storageKey);
    }

    private byte[] secureKeyPrefix(byte[] key) {
        return Arrays.copyOfRange(Keccak256Helper.keccak256(key), 0, SECURE_KEY_SIZE);
    }
}
