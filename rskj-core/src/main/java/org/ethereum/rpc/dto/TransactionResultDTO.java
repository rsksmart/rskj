/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package org.ethereum.rpc.dto;

import co.rsk.core.Coin;
import co.rsk.remasc.RemascTransaction;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.signature.ECDSASignature;
import org.ethereum.rpc.TypeConverter;

/**
 * Created by Ruben on 8/1/2016.
 */
public class TransactionResultDTO {

    private String hash;
    private String nonce;
    private String blockHash;
    private String blockNumber;
    private String transactionIndex;
    private String from;
    private String to;
    private String gas;
    private String gasPrice;
    private String value;
    private String input;
    private String v;
    private String r;
    private String s;

    public TransactionResultDTO(Block b, Integer index, Transaction tx) {
        hash = tx.getHash().toJsonString();

        nonce = TypeConverter.toQuantityJsonHex(tx.getNonce());

        blockHash = b != null ? b.getHashJsonString() : null;
        blockNumber = b != null ? TypeConverter.toQuantityJsonHex(b.getNumber()) : null;
        transactionIndex = index != null ? TypeConverter.toQuantityJsonHex(index) : null;

        from = tx.getSender().toJsonString();
        to = tx.getReceiveAddress().toJsonString();
        gas = TypeConverter.toQuantityJsonHex(tx.getGasLimit());

        gasPrice = TypeConverter.toQuantityJsonHex(tx.getGasPrice().getBytes());

        if (Coin.ZERO.equals(tx.getValue())) {
            value = "0x0";
        } else {
            value = TypeConverter.toQuantityJsonHex(tx.getValue().getBytes());
        }

        input = TypeConverter.toUnformattedJsonHex(tx.getData());

        if (!(tx instanceof RemascTransaction)) {
            ECDSASignature signature = tx.getSignature();

            v = String.format("0x%02x", tx.getEncodedV());

            r = TypeConverter.toQuantityJsonHex(signature.getR());
            s = TypeConverter.toQuantityJsonHex(signature.getS());
        }
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }

    public String getBlockHash() {
        return blockHash;
    }

    public void setBlockHash(String blockHash) {
        this.blockHash = blockHash;
    }

    public String getBlockNumber() {
        return blockNumber;
    }

    public void setBlockNumber(String blockNumber) {
        this.blockNumber = blockNumber;
    }

    public String getTransactionIndex() {
        return transactionIndex;
    }

    public void setTransactionIndex(String transactionIndex) {
        this.transactionIndex = transactionIndex;
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

    public String getGasPrice() {
        return gasPrice;
    }

    public void setGasPrice(String gasPrice) {
        this.gasPrice = gasPrice;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }

    public String getV() {
        return v;
    }

    public void setV(String v) {
        this.v = v;
    }

    public String getR() {
        return r;
    }

    public void setR(String r) {
        this.r = r;
    }

    public String getS() {
        return s;
    }

    public void setS(String s) {
        this.s = s;
    }

}