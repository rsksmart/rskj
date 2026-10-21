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

public class Type2TransactionEncoder implements TransactionEncoder {

    /** Type 2 full encoding: 12 elements with signature */
    @Override
    public byte[] encodeSigned(Transaction transaction) {
        byte[][] fields = encodeUnsignedFields(transaction);
        byte[] yParity = transaction.getSignature() != null
                ? RLP.encodeByte((byte) (transaction.getSignature().getV() - Transaction.LOWER_REAL_V))
                : RLP.encodeByte((byte) 0);

        byte[] r = transaction.getSignature() != null
                ? RLP.encodeElement(BigIntegers.asUnsignedByteArray(transaction.getSignature().getR()))
                : RLP.encodeElement(EMPTY_BYTE_ARRAY);

        byte[] s = transaction.getSignature() != null
                ? RLP.encodeElement(BigIntegers.asUnsignedByteArray(transaction.getSignature().getS()))
                : RLP.encodeElement(EMPTY_BYTE_ARRAY);


        return ByteUtil.merge(transaction.getTypePrefix().toBytes(),
                RLP.encodeList(appendSignature(fields, yParity, r, s)));
    }

    /** Type 2 unsigned payload for signing (RSKIP-546). */
    @Override
    public byte[] encodeForSigning(Transaction transaction) {
        return  ByteUtil.merge(transaction.getTypePrefix().toBytes(),
                RLP.encodeList(encodeUnsignedFields(transaction)));
    }

    private byte[][] encodeUnsignedFields(Transaction transaction) {
        if (transaction.getMaxPriorityFeePerGas() == null || transaction.getMaxFeePerGas() == null) {
            throw new IllegalStateException("Standard Type 2 transaction requires maxPriorityFeePerGas and maxFeePerGas");
        }

        return new byte[][]{
                RLP.encodeByte(transaction.getChainId()),
                TransactionEncodingUtils.encodeNonce(transaction.getNonce()),
                RLP.encodeCoinNonNullZero(transaction.getMaxPriorityFeePerGas()),
                RLP.encodeCoinNonNullZero(transaction.getMaxFeePerGas()),
                RLP.encodeElement(transaction.getGasLimit()),
                RLP.encodeRskAddress(transaction.getReceiveAddress()),
                RLP.encodeCoinNullZero(transaction.getValue()),
                RLP.encodeElement(transaction.getData()),
                TransactionEncodingUtils.encodeAccessList(transaction.getAccessListBytes())
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
