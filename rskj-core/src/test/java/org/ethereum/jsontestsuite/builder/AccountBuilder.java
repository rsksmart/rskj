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
import co.rsk.core.Coin;
import co.rsk.db.ContractDetailsImpl;
import org.ethereum.core.AccountState;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.jsontestsuite.model.AccountTck;
import org.ethereum.vm.DataWord;

import java.util.HashMap;
import java.util.Map;

import static org.ethereum.crypto.HashUtil.keccak256;
import static org.ethereum.json.Utils.parseData;
import static org.ethereum.util.Utils.unifiedNumericToBigInteger;

public class AccountBuilder {

    public static StateWrap build(AccountTck account, HashMapDB store) {

        TestSystemProperties config = new TestSystemProperties();
        ContractDetailsImpl details = new ContractDetailsImpl(null,
                ContractDetailsImpl.newStorage(), null);
        details.setCode(parseData(account.getCode()));
        details.setStorage(convertStorage(account.getStorage()));

        AccountState state = new AccountState();

        state.addToBalance(new Coin(unifiedNumericToBigInteger(account.getBalance())));
        state.setNonce(unifiedNumericToBigInteger(account.getNonce()));
        //state.setCodeHash(HashUtil.keccak256(details.getCode()));

        return new StateWrap(state, details);
    }


    private static Map<DataWord, byte[]> convertStorage(Map<String, String> storageTck) {

        Map<DataWord, byte[]> storage = new HashMap<>();

        for (String keyTck : storageTck.keySet()) {
            String valueTck = storageTck.get(keyTck);

            DataWord key = new DataWord(parseData(keyTck));
            byte[] value = parseData(valueTck);

            storage.put(key, value);
        }

        return storage;
    }


    public static class StateWrap {

        AccountState accountState;
        ContractDetailsImpl contractDetails;

        public StateWrap(AccountState accountState, ContractDetailsImpl contractDetails) {
            this.accountState = accountState;
            this.contractDetails = contractDetails;
        }

        public AccountState getAccountState() {
            return accountState;
        }

        public ContractDetailsImpl getContractDetails() {
            return contractDetails;
        }
    }
}
