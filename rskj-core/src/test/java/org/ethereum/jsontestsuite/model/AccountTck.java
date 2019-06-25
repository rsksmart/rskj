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

package org.ethereum.jsontestsuite.model;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Roman Mandeleil
 * @since 28.06.2014
 */
public class AccountTck {

    boolean forcedContract;
    String balance;
    String code;
    String nonce;

    Map<String, String> storage = new HashMap<>();

    String privateKey;

    public AccountTck() {}

    public boolean isForcedContract() {
        return forcedContract;
    }

    public void setForcedContract(boolean forcedContract) {
        this.forcedContract = forcedContract;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public String getBalance() {
        return balance;
    }

    public void setBalance(String balance) {
        this.balance = balance;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }

    public Map<String, String> getStorage() {
        return storage;
    }

    public void setStorage(Map<String, String> storage) {
        this.storage = storage;
    }

    @Override
    public String toString() {
        return "AccountState2{"
                + "balance='"
                + balance
                + '\''
                + ", code='"
                + code
                + '\''
                + ", nonce='"
                + nonce
                + '\''
                + ", storage="
                + storage
                + '}';
    }
}
