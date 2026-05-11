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
package org.ethereum.core.transaction.parser;

import co.rsk.core.types.bytes.BytesSlice;
import org.ethereum.core.TransactionTypePrefix;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.rpc.CallArguments;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.util.function.Supplier;

public final class RawTransactionEnvelopeParser {

    private static final Type0RawTransactionParser type0Parser = new Type0RawTransactionParser();
    private static final Type1RawTransactionParser type1Parser = new Type1RawTransactionParser();
    private static final Type2RawTransactionParser type2Parser = new Type2RawTransactionParser();

    private RawTransactionEnvelopeParser() {}

    public static ParsedRawTransaction parse(byte[] rawData) {
        if (rawData == null || rawData.length == 0) {
            throw new IllegalArgumentException("Transaction raw data cannot be null or empty");
        }

        TransactionTypePrefix typePrefix = TransactionTypePrefix.fromRawData(rawData);
        BytesSlice payload = TransactionTypePrefix.stripPrefix(rawData, typePrefix);
        RLPList txFields = RLP.decodeList(payload);

        return resolveParser(typePrefix).parse(typePrefix, txFields);
    }

    public static ParsedRawTransaction parse(CallArguments argsParam, Supplier<String> nonceSupplier, byte defaultChainId) {
        if (argsParam == null ) {
            throw new IllegalArgumentException("Transaction argsParam cannot be null or empty");
        }
        if (argsParam.getNonce()==null && nonceSupplier != null) {
            argsParam.setNonce(nonceSupplier.get());
        }
        TransactionTypePrefix typePrefix = TransactionTypePrefix.fromHex(argsParam.getType(), argsParam.getRskSubtype());
        return resolveParser(typePrefix).parse(typePrefix, argsParam, defaultChainId);
    }

    private static RawTransactionTypeParser<? extends ParsedRawTransaction> resolveParser(TransactionTypePrefix typePrefix) {
        TransactionType type = typePrefix.type();
        return switch (type) {
            case LEGACY -> type0Parser;
            case TYPE_1 -> type1Parser;
            case TYPE_2 -> typePrefix.isRskNamespace() ? type0Parser : type2Parser;
            case TYPE_3, TYPE_4 -> throw new IllegalArgumentException("Unsupported transaction type: " + typePrefix);
        };
    }
}
