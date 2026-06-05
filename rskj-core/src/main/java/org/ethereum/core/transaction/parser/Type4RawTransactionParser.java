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
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.TransactionTypePrefix;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.core.transaction.parser.util.AccessListCodec;
import org.ethereum.core.transaction.parser.util.AuthorizationListCodec;
import org.ethereum.core.transaction.parser.util.CommonParsingUtils;
import org.ethereum.core.transaction.parser.util.Type4TransactionValidation;
import org.ethereum.core.transaction.parser.util.TypedTransactionCodec;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.math.BigInteger;
import java.util.Objects;

import static org.ethereum.rpc.exception.RskJsonRpcRequestException.invalidParamError;

public class Type4RawTransactionParser implements RawTransactionTypeParser<ParsedType4Transaction>{

    private static final int FIELD_COUNT = 13;
    private static final int CHAIN_ID_INDEX = 0;
    private static final int NONCE_INDEX = 1;
    private static final int MAX_PRIORITY_FEE_PER_GAS_INDEX = 2;
    private static final int MAX_FEE_PER_GAS_INDEX = 3;
    private static final int GAS_LIMIT_INDEX = 4;
    private static final int TO_INDEX = 5;
    private static final int VALUE_INDEX = 6;
    private static final int DATA_INDEX = 7;
    private static final int ACCESS_LIST_INDEX = 8;
    private static final int AUTHORIZATION_LIST_INDEX = 9;
    private static final int Y_PARITY_INDEX = 10;
    private static final int R_INDEX = 11;
    private static final int S_INDEX = 12;

    @Override
    public ParsedType4Transaction parse(TransactionTypePrefix typePrefix, RLPList txFields) {
        CommonParsingUtils.requireFieldCount(txFields, FIELD_COUNT, TransactionType.TYPE_4.getTypeName());
        // nonce
        byte[] nonce = CommonParsingUtils.nullToEmpty(txFields.get(NONCE_INDEX).getRLPData());
        // gasLimit
        byte[] gasLimit = CommonParsingUtils.nullToEmpty(txFields.get(GAS_LIMIT_INDEX).getRLPData());
        // to
        RskAddress receiveAddress = CommonParsingUtils.defaultAddress(RLP.parseRskAddress(txFields.get(TO_INDEX).getRLPData()));
        if (receiveAddress.equals(RskAddress.nullAddress())) {
            throw new IllegalArgumentException("Set-code transaction must have a non-null destination");
        }
        // value
        Coin value = CommonParsingUtils.defaultValue(RLP.parseCoinNullZero(txFields.get(VALUE_INDEX).getRLPData()));
        // data
        byte[] data = CommonParsingUtils.nullToEmpty(txFields.get(DATA_INDEX).getRLPData());
        // access list
        byte[] accessListBytes = AccessListCodec.defaultAccessListBytes(txFields.get(ACCESS_LIST_INDEX).getRLPRawData());
        // authorization list
        byte[] authorizationListBytes = AuthorizationListCodec.requireAuthorizationListBytes(
                txFields.get(AUTHORIZATION_LIST_INDEX).getRLPRawData());
        var authorizationList = AuthorizationListCodec.decodeListUnchecked(authorizationListBytes);
        // max priority fee per gas and max fee per gas
        Coin maxPriorityFeePerGas = Objects.requireNonNull(RLP.parseCoinNonNullZero(txFields.get(MAX_PRIORITY_FEE_PER_GAS_INDEX).getRLPData()), "Type 4 maxPriorityFeePerGas");
        Coin maxFeePerGas = Objects.requireNonNull(RLP.parseCoinNonNullZero(txFields.get(MAX_FEE_PER_GAS_INDEX).getRLPData()), "Type 4 maxFeePerGas");
        validateFeeCapRelationship(maxPriorityFeePerGas, maxFeePerGas);

        ParsedType4Transaction parsed = new ParsedType4Transaction(
                typePrefix,
                nonce,
                gasLimit,
                receiveAddress,
                value,
                data,
                TypedTransactionCodec.parseTypedSignatureState(txFields, CHAIN_ID_INDEX, Y_PARITY_INDEX, R_INDEX, S_INDEX),
                accessListBytes,
                maxPriorityFeePerGas,
                maxFeePerGas,
                authorizationList
        );
        Type4TransactionValidation.validateParsed(parsed);
        return parsed;
    }

    @Override
    public void validate(long bestBlock, ActivationConfig activationConfig, Constants constants) {
        ActivationConfig.ForBlock activations = activationConfig.forBlock(bestBlock);
        if (!activations.isActive(ConsensusRule.RSKIP543)) {
            throw invalidParamError("Typed transactions (type " + TransactionType.TYPE_4 + ") is not supported before RSKIP-543 activation");
        }
        if (!activations.isActive(ConsensusRule.RSKIP546)) {
            throw invalidParamError("Type 4 transactions are not supported before RSKIP-546 activation");
        }
        if (!activations.isActive(ConsensusRule.RSKIP545)) {
            throw invalidParamError("Type 4 (set-code) transactions are not supported before RSKIP-545 activation");
        }
    }

    @Override
    public ParsedType4Transaction parse(TransactionTypePrefix typePrefix, TransactionInput input, byte defaultChainId) {
        byte[] nonce = TransactionInput.resolveNonceBytes(input.nonce(), true);
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
        validateFeeCapRelationship(maxPriorityFeePerGas, maxFeePerGas);

        var authorizationList = input.authorizationList();
        if (authorizationList == null || authorizationList.isEmpty()) {
            throw invalidParamError("Set-code transaction authorization_list must not be empty");
        }

        ParsedType4Transaction parsed = new ParsedType4Transaction(
                typePrefix,
                nonce,
                gasLimit.toByteArray(),
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

    private void validateFeeCapRelationship(Coin maxPriorityFeePerGas, Coin maxFeePerGas) {
        if (maxPriorityFeePerGas.compareTo(maxFeePerGas) > 0) {
            throw new IllegalArgumentException(
                    "Type 4 transaction maxPriorityFeePerGas (" + maxPriorityFeePerGas
                            + ") must not exceed maxFeePerGas (" + maxFeePerGas + ")"
            );
        }
    }

    private Coin parseRequiredCoin(Coin value, String errorMessage) {
        if (value == null) {
            throw invalidParamError(errorMessage);
        }
        return value;
    }
}
