/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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
import co.rsk.trie.Trie;
import co.rsk.trie.TrieKeySlice;
import co.rsk.trie.TrieStore;
import org.ethereum.core.AccountState;
import org.ethereum.db.TrieKeyMapper;
import org.ethereum.vm.DataWord;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Created by ajlopez on 06/12/2019.
 */
public class TopRepository extends AbstractRepository {
    private static final byte[] ONE_BYTE_ARRAY = new byte[] { 0x01 };

    private Trie trie;
    private final TrieStore trieStore;

    private final TrieKeyMapper trieKeyMapper = new TrieKeyMapper();

    public TopRepository(Trie trie, TrieStore trieStore) {
        this.trie = trie;
        this.trieStore = trieStore;
    }

    @Override
    public Trie getTrie() {
        return this.trie;
    }

    @Override
    public void addStorageRow(RskAddress address, DataWord key, DataWord value) {
        this.addStorageBytes(address, key, value.getByteArrayForStorage());
    }

    @Override
    public void save() {
        // TODO commit is needed?
        this.commit();

        if (this.trieStore != null) {
            this.trieStore.save(this.trie);
        }
    }

    @Override
    public byte[] getRoot() {
        // TODO alternative: throw exception if pending commit
        this.commit();

        return this.trie.getHash().getBytes();
    }

    @Override
    public AccountState retrieveAccountState(RskAddress address) {
        byte[] data = this.trie.get(this.trieKeyMapper.getAccountKey(address));

        if (data == null) {
            return null;
        }

        return new AccountState(data);
    }

    @Override
    public byte[] retrieveStorageBytes(RskAddress address, DataWord key) {
        return this.trie.get(this.trieKeyMapper.getAccountStorageKey(address, key));
    }

    @Override
    public byte[] retrieveCode(RskAddress address) {
        return this.trie.get(this.trieKeyMapper.getCodeKey(address));
    }

    @Override
    public boolean retrieveIsContract(RskAddress address) {
        return this.trie.get(this.trieKeyMapper.getAccountStoragePrefixKey(address)) != null;
    }

    @Override
    public void commitAccountState(RskAddress address, AccountState accountState) {
        if (accountState == null) {
            this.trie = this.trie.deleteRecursive(this.trieKeyMapper.getAccountKey(address));
        }
        else {
            this.trie = this.trie.put(this.trieKeyMapper.getAccountKey(address), accountState.getEncoded());
        }
    }

    @Override
    public void commitContract(RskAddress address) {
        this.trie = this.trie.put(this.trieKeyMapper.getAccountStoragePrefixKey(address), ONE_BYTE_ARRAY);
    }

    @Override
    public void commitStorage(RskAddress address, DataWord key, byte[] value) {
        this.trie = this.trie.put(this.trieKeyMapper.getAccountStorageKey(address, key), value);
    }

    @Override
    public void commitCode(RskAddress address, byte[] code) {
        this.trie = this.trie.put(this.trieKeyMapper.getCodeKey(address), code);
    }

    @Override
    public Set<RskAddress> retrieveAccountsKeys() {
        Set<RskAddress> result = new HashSet<>();

        Iterator<Trie.IterationElement> preOrderIterator = this.trie.getPreOrderIterator();

        while (preOrderIterator.hasNext()) {
            TrieKeySlice nodeKey = preOrderIterator.next().getNodeKey();
            int nodeKeyLength = nodeKey.length();

            if (nodeKeyLength == (1 + TrieKeyMapper.SECURE_KEY_SIZE + RskAddress.LENGTH_IN_BYTES) * Byte.SIZE) {
                byte[] address = nodeKey.slice(nodeKeyLength - RskAddress.LENGTH_IN_BYTES * Byte.SIZE, nodeKeyLength).encode();
                result.add(new RskAddress(address));
            }
        }

        return result;
    }
}
