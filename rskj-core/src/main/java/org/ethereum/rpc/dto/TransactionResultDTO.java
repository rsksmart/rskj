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
}