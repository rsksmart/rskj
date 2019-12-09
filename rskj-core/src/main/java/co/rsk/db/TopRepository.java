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

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.trie.Trie;
import org.ethereum.core.Account;
import org.ethereum.core.AccountState;
import org.ethereum.core.Repository;
import org.ethereum.db.TrieKeyMapper;
import org.ethereum.vm.DataWord;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.*;

/**
 * Created by ajlopez on 06/12/2019.
 */
public class TopRepository extends AbstractRepository {
    private static final byte[] ONE_BYTE_ARRAY = new byte[] { 0x01 };

    private Trie trie;

    private final TrieKeyMapper trieKeyMapper = new TrieKeyMapper();

    public TopRepository(Trie trie) {
        this.trie = trie;
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
    public byte[] getRoot() {
        // TODO trie save?
        // TODO commit?
        
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
        this.trie = this.trie.put(this.trieKeyMapper.getAccountKey(address), accountState.getEncoded());
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
}
