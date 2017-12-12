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

import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
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

    public TransactionResultDTO (Block b, Integer index, Transaction tx) {
        hash =  TypeConverter.toJsonHex(tx.getHash());

        if (Arrays.equals(tx.getNonce(), ByteUtil.EMPTY_BYTE_ARRAY)) {
            nonce = "0";
        } else {
            nonce = TypeConverter.toJsonHex(tx.getNonce());
        }

        blockHash = b != null ? TypeConverter.toJsonHex(b.getHash()) : null;
        blockNumber = b != null ? TypeConverter.toJsonHex(b.getNumber()) : null;
        transactionIndex = index != null ? TypeConverter.toJsonHex(index) : null;
        from= TypeConverter.toJsonHex(tx.getSender());
        to = TypeConverter.toJsonHex(tx.getReceiveAddress());
        gas = TypeConverter.toJsonHex(tx.getGasLimit()); // Todo: unclear if it's the gas limit or gas consumed what is asked

        gasPrice = TypeConverter.toJsonHex(tx.getGasPrice());

        if (Arrays.equals(tx.getValue(), ByteUtil.EMPTY_BYTE_ARRAY)) {
            value = "0";
        } else {
            value = TypeConverter.toJsonHex(tx.getValue());
        }

        input = TypeConverter.toJsonHex(tx.getData());
    }

}