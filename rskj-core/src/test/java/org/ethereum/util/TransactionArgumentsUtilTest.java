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

import java.math.BigInteger;

import org.ethereum.config.Constants;
import org.ethereum.core.TransactionArguments;
import org.ethereum.core.TransactionType;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import co.rsk.core.RskAddress;
import co.rsk.core.Wallet;

import static org.junit.jupiter.api.Assertions.*;

class TransactionArgumentsUtilTest {

	@Test
	void processArguments() {

		Constants constants = Constants.regtest();

		Wallet wallet = new Wallet(new HashMapDB());
		RskAddress sender = wallet.addAccount();
		RskAddress receiver = wallet.addAccount();

		CallArguments args = TransactionFactoryHelper.createArguments(sender, receiver);

		TransactionArguments txArgs = TransactionArgumentsUtil.processArguments(args, null, wallet.getAccount(sender), constants.getChainId());

		assertEquals(txArgs.getValue(), BigInteger.valueOf(100000L));
		assertEquals(txArgs.getGasPrice(), BigInteger.valueOf(10000000000000L));
		assertEquals(txArgs.getGasLimit(), BigInteger.valueOf(30400L));
		assertEquals(33, txArgs.getChainId());
		assertEquals(BigInteger.ONE, txArgs.getNonce());
		assertNull(txArgs.getData());
		assertArrayEquals(txArgs.getTo(), receiver.getBytes());

	}

	@Test
	void hexToTransactionType_nullReturnsLegacy() {
		assertEquals(TransactionType.LEGACY,
				TransactionArgumentsUtil.hexToTransactionType(null));
	}

	@Test
	void hexToTransactionType_explicitZero_isRejected() {
		RskJsonRpcRequestException ex = assertThrows(
				RskJsonRpcRequestException.class,
				() -> TransactionArgumentsUtil.hexToTransactionType("0"));
		assertTrue(ex.getMessage().contains("explicit type 0x00 is not allowed"),
				"Error should mention explicit 0x00 is not allowed, got: " + ex.getMessage());
	}

	@ParameterizedTest
	@ValueSource(strings = {"1", "2", "3", "4"})
	void hexToTransactionType_validTypes_areAccepted(String hex) {
		TransactionType type = TransactionArgumentsUtil.hexToTransactionType(hex);

		assertNotNull(type);
		assertNotEquals(TransactionType.LEGACY, type);
	}

	@ParameterizedTest
	@ValueSource(strings = {"5", "6", "10", "127"})
	void hexToTransactionType_unknownTypes_areRejected(String hex) {
		assertThrows(RskJsonRpcRequestException.class,
				() -> TransactionArgumentsUtil.hexToTransactionType(hex));
	}

	@ParameterizedTest
	@ValueSource(strings = {"0x0", "0x1", "abc", "0xff", ""})
	void hexToTransactionType_unparseableStrings_areRejected(String hex) {
		assertThrows(RskJsonRpcRequestException.class,
				() -> TransactionArgumentsUtil.hexToTransactionType(hex));
	}

	@Test
	void processArguments_withExplicitType0x00_isRejected() {
		CallArguments args = new CallArguments();
		args.setFrom("0x0000000000000000000000000000000000000001");
		args.setTo("0x0000000000000000000000000000000000000002");
		args.setGas("0x5208");
		args.setGasPrice("0x1");
		args.setValue("0x0");
		args.setType("0");

		assertThrows(RskJsonRpcRequestException.class,
				() -> TransactionArgumentsUtil.processArguments(args, Constants.regtest().getChainId()));
	}

}
