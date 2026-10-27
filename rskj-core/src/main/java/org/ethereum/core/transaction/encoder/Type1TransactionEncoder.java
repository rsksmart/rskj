/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
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
package org.ethereum.core.transaction.encoder;

import org.bouncycastle.util.BigIntegers;
import org.ethereum.core.Transaction;
import org.ethereum.core.transaction.encoder.util.TransactionEncodingUtils;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

public class Type1TransactionEncoder  implements TransactionEncoder {

    /** Type 1 full encoding: 11 elements with signature */
    @Override
    public byte[] encodeSigned(Transaction transaction) {
        byte[][] fields = encodeUnsignedFields(transaction);
        byte[] yParity = transaction.getSignature() != null
                ? RLP.encodeByte((byte) (transaction.getSignature() .getV() - Transaction.LOWER_REAL_V))
                : RLP.encodeByte((byte) 0);
        byte[] r = transaction.getSignature()  != null
                ? RLP.encodeElement(BigIntegers.asUnsignedByteArray(transaction.getSignature() .getR()))
                : RLP.encodeElement(EMPTY_BYTE_ARRAY);
        byte[] s = transaction.getSignature()  != null
                ? RLP.encodeElement(BigIntegers.asUnsignedByteArray(transaction.getSignature() .getS()))
                : RLP.encodeElement(EMPTY_BYTE_ARRAY);

        return ByteUtil.merge( transaction.getTypePrefix().toBytes(), RLP.encodeList(appendSignature(fields, yParity, r, s)));
    }

    /** Type 1 unsigned payload for signing: rlp([chainId, nonce, gasPrice, gasLimit, to, value, data, accessList]) */
    @Override
    public byte[] encodeForSigning(Transaction transaction) {
        return  ByteUtil.merge(transaction.getTypePrefix().toBytes(),
                RLP.encodeList(encodeUnsignedFields(transaction)));
    }

    /** Encodes the 8 shared Type 1 fields: [chainId, nonce, gasPrice, gasLimit, to, value, data, accessList] */
    protected byte[][] encodeUnsignedFields(Transaction tx) {
        return new byte[][]{
                RLP.encodeByte(tx.getChainId()),
                TransactionEncodingUtils.encodeNonce(tx.getNonce()),
                RLP.encodeCoinNonNullZero(tx.getGasPrice()),
                RLP.encodeElement(tx.getGasLimit()),
                RLP.encodeRskAddress(tx.getReceiveAddress()),
                RLP.encodeCoinNullZero(tx.getValue()),
                RLP.encodeElement(tx.getData()),
                TransactionEncodingUtils.encodeAccessList(tx.getAccessListBytes())
        };
    }

    private byte[][] appendSignature(byte[][] fields, byte[] yParity, byte[] r, byte[] s) {
        byte[][] all = new byte[fields.length + 3][];
        System.arraycopy(fields, 0, all, 0, fields.length);
        all[fields.length] = yParity;
        all[fields.length + 1] = r;
        all[fields.length + 2] = s;
        return all;
    }
}
