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

import co.rsk.core.RskAddress;
import co.rsk.trie.TrieStore;
import org.ethereum.vm.PrecompiledContracts;

import static org.ethereum.util.ByteUtil.toHexString;

public class ContractStorageStoreFactory {
    private TrieStore.Pool pool;

    public ContractStorageStoreFactory(TrieStore.Pool pool) {
        this.pool = pool;
    }

    public TrieStore getTrieStore(byte[] address) {
        synchronized (ContractStorageStoreFactory.class) {
            if (addressIsDedicated(address)) {
                return this.pool.getInstanceFor(getStorageNameForAddress(address));
            }

            TrieStore unifiedStore = this.pool.getInstanceFor(getUnifiedStorageName());

            String addressName = getStorageNameForAddress(address);

            if (this.pool.existsInstanceFor(addressName)) {
                TrieStore dedicatedStore = this.pool.getInstanceFor(addressName);
                unifiedStore.copyFrom(dedicatedStore);
                this.pool.closeInstanceFor(addressName);
                this.pool.destroyInstanceFor(addressName);
            }

            return unifiedStore;
        }
    }

    private static String getUnifiedStorageName() {
        return "contracts-storage";
    }

    private static String getStorageNameForAddress(byte[] address) {
        return "details-storage/" + toHexString(address);
    }

    private static boolean addressIsDedicated(byte[] address) {
        if (address == null || address.length != 20) {
            return false;
        }

        RskAddress addr = new RskAddress(address);

        return addr.equals(PrecompiledContracts.REMASC_ADDR) || addr.equals(PrecompiledContracts.BRIDGE_ADDR);
    }
}
