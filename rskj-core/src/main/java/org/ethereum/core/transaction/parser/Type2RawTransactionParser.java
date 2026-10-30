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

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.ethereum.core.TransactionTypePrefix;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.core.transaction.parser.util.AccessListCodec;
import org.ethereum.core.transaction.parser.util.CommonParsingUtils;
import org.ethereum.core.transaction.parser.util.Rskip546FeeValidation;
import org.ethereum.core.transaction.parser.util.TypedEnvelopeSchema;
import org.ethereum.core.transaction.parser.util.TypedTransactionCodec;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.math.BigInteger;

import static org.ethereum.core.transaction.parser.util.TypedEnvelopeSchema.address;
import static org.ethereum.core.transaction.parser.util.TypedEnvelopeSchema.bytes;
import static org.ethereum.core.transaction.parser.util.TypedEnvelopeSchema.coinScalar;
import static org.ethereum.core.transaction.parser.util.TypedEnvelopeSchema.list;
import static org.ethereum.core.transaction.parser.util.TypedEnvelopeSchema.scalar;
import static org.ethereum.rpc.exception.RskJsonRpcRequestException.invalidParamError;

public class Type2RawTransactionParser implements RawTransactionTypeParser<ParsedType2Transaction>{

    private static final int CHAIN_ID_INDEX = 0;
    private static final int NONCE_INDEX = 1;
    private static final int MAX_PRIORITY_FEE_PER_GAS_INDEX = 2;
    private static final int MAX_FEE_PER_GAS_INDEX = 3;
    private static final int GAS_LIMIT_INDEX = 4;
    private static final int TO_INDEX = 5;
    private static final int VALUE_INDEX = 6;
    private static final int DATA_INDEX = 7;
    private static final int ACCESS_LIST_INDEX = 8;
    private static final int Y_PARITY_INDEX = 9;
    private static final int R_INDEX = 10;
    private static final int S_INDEX = 11;

    private static final TypedEnvelopeSchema SCHEMA = TypedEnvelopeSchema.of(
            TransactionType.TYPE_2.getTypeName(),
            bytes(), // chainId
            scalar(0, "Nonce is not valid", "Nonce"),
            coinScalar(3, "Gas Price is not valid", "Max priority fee per gas"),
            coinScalar(4, "Gas Price is not valid", "Max fee per gas"),
            scalar(1, "Gas Limit is not valid", "Gas Limit"),
            address(),
            coinScalar(2, "Value is not valid", "Value"),
            bytes(), // data
            list(), // access list
            bytes(), // yParity
            bytes(), // r
            bytes() // s
    );

    /** Values the semantic phase parses; a plain holder, since a record would compare the array by reference. */
    private static final class SemanticFields {
        private final byte[] accessListBytes;
        private final Coin maxPriorityFeePerGas;
        private final Coin maxFeePerGas;

        private SemanticFields(byte[] accessListBytes, Coin maxPriorityFeePerGas, Coin maxFeePerGas) {
            this.accessListBytes = accessListBytes;
            this.maxPriorityFeePerGas = maxPriorityFeePerGas;
            this.maxFeePerGas = maxFeePerGas;
        }
    }

    @Override
    public ParsedType2Transaction parse(TransactionTypePrefix typePrefix, RLPList txFields) {
        SemanticFields semantic = SCHEMA.validate(txFields, fields -> {
            byte[] accessListBytes = AccessListCodec.requireRawAccessListBytes(fields.get(ACCESS_LIST_INDEX));
            Coin maxPriorityFeePerGas = CommonParsingUtils.defaultValue(
                    RLP.parseCoinNonNullZero(fields.get(MAX_PRIORITY_FEE_PER_GAS_INDEX).getRLPData()));
            Coin maxFeePerGas = CommonParsingUtils.defaultValue(
                    RLP.parseCoinNonNullZero(fields.get(MAX_FEE_PER_GAS_INDEX).getRLPData()));
            Rskip546FeeValidation.requireFeeCapRelationship(maxPriorityFeePerGas, maxFeePerGas);
            return new SemanticFields(accessListBytes, maxPriorityFeePerGas, maxFeePerGas);
        });

        byte[] nonce = CommonParsingUtils.nullToEmpty(txFields.get(NONCE_INDEX).getRLPData());
        byte[] gasLimit = CommonParsingUtils.nullToEmpty(txFields.get(GAS_LIMIT_INDEX).getRLPData());
        RskAddress receiveAddress = CommonParsingUtils.defaultAddress(RLP.parseRskAddress(txFields.get(TO_INDEX).getRLPData()));
        Coin value = CommonParsingUtils.defaultValue(RLP.parseCoinNullZero(txFields.get(VALUE_INDEX).getRLPData()));
        byte[] data = CommonParsingUtils.nullToEmpty(txFields.get(DATA_INDEX).getRLPData());

        return new ParsedType2Transaction(
                typePrefix,
                nonce,
                gasLimit,
                receiveAddress,
                value,
                data,
                TypedTransactionCodec.parseTypedSignatureState(txFields, CHAIN_ID_INDEX, Y_PARITY_INDEX, R_INDEX, S_INDEX),
                semantic.accessListBytes,
                semantic.maxPriorityFeePerGas,
                semantic.maxFeePerGas
        );
    }

    @Override
    public ParsedType2Transaction parse(TransactionTypePrefix typePrefix, TransactionInput input, byte defaultChainId) {
        byte[] nonce = TransactionInput.resolveNonceBytes(input.nonce());
        BigInteger gasLimit = TransactionInput.resolveGasLimit(input.gasLimit());
        Coin value = CommonParsingUtils.defaultValue(input.value());
        RskAddress receiveAddress = CommonParsingUtils.defaultAddress(input.receiveAddress());
        byte[] data = CommonParsingUtils.nullToEmpty(input.data());
        byte[] accessListBytes = AccessListCodec.defaultAccessListBytes(input.accessListBytes());
        byte chainId = TransactionInput.resolveTypedChainId(input.chainId());
        Coin maxPriorityFeePerGas = parseRequiredCoin(
                input.maxPriorityFeePerGas(),
                "Type 2 transaction requires maxPriorityFeePerGas"
        );
        Coin maxFeePerGas = parseRequiredCoin(
                input.maxFeePerGas(),
                "Type 2 transaction requires maxFeePerGas"
        );

        Rskip546FeeValidation.requireFeeCapRelationship(maxPriorityFeePerGas, maxFeePerGas);
        byte[] gasLimitBytes = CommonParsingUtils.unsignedBytes(gasLimit);
        CommonParsingUtils.requireTypedScalarFields(nonce, gasLimitBytes, value, maxPriorityFeePerGas, maxFeePerGas);

        return new ParsedType2Transaction(
                typePrefix,
                nonce,
                gasLimitBytes,
                receiveAddress,
                value,
                data,
                new UnsignedSignature(chainId),
                accessListBytes,
                maxPriorityFeePerGas,
                maxFeePerGas
        );
    }

    private Coin parseRequiredCoin(Coin value, String errorMessage) {
        if (value == null) {
            throw invalidParamError(errorMessage);
        }
        return value;
    }
}
