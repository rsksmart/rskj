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
import co.rsk.core.RskAddress;
import co.rsk.remasc.RemascTransaction;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.util.ByteUtil;

import java.util.Arrays;

/**
 * Created by Ruben on 8/1/2016.
 */
public class TransactionResultDTO {

    public String hash;
    public String nonce;
    public String blockHash;
    public String blockNumber;
    public String transactionIndex;

    public String from;
    public String to;
    public String gas;
    public String gasPrice;
    public String value;
    public String input;

    public String v;
    public String r;
    public String s;

    public TransactionResultDTO(Block b, Integer index, Transaction tx) {
        hash = tx.getHash().toJsonString();

        if (Arrays.equals(tx.getNonce(), ByteUtil.EMPTY_BYTE_ARRAY)) {
            nonce = "0";
        } else {
            nonce = TypeConverter.toJsonHex(tx.getNonce());
        }

        blockHash = b != null ? b.getHashJsonString() : null;
        blockNumber = b != null ? TypeConverter.toJsonHex(b.getNumber()) : null;
        transactionIndex = index != null ? TypeConverter.toJsonHex(index) : null;
        from = addressToJsonHex(tx.getSender());
        to = addressToJsonHex(tx.getReceiveAddress());
        gas = TypeConverter.toJsonHex(tx.getGasLimit()); // Todo: unclear if it's the gas limit or gas consumed what is asked

        gasPrice = TypeConverter.toJsonHex(tx.getGasPrice().getBytes());

        if (Coin.ZERO.equals(tx.getValue())) {
            value = "0";
        } else {
            value = TypeConverter.toJsonHex(tx.getValue().getBytes());
        }

        input = TypeConverter.toJsonHex(tx.getData());

        if (tx instanceof RemascTransaction) {
            // Web3.js requires the address to be valid (20 bytes),
            // so we have to serialize the Remasc sender as a valid address.
            from = TypeConverter.toJsonHex(new byte[20]);
        } else {
            ECKey.ECDSASignature signature = tx.getSignature();
            v = String.format("0x%02X", signature.v);
            r = TypeConverter.toJsonHex(signature.r);
            s = TypeConverter.toJsonHex(signature.s);
        }
    }

    private String addressToJsonHex(RskAddress address) {
        if (RskAddress.nullAddress().equals(address)) {
            return null;
        }
        return TypeConverter.toJsonHex(address.getBytes());
    }
}