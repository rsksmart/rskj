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
import java.util.HashMap;
import java.util.Map;

/**
 * Utility used for the Repository to translate {@link RskAddress} into keys of the trie.
 *
 * It uses internally a map cache of address->key (1:1, due to the immutability of the RskAddress object)
 */
public class TrieKeyMapper {

    public static final int SECURE_KEY_SIZE = 10;
    public static final int REMASC_ACCOUNT_KEY_SIZE = SECURE_KEY_SIZE + RemascTransaction.REMASC_ADDRESS.getBytes().length;
    public static final int ACCOUNT_KEY_SIZE = RskAddress.LENGTH_IN_BYTES;
    public static final int SECURE_ACCOUNT_KEY_SIZE = SECURE_KEY_SIZE + ACCOUNT_KEY_SIZE;
    private static final byte[] DOMAIN_PREFIX = new byte[] {0x00};
    private static final byte[] STORAGE_PREFIX = new byte[] {0x00}; // This makes the MSB 0 be branching
    private static final byte[] CODE_PREFIX = new byte[] {(byte) 0x80}; // This makes the MSB 1 be branching

    private final Map<RskAddress, byte[]> accountKeys = new HashMap<>(); //map cache of address->key (1:1) ** RskAddress is immutable.

    public synchronized byte[] getAccountKey(RskAddress addr) {
        if (accountKeys.containsKey(addr)) {
            byte[] key = accountKeys.get(addr);
            return Arrays.copyOf(key, key.length);
        }

        byte[] key = mapRskAddressToKey(addr);

        accountKeys.put(addr, key);

        return Arrays.copyOf(key, key.length);
    }

    public byte[] getCodeKey(RskAddress addr) {
        return ByteUtil.merge(getAccountKey(addr), CODE_PREFIX);
    }

    public byte[] getAccountStoragePrefixKey(RskAddress addr) {
        return ByteUtil.merge(getAccountKey(addr), STORAGE_PREFIX);
    }

    public byte[] getAccountStorageKey(RskAddress addr, DataWord subkeyDW) {
        // TODO(SDL) should we hash the full subkey or the stripped one?
        byte[] subkey = subkeyDW.getData();
        byte[] secureKeyPrefix = secureKeyPrefix(subkey);
        byte[] storageKey = ByteUtil.merge(secureKeyPrefix, ByteUtil.stripLeadingZeroes(subkey));
        return ByteUtil.merge(getAccountStoragePrefixKey(addr), storageKey);
    }

    public byte[] secureKeyPrefix(byte[] key) {
        return Arrays.copyOfRange(Keccak256Helper.keccak256(key), 0, SECURE_KEY_SIZE);
    }

    public static byte[] domainPrefix() {
        return Arrays.copyOf(DOMAIN_PREFIX, DOMAIN_PREFIX.length);
    }

    public static byte[] storagePrefix() {
        return Arrays.copyOf(STORAGE_PREFIX, STORAGE_PREFIX.length);
    }

    protected byte[] mapRskAddressToKey(RskAddress addr) {
        byte[] secureKey = secureKeyPrefix(addr.getBytes());
        return ByteUtil.merge(DOMAIN_PREFIX, secureKey, addr.getBytes());
    }

}
