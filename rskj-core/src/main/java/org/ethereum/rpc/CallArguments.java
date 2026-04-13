/*
 * This file is part of RskJ
 * Copyright (C) 2021 RSK Labs Ltd.
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

package org.ethereum.rpc;

import java.util.List;

/**
 * Wraps call arguments for several json-rpc methods.
 * Take account to fill up the arguments using the right hex value encoding (QUANTITY and UNFORMATTED DATA),
 * a simple way to do this is by using the TypeConverter class.
 *
 * Note: you can find more info about hex encoding in this site https://eth.wiki/json-rpc/API
 * */
public class CallArguments {
    private String from;
    private String to;
    private String gas;
    private String gasLimit;
    private String gasPrice;
    /** EIP-1559 (Type 2); optional hex quantity */
    private String maxPriorityFeePerGas;
    private String maxFeePerGas;
    private String value;
    private String data; // compiledCode
    private String nonce;
    private String chainId;
    private String type; // This was ignored before (see https://github.com/rsksmart/rskj/pull/1601)
    private String rskSubtype;
    /** EIP-2930 / EIP-1559 access list; null or empty means no access list entries */
    private List<AccessListEntry> accessList;

    /**
     * A single entry in an EIP-2930 / EIP-1559 access list.
     * {@code address} is a hex-encoded 20-byte Ethereum address.
     * {@code storageKeys} is a list of hex-encoded 32-byte storage slot keys.
     */
    public static class AccessListEntry {
        private String address;
        private List<String> storageKeys;

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public List<String> getStorageKeys() {
            return storageKeys;
        }

        public void setStorageKeys(List<String> storageKeys) {
            this.storageKeys = storageKeys;
        }
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getGas() {
        return gas;
    }

    public void setGas(String gas) {
        this.gas = gas;
    }

    public String getGasLimit() {
        return gasLimit;
    }

    public void setGasLimit(String gasLimit) {
        this.gasLimit = gasLimit;
    }

    public String getGasPrice() {
        return gasPrice;
    }

    public void setGasPrice(String gasPrice) {
        this.gasPrice = gasPrice;
    }

    public String getMaxPriorityFeePerGas() {
        return maxPriorityFeePerGas;
    }

    public void setMaxPriorityFeePerGas(String maxPriorityFeePerGas) {
        this.maxPriorityFeePerGas = maxPriorityFeePerGas;
    }

    public String getMaxFeePerGas() {
        return maxFeePerGas;
    }

    public void setMaxFeePerGas(String maxFeePerGas) {
        this.maxFeePerGas = maxFeePerGas;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getData() {
        return this.data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }

    public String getChainId() {
        return chainId;
    }

    public void setChainId(String chainId) {
        this.chainId = chainId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRskSubtype() {
        return rskSubtype;
    }

    public void setRskSubtype(String rskSubtype) {
        this.rskSubtype = rskSubtype;
    }

    public List<AccessListEntry> getAccessList() {
        return accessList;
    }

    public void setAccessList(List<AccessListEntry> accessList) {
        this.accessList = accessList;
    }

    public String getInput() {
        return this.data;
    }

    public void setInput(String input) {
        this.data = input;
    }

    @Override
    public String toString() {
        return "CallArguments{" +
                "from='" + from + '\'' +
                ", to='" + to + '\'' +
                ", gas='" + gas + '\'' +
                ", gasLimit='" + gasLimit + '\'' +
                ", gasPrice='" + gasPrice + '\'' +
                ", maxPriorityFeePerGas='" + maxPriorityFeePerGas + '\'' +
                ", maxFeePerGas='" + maxFeePerGas + '\'' +
                ", value='" + value + '\'' +
                ", data='" + data + '\'' +
                ", nonce='" + nonce + '\'' +
                ", chainId='" + chainId + '\'' +
                ", type='" + type + '\'' +
                ", rskSubtype='" + rskSubtype + '\'' +
                ", accessList=" + accessList +
                '}';
    }
}
