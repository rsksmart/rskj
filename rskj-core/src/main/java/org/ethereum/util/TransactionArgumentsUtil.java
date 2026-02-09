/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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
package org.ethereum.util;

import co.rsk.util.HexUtils;
import org.ethereum.core.Account;
import org.ethereum.core.TransactionArguments;
import org.ethereum.core.TransactionPool;
import org.ethereum.core.TransactionType;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.vm.GasCost;

import java.math.BigInteger;
import java.util.Optional;
import java.util.function.Supplier;

public class TransactionArgumentsUtil {

	private static final BigInteger DEFAULT_GAS_LIMIT = BigInteger.valueOf(GasCost.TRANSACTION_DEFAULT);

    public static final String ERR_INVALID_TX_TYPE = "Invalid transaction type: ";
	public static final String ERR_INVALID_CHAIN_ID = "Invalid chainId: ";
	public static final String ERR_COULD_NOT_FIND_ACCOUNT = "Could not find account for address: ";

    private TransactionArgumentsUtil() {}

	public static TransactionArguments processArguments(CallArguments argsParam, byte defaultChainId) {
		return processArguments(argsParam, null, null, defaultChainId);
	}

    /**
     * transform the Web3.CallArguments in TransactionArguments that can be used in
     * the TransactionBuilder
     */
	public static TransactionArguments processArguments(CallArguments argsParam, TransactionPool transactionPool, Account senderAccount, byte defaultChainId) {

		TransactionArguments argsRet = new TransactionArguments();

        argsRet.setType(TransactionType.LEGACY);

        if (argsParam.getType() != null) {
            TransactionType type = TransactionType.getByByte(Byte.parseByte(argsParam.getType()));
            if (type == null) {
                throw RskJsonRpcRequestException.invalidParamError(ERR_INVALID_TX_TYPE + Byte.parseByte(argsParam.getType()));
            }
            argsRet.setType(type);
        }

		argsRet.setFrom(argsParam.getFrom());

		argsRet.setTo(stringHexToByteArray(argsParam.getTo()));

		if(transactionPool == null || senderAccount == null) {
			argsRet.setNonce(Optional.ofNullable(argsParam.getNonce())
					.map(HexUtils::strHexOrStrNumberToBigInteger)
					.orElse(null));
		} else {
			argsRet.setNonce(strHexOrStrNumberToBigInteger(argsParam.getNonce(), () -> transactionPool.getPendingState().getNonce(senderAccount.getAddress())));
		}

		argsRet.setValue(strHexOrStrNumberToBigInteger(argsParam.getValue(), () -> BigInteger.ZERO));

		argsRet.setGasPrice(strHexOrStrNumberToBigInteger(argsParam.getGasPrice(), () -> BigInteger.ZERO));

		argsRet.setGasLimit(strHexOrStrNumberToBigInteger(argsParam.getGas(), () -> null));

		if (argsRet.getGasLimit() == null) {
			argsRet.setGasLimit(strHexOrStrNumberToBigInteger(argsParam.getGasLimit(), () -> DEFAULT_GAS_LIMIT));
		}

		if (argsParam.getData() != null && argsParam.getData().startsWith("0x")) {
			argsRet.setData(argsParam.getData().substring(2));
			argsParam.setData(argsRet.getData()); // needs to change the parameter because some places expect the changed value after sendTransaction call
		}

		argsRet.setChainId(hexToChainId(argsParam.getChainId()));
		if (argsRet.getChainId() == 0) {
			argsRet.setChainId(defaultChainId);
		}

		return argsRet;
	}

    private static byte[] stringHexToByteArray(String value) {
        return Optional.ofNullable(value).map(HexUtils::stringHexToByteArray).orElse(null);
    }

	private static BigInteger strHexOrStrNumberToBigInteger(String value, Supplier<BigInteger> getDefaultValue) {
		return Optional.ofNullable(value).map(HexUtils::strHexOrStrNumberToBigInteger).orElseGet(getDefaultValue);
	}

	private static byte hexToChainId(String hex) {
		if (hex == null) {
			return 0;
		}
		try {
			byte[] bytes = HexUtils.strHexOrStrNumberToByteArray(hex);
			if (bytes.length != 1) {
				throw RskJsonRpcRequestException.invalidParamError(ERR_INVALID_CHAIN_ID + hex);
			}

			return bytes[0];
		} catch (Exception e) {
			throw RskJsonRpcRequestException.invalidParamError(ERR_INVALID_CHAIN_ID + hex, e);
		}
	}

}
