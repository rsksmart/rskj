/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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
package co.rsk.db;

import co.rsk.trie.TrieStore;
import static org.ethereum.util.ByteUtil.toHexString;

public class ContractStorageStoreFactory {
    private TrieStore.Pool pool;

    public ContractStorageStoreFactory(TrieStore.Pool pool) {
        this.pool = pool;
    }

    public TrieStore getTrieStore(byte[] address) {
        return this.pool.getInstanceFor(getStorageNameForAddress(address));
    }

    private static String getUnifiedStorageName() {
        return "contracts-storage";
    }

    private static String getStorageNameForAddress(byte[] address) {
        return "details-storage/" + toHexString(address);
    }
}
