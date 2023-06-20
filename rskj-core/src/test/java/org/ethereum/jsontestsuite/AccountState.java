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
import com.fasterxml.jackson.databind.JsonNode;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Iterator;
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


    public AccountState(RskAddress address, JsonNode accountState) {

        this.address = address;
        String balance = accountState.get("balance").asText();
        String code = accountState.get("code").asText();
        String nonce = accountState.get("nonce").asText();

        JsonNode store = accountState.get("storage");

        this.balance = new Coin(TestingCase.toBigInt(balance));

        if (code != null && code.length() > 2)
            this.code = Hex.decode(code.substring(2));
        else
            this.code = ByteUtil.EMPTY_BYTE_ARRAY;

        this.nonce = TestingCase.toBigInt(nonce).toByteArray();

        for (Iterator<String> it = store.fieldNames(); it.hasNext(); ) {
            String keyS = it.next();
            String valS = store.get(keyS).asText();

            byte[] key = org.ethereum.json.Utils.parseData(keyS);
            byte[] value = org.ethereum.json.Utils.parseData(valS);
            storage.put(DataWord.valueOf(key), DataWord.valueOf(value));
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
                ", code=" + ByteUtil.toHexString(code) +
                ", nonce=" + ByteUtil.toHexString(nonce) +
                ", storage=" + storage +
                '}';
    }
}
