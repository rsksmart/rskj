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

package org.ethereum.jsontestsuite.builder;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.db.MutableTrieImpl;
import co.rsk.trie.TrieImpl;
import co.rsk.trie.TrieStoreImpl;
import org.ethereum.core.AccountState;
import org.ethereum.core.Repository;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.ContractDetails;
import org.ethereum.db.MutableRepository;
import org.ethereum.jsontestsuite.model.AccountTck;

import java.util.HashMap;
import java.util.Map;

public class RepositoryBuilder {

    public static Repository build(Map<String, AccountTck> accounts){
        HashMap<RskAddress, AccountState> stateBatch = new HashMap<>();
        HashMap<RskAddress, ContractDetails> detailsBatch = new HashMap<>();
        HashMapDB store = new HashMapDB();

        for (String address : accounts.keySet()) {
            RskAddress addr = new RskAddress(address);

            AccountTck accountTCK = accounts.get(address);
            AccountBuilder.StateWrap stateWrap = AccountBuilder.build(accountTCK, store);

            AccountState state = stateWrap.getAccountState();
            ContractDetails details = stateWrap.getContractDetails();

            stateBatch.put(addr, state);

            details.setDirty(true);

            detailsBatch.put(addr, details);
        }

        final TestSystemProperties testSystemProperties = new TestSystemProperties();
        // It must me not secure in order to be able to collect keys later
        Repository repositoryDummy = new MutableRepository(new MutableTrieImpl(new TrieImpl(new TrieStoreImpl(new HashMapDB()), false)));
        Repository track = repositoryDummy.startTracking();
        track.updateBatch(stateBatch);
        track.updateBatchDetails(detailsBatch);

        track.commit();

        return repositoryDummy;
    }
}
