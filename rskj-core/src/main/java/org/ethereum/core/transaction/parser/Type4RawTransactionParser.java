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
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.core.transaction.parser.util.AccessListCodec;
import org.ethereum.core.transaction.parser.util.AuthorizationListCodec;
import org.ethereum.core.transaction.parser.util.CommonParsingUtils;
import org.ethereum.core.transaction.parser.util.Rskip546FeeValidation;
import org.ethereum.core.transaction.parser.util.Type4TransactionValidation;
import org.ethereum.core.transaction.parser.util.TypedEnvelopeSchema;
import org.ethereum.core.transaction.parser.util.TypedEnvelopeSchema.ListField;
import org.ethereum.core.transaction.parser.util.TypedEnvelopeSchema.ValidatedEnvelope;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.math.BigInteger;
import java.util.List;

import static org.ethereum.core.transaction.parser.util.TypedEnvelopeSchema.bytes;
import static org.ethereum.core.transaction.parser.util.TypedEnvelopeSchema.chainId;
import static org.ethereum.core.transaction.parser.util.TypedEnvelopeSchema.coinScalar;
import static org.ethereum.core.transaction.parser.util.TypedEnvelopeSchema.list;
import static org.ethereum.core.transaction.parser.util.TypedEnvelopeSchema.nonNullAddress;
import static org.ethereum.core.transaction.parser.util.TypedEnvelopeSchema.scalar;
import static org.ethereum.core.transaction.parser.util.TypedEnvelopeSchema.signatureR;
import static org.ethereum.core.transaction.parser.util.TypedEnvelopeSchema.signatureS;
import static org.ethereum.core.transaction.parser.util.TypedEnvelopeSchema.yParity;
import static org.ethereum.rpc.exception.RskJsonRpcRequestException.invalidParamError;

public class Type4RawTransactionParser implements RawTransactionTypeParser<ParsedType4Transaction>{

    private static final int NONCE_INDEX = 1;
    private static final int MAX_PRIORITY_FEE_PER_GAS_INDEX = 2;
    private static final int MAX_FEE_PER_GAS_INDEX = 3;
    private static final int GAS_LIMIT_INDEX = 4;
    private static final int TO_INDEX = 5;
    private static final int VALUE_INDEX = 6;
    private static final int DATA_INDEX = 7;

    private static final ListField<byte[]> ACCESS_LIST = list(AccessListCodec::requireRawAccessListBytes);
    private static final ListField<List<SetCodeAuthorization>> AUTHORIZATION_LIST =
            list(AuthorizationListCodec::requireRawAuthorizationList);

    private static final TypedEnvelopeSchema SCHEMA = TypedEnvelopeSchema.of(
            TransactionType.TYPE_4.getTypeName(),
            chainId(),
            scalar(0, "Nonce is not valid", "Nonce"),
            coinScalar(3, "Gas Price is not valid", "Max priority fee per gas"),
            coinScalar(4, "Gas Price is not valid", "Max fee per gas"),
            scalar(1, "Gas Limit is not valid", "Gas Limit"),
            nonNullAddress("Set-code transaction must have a non-null destination"),
            coinScalar(2, "Value is not valid", "Value"),
            bytes(), // data
            ACCESS_LIST,
            AUTHORIZATION_LIST,
            yParity(),
            signatureR(),
            signatureS()
    );

    @Override
    public ParsedType4Transaction parse(TransactionTypePrefix typePrefix, RLPList txFields) {
        ValidatedEnvelope envelope = SCHEMA.validate(txFields, fields -> Rskip546FeeValidation.requireFeeCapRelationship(
                fee(fields, MAX_PRIORITY_FEE_PER_GAS_INDEX), fee(fields, MAX_FEE_PER_GAS_INDEX)));

        byte[] nonce = CommonParsingUtils.nullToEmpty(txFields.get(NONCE_INDEX).getRLPData());
        byte[] gasLimit = CommonParsingUtils.nullToEmpty(txFields.get(GAS_LIMIT_INDEX).getRLPData());
        RskAddress receiveAddress = CommonParsingUtils.defaultAddress(RLP.parseRskAddress(txFields.get(TO_INDEX).getRLPData()));
        Coin value = CommonParsingUtils.defaultValue(RLP.parseCoinNullZero(txFields.get(VALUE_INDEX).getRLPData()));
        byte[] data = CommonParsingUtils.nullToEmpty(txFields.get(DATA_INDEX).getRLPData());

        ParsedType4Transaction parsed = new ParsedType4Transaction(
                typePrefix,
                nonce,
                gasLimit,
                receiveAddress,
                value,
                data,
                envelope.signature(),
                envelope.get(ACCESS_LIST),
                fee(txFields, MAX_PRIORITY_FEE_PER_GAS_INDEX),
                fee(txFields, MAX_FEE_PER_GAS_INDEX),
                envelope.get(AUTHORIZATION_LIST)
        );
        Type4TransactionValidation.validateParsed(parsed);
        return parsed;
    }

    @Override
    public ParsedType4Transaction parse(TransactionTypePrefix typePrefix, TransactionInput input, byte defaultChainId) {
        byte[] nonce = TransactionInput.resolveNonceBytes(input.nonce());
        BigInteger gasLimit = TransactionInput.resolveGasLimit(input.gasLimit());
        Coin value = CommonParsingUtils.defaultValue(input.value());
        RskAddress receiveAddress = CommonParsingUtils.defaultAddress(input.receiveAddress());
        if (receiveAddress.equals(RskAddress.nullAddress())) {
            throw invalidParamError("Set-code transaction requires a non-null destination");
        }
        byte[] data = CommonParsingUtils.nullToEmpty(input.data());
        byte[] accessListBytes = AccessListCodec.defaultAccessListBytes(input.accessListBytes());
        byte chainId = TransactionInput.resolveTypedChainId(input.chainId());
        Coin maxPriorityFeePerGas = parseRequiredCoin(
                input.maxPriorityFeePerGas(),
                "Type 4 transaction requires maxPriorityFeePerGas"
        );
        Coin maxFeePerGas = parseRequiredCoin(
                input.maxFeePerGas(),
                "Type 4 transaction requires maxFeePerGas"
        );
        Rskip546FeeValidation.requireFeeCapRelationship(maxPriorityFeePerGas, maxFeePerGas);
        byte[] gasLimitBytes = CommonParsingUtils.unsignedBytes(gasLimit);
        CommonParsingUtils.requireTypedScalarFields(nonce, gasLimitBytes, value, maxPriorityFeePerGas, maxFeePerGas);

        var authorizationList = input.authorizationList();
        if (authorizationList == null || authorizationList.isEmpty()) {
            throw invalidParamError("Set-code transaction authorization_list must not be empty");
        }

        ParsedType4Transaction parsed = new ParsedType4Transaction(
                typePrefix,
                nonce,
                gasLimitBytes,
                receiveAddress,
                value,
                data,
                new UnsignedSignature(chainId),
                accessListBytes,
                maxPriorityFeePerGas,
                maxFeePerGas,
                authorizationList
        );
        Type4TransactionValidation.validateParsed(parsed);
        return parsed;
    }

    /** A fee field; zero-length is zero, as in the envelope's canonical encoding. */
    private static Coin fee(RLPList txFields, int index) {
        return CommonParsingUtils.defaultValue(RLP.parseCoinNonNullZero(txFields.get(index).getRLPData()));
    }

    private Coin parseRequiredCoin(Coin value, String errorMessage) {
        if (value == null) {
            throw invalidParamError(errorMessage);
        }
        return value;
    }
}
