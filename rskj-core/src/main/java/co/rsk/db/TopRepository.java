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
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.AccountState;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.TrieKeyMapper;
import org.ethereum.vm.DataWord;

import java.util.*;

/**
 * Created by ajlopez on 06/12/2019.
 */
public class TopRepository extends AbstractRepository implements TopRepositorySnapshot {
    private static final byte[] ONE_BYTE_ARRAY = new byte[] { 0x01 };

    private Trie trie;
    private final TrieStore trieStore;

    private final TrieKeyMapper trieKeyMapper = new TrieKeyMapper();

    public TopRepository(Trie trie, TrieStore trieStore) {
        this.trie = trie;
        this.trieStore = trieStore;
    }

    public Trie getTrie() {
        return this.trie;
    }

    @Override
    public void addStorageRow(RskAddress address, DataWord key, DataWord value) {
        this.addStorageBytes(address, key, value.getByteArrayForStorage());
    }

    public void save() {
        // TODO commit is needed?
        this.commit();

        if (this.trieStore != null) {
            this.trieStore.save(this.trie);
        }
    }

    @Override
    public byte[] getRoot() {
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

    @VisibleForTesting
    public byte[] getStorageStateRoot(RskAddress addr) {
        this.commit();

        byte[] prefix = trieKeyMapper.getAccountStoragePrefixKey(addr);

        // The value should be ONE_BYTE_ARRAY, but we don't need to check nothing else could be there.
        Trie storageRootNode = this.trie.find(prefix);
        if (storageRootNode == null) {
            return HashUtil.EMPTY_TRIE_HASH;
        }

        // Now it's a bit tricky what to return: if I return the storageRootNode hash then it's counting the "0x01"
        // value, so the try one gets will never match the trie one gets if creating the trie without any other data.
        // Unless the PDV trie is used. The best we can do is to return storageRootNode hash
        return storageRootNode.getHash().getBytes();
    }

    @Override
    public Iterator<DataWord> retrieveStorageKeys(RskAddress address) {
        byte[] accountStorageKey = trieKeyMapper.getAccountStoragePrefixKey(address);
        final int storageKeyOffset = (TrieKeyMapper.storagePrefix().length + TrieKeyMapper.SECURE_KEY_SIZE) * Byte.SIZE - 1;

        Trie storageTrie = trie.find(accountStorageKey);

        if (storageTrie != null) {
            Iterator<Trie.IterationElement> storageIterator = storageTrie.getPreOrderIterator();
            storageIterator.next(); // skip storage root
            return new TopRepository.StorageKeysIterator(storageIterator, storageKeyOffset);
        }

        return Collections.emptyIterator();
    }

    private static class StorageKeysIterator implements Iterator<DataWord> {
        private final Iterator<Trie.IterationElement> storageIterator;
        private final int storageKeyOffset;
        private DataWord currentStorageKey;

        StorageKeysIterator(Iterator<Trie.IterationElement> storageIterator, int storageKeyOffset) {
            this.storageIterator = storageIterator;
            this.storageKeyOffset = storageKeyOffset;
        }

        @Override
        public boolean hasNext() {
            if (currentStorageKey != null) {
                return true;
            }
            while (storageIterator.hasNext()) {
                Trie.IterationElement iterationElement = storageIterator.next();
                if (iterationElement.getNode().getValue() != null) {
                    TrieKeySlice nodeKey = iterationElement.getNodeKey();
                    byte[] storageExpandedKeySuffix = nodeKey.slice(storageKeyOffset, nodeKey.length()).encode();
                    currentStorageKey = DataWord.valueOf(storageExpandedKeySuffix);
                    return true;
                }
            }
            return false;
        }

        @Override
        public DataWord next() {
            if (currentStorageKey == null && !hasNext()) {
                throw new NoSuchElementException();
            }

            DataWord next = currentStorageKey;
            currentStorageKey = null;
            return next;
        }
    }
}
