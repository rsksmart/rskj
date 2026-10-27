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
import org.ethereum.core.TransactionTypePrefix;
import org.ethereum.core.transaction.parser.util.CommonParsingUtils;
import org.ethereum.core.transaction.parser.util.Type0SignatureUtils;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.math.BigInteger;

public class Type0RawTransactionParser implements RawTransactionTypeParser<ParsedType0Transaction> {

    private static final int LEGACY_FIELD_COUNT = 9;

    private static final int NONCE_INDEX = 0;
    private static final int GAS_PRICE_INDEX = 1;
    private static final int GAS_LIMIT_INDEX = 2;
    private static final int RECEIVE_ADDRESS_INDEX = 3;
    private static final int VALUE_INDEX = 4;
    private static final int DATA_INDEX = 5;
    private static final int V_INDEX = 6;
    private static final int R_INDEX = 7;
    private static final int S_INDEX = 8;

    @Override
    public ParsedType0Transaction parse(TransactionTypePrefix typePrefix, RLPList txFields) {
        CommonParsingUtils.requireFieldCount(txFields, LEGACY_FIELD_COUNT, "Legacy-format");

        byte[] nonce = CommonParsingUtils.nullToEmpty(txFields.get(NONCE_INDEX).getRLPData());
        Coin gasPrice = CommonParsingUtils.defaultValue(RLP.parseCoinNonNullZero(txFields.get(GAS_PRICE_INDEX).getRLPData()));
        byte[] gasLimit = CommonParsingUtils.nullToEmpty(txFields.get(GAS_LIMIT_INDEX).getRLPData());
        RskAddress receiveAddress = CommonParsingUtils.defaultAddress(
                RLP.parseRskAddress(txFields.get(RECEIVE_ADDRESS_INDEX).getRLPData()));
        Coin value = CommonParsingUtils.defaultValue(RLP.parseCoinNullZero(txFields.get(VALUE_INDEX).getRLPData()));
        byte[] data = CommonParsingUtils.nullToEmpty(txFields.get(DATA_INDEX).getRLPData());
        CommonParsingUtils.requireLegacyScalarFields(nonce, gasPrice, gasLimit, value);

        return new ParsedType0Transaction(
                typePrefix,
                nonce,
                gasPrice,
                gasLimit,
                receiveAddress,
                value,
                data,
                Type0SignatureUtils.parseType0SignatureState(txFields, V_INDEX, R_INDEX, S_INDEX)
        );
    }

    @Override
    public void validate(long bestBlock, ActivationConfig activationConfig, Constants constants) {


    }

    @Override
    public ParsedType0Transaction parse(TransactionTypePrefix typePrefix, TransactionInput input, byte defaultChainId) {
        byte[] nonce = TransactionInput.resolveNonceBytes(input.nonce(), false);
        BigInteger gasLimit = TransactionInput.resolveGasLimit(input.gasLimit());
        Coin gasPrice = CommonParsingUtils.defaultValue(input.gasPrice());
        Coin value = CommonParsingUtils.defaultValue(input.value());
        RskAddress receiveAddress = CommonParsingUtils.defaultAddress(input.receiveAddress());
        byte[] data = CommonParsingUtils.nullToEmpty(input.data());
        byte chainId = TransactionInput.resolveLegacyChainId(input.chainId(), defaultChainId);
        byte[] gasLimitBytes = CommonParsingUtils.unsignedBytes(gasLimit);
        CommonParsingUtils.requireLegacyScalarFields(nonce, gasPrice, gasLimitBytes, value);

        return new ParsedType0Transaction(
                typePrefix,
                nonce,
                gasPrice,
                gasLimitBytes,
                receiveAddress,
                value,
                data,
                new UnsignedSignature(chainId)
        );
    }
}
