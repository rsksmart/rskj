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

import static org.ethereum.json.Utils.parseData;
import static org.ethereum.util.Utils.unifiedNumericToBigInteger;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.db.MutableTrieImpl;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStoreImpl;
import java.util.Map;
import org.ethereum.core.AccountState;
import org.ethereum.core.Repository;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.MutableRepository;
import org.ethereum.jsontestsuite.model.AccountTck;
import org.ethereum.vm.DataWord;

public class RepositoryBuilder {

    public static Repository build(Map<String, AccountTck> accounts) {
        Repository repositoryDummy =
                new MutableRepository(
                        new MutableTrieImpl(new Trie(new TrieStoreImpl(new HashMapDB()))));
        Repository track = repositoryDummy.startTracking();
        for (String address : accounts.keySet()) {
            RskAddress addr = new RskAddress(address);
            AccountTck accountTCK = accounts.get(address);

            AccountState state =
                    new AccountState(
                            unifiedNumericToBigInteger(accountTCK.getNonce()),
                            new Coin(unifiedNumericToBigInteger(accountTCK.getBalance())));
            track.updateAccountState(addr, state);
            byte[] code = parseData(accountTCK.getCode());
            if (accountTCK.isForcedContract()
                    || code.length > 0
                    || !accountTCK.getStorage().isEmpty()) {
                track.setupContract(addr);
                track.saveCode(addr, code);
                saveStorageValues(track, addr, accountTCK.getStorage());
            }
        }

        track.commit();

        return repositoryDummy;
    }

    private static void saveStorageValues(
            Repository track, RskAddress addr, Map<String, String> storageTck) {
        for (String keyTck : storageTck.keySet()) {
            String valueTck = storageTck.get(keyTck);

            DataWord key = DataWord.valueOf(parseData(keyTck));
            byte[] value = parseData(valueTck);

            track.addStorageBytes(addr, key, value);
        }
    }
}
