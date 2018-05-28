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
import co.rsk.crypto.Keccak256;
import co.rsk.db.RepositoryImpl;
import co.rsk.trie.TrieStoreImpl;
import org.ethereum.core.AccountState;
import org.ethereum.core.Repository;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.ContractDetails;
import org.ethereum.db.ContractDetailsCacheImpl;
import org.ethereum.jsontestsuite.model.AccountTck;
import org.spongycastle.util.encoders.Hex;

import java.util.HashMap;
import java.util.Map;

public class RepositoryBuilder {

    public static Repository build(Map<String, AccountTck> accounts){
        HashMap<RskAddress, AccountState> stateBatch = new HashMap<>();
        HashMap<RskAddress, ContractDetails> detailsBatch = new HashMap<>();
        HashMap<Keccak256, byte[]> codeBatch = new HashMap<>();

        for (String address : accounts.keySet()) {
            RskAddress addr = new RskAddress(address);

            AccountTck accountTCK = accounts.get(address);
            AccountBuilder.StateWrap stateWrap = AccountBuilder.build(accountTCK);

            AccountState state = stateWrap.getAccountState();
            ContractDetails details = stateWrap.getContractDetails();

            stateBatch.put(addr, state);

            ContractDetailsCacheImpl detailsCache = new ContractDetailsCacheImpl(details);
            detailsCache.setDirty(true);

            detailsBatch.put(addr, detailsCache);

            if (accountTCK.getCode() != null && accountTCK.getCode().length() > 0) {
                String codestr = accountTCK.getCode();
                if (codestr.startsWith("0x") || codestr.startsWith("0X")) {
                    codestr = codestr.substring(2);
                }
                byte[] code = Hex.decode(codestr);
                Keccak256 hash = new Keccak256(Keccak256Helper.keccak256(code));
                codeBatch.put(hash, code);
            }
        }

        RepositoryImpl repositoryDummy = new RepositoryImpl(new TestSystemProperties(), new TrieStoreImpl(new HashMapDB()));
        Repository track = repositoryDummy.startTracking();
        track.updateBatch(stateBatch, detailsBatch, codeBatch);
        track.commit();

        return repositoryDummy;
    }
}
