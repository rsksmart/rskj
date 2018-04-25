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

package org.ethereum.jsontestsuite;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.json.simple.JSONObject;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Roman Mandeleil
 * @since 28.06.2014
 */
public class AccountState {

    RskAddress address;
    Coin balance;
    byte[] code;
    byte[] nonce;

    Map<DataWord, DataWord> storage = new HashMap<>();


    public AccountState(RskAddress address, JSONObject accountState) {

        this.address = address;
        String balance = accountState.get("balance").toString();
        String code = (String) accountState.get("code");
        String nonce = accountState.get("nonce").toString();

        JSONObject store = (JSONObject) accountState.get("storage");

        this.balance = new Coin(TestCase.toBigInt(balance));

        if (code != null && code.length() > 2)
            this.code = Hex.decode(code.substring(2));
        else
            this.code = ByteUtil.EMPTY_BYTE_ARRAY;

        this.nonce = TestCase.toBigInt(nonce).toByteArray();

        int size = store.keySet().size();
        Object[] keys = store.keySet().toArray();
        for (int i = 0; i < size; ++i) {

            String keyS = keys[i].toString();
            String valS = store.get(keys[i]).toString();

            byte[] key = org.ethereum.json.Utils.parseData(keyS);
            byte[] value = org.ethereum.json.Utils.parseData(valS);
            storage.put(new DataWord(key), new DataWord(value));
        }
    }

    public RskAddress getAddress() {
        return address;
    }

    public Coin getBalance() {
        return balance;
    }

    public byte[] getCode() {
        return code;
    }

    public byte[] getNonce() {
        return nonce;
    }

    public long getNonceLong() {
        return new BigInteger(nonce).longValue();
    }


    public Map<DataWord, DataWord> getStorage() {
        return storage;
    }

    @Override
    public String toString() {
        return "AccountState{" +
                "address=" + address +
                ", balance=" + balance +
                ", code=" + Hex.toHexString(code) +
                ", nonce=" + Hex.toHexString(nonce) +
                ", storage=" + storage +
                '}';
    }
}
