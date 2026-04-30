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

import co.rsk.core.Coin;
import org.ethereum.config.Constants;
import org.ethereum.core.Transaction;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.core.TransactionTypePrefix;
import org.ethereum.core.transaction.parser.ParsedRawTransaction;
import org.ethereum.core.transaction.parser.RawTransactionEnvelopeParser;
import org.ethereum.core.transaction.parser.util.TransactionTypeRpcParser;
import org.ethereum.core.transaction.temp.ParsedRawTransactionAdapter;
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
		Wallet wallet = new Wallet(new HashMapDB());
		RskAddress sender = wallet.addAccount();
		RskAddress receiver = wallet.addAccount();

		CallArguments args = TransactionFactoryHelper.createArguments(sender, receiver);

		ParsedRawTransaction parsedTransaction = RawTransactionEnvelopeParser.parse(args, null, (byte) 33);
		ParsedRawTransactionAdapter adapter = new ParsedRawTransactionAdapter(parsedTransaction);
		assertEquals(adapter.value().asBigInteger(), BigInteger.valueOf(100000L));
		assertEquals(adapter.effectiveGasPrice().asBigInteger(), BigInteger.valueOf(10000000000000L));
		assertEquals( new BigInteger(1, adapter.gasLimit()), BigInteger.valueOf(30400L));
		assertEquals(33, adapter.chainId());
		assertArrayEquals(new byte[] { 0x01 }, adapter.nonce());
		assertArrayEquals(new byte[]{}, adapter.data());
		assertArrayEquals(adapter.receiveAddress().getBytes(), receiver.getBytes());

	}

	@Test
	void hexToTransactionType_nullReturnsLegacy() {
		assertEquals(TransactionType.LEGACY, TransactionTypeRpcParser.fromHex(null));
	}

	@Test
	void hexToTransactionType_explicitZero_isRejected() {
		RskJsonRpcRequestException ex = assertThrows(
				RskJsonRpcRequestException.class,
				() -> TransactionTypeRpcParser.fromHex("0"));
		assertTrue(ex.getMessage().contains("explicit type 0x00 is not allowed"),
				"Error should mention explicit 0x00 is not allowed, got: " + ex.getMessage());
	}

	@ParameterizedTest
	@ValueSource(strings = {"1", "2", "3", "4", "0x1", "0x2", "0x3", "0x4", "0x01", "0x02", "0x03", "0x04"})
	void hexToTransactionType_validTypes_areAccepted(String hex) {
		TransactionType type = TransactionTypeRpcParser.fromHex(hex);

		assertNotNull(type);
		assertNotEquals(TransactionType.LEGACY, type);
	}

	@ParameterizedTest
	@ValueSource(strings = {"5", "6", "10", "127", "0x5", "0x0a", "0x7f"})
	void hexToTransactionType_unknownTypes_areRejected(String hex) {
		assertThrows(RskJsonRpcRequestException.class,
				() -> TransactionTypeRpcParser.fromHex(hex));
	}

	@ParameterizedTest
	@ValueSource(strings = {"0x0", "0x00", "0"})
	void hexToTransactionType_explicitZeroHex_isRejected(String hex) {
		RskJsonRpcRequestException ex = assertThrows(
				RskJsonRpcRequestException.class,
				() -> TransactionTypeRpcParser.fromHex(hex));
		assertTrue(ex.getMessage().contains("explicit type 0x00 is not allowed"),
				"Error should mention explicit 0x00 is not allowed, got: " + ex.getMessage());
	}

	@ParameterizedTest
	@ValueSource(strings = {"abc", "0xff", "", "0x80", "-1"})
	void hexToTransactionType_invalidStrings_areRejected(String hex) {
		assertThrows(RskJsonRpcRequestException.class,
				() -> TransactionTypeRpcParser.fromHex(hex));
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
				() -> RawTransactionEnvelopeParser.parse(args, null, (byte) 33));
	}

	@Test
	void processArguments_withHexType_isAccepted() {
		CallArguments args = new CallArguments();
		args.setFrom("0x0000000000000000000000000000000000000001");
		args.setTo("0x0000000000000000000000000000000000000002");
		args.setGas("0x5208");
		args.setGasPrice("0x1");
		args.setValue("0x0");
		args.setType("0x1");
		args.setChainId("0x21");

		ParsedRawTransaction parsedTransaction = RawTransactionEnvelopeParser.parse(args, null, (byte) 33);
		ParsedRawTransactionAdapter adapter = new ParsedRawTransactionAdapter(parsedTransaction);
		assertEquals(TransactionType.TYPE_1, adapter.typePrefix().type());
		assertFalse(adapter.typePrefix().isRskNamespace());
	}

	@Test
	void hexToRskSubtype_nullReturnsNull() {
		assertNull(TransactionTypePrefix.hexToRskSubtype(null));
	}

	@ParameterizedTest
	@ValueSource(strings = {"0", "1", "127", "0x0", "0x1", "0x7f", "0x00", "0x01"})
	void hexToRskSubtype_validValues_areAccepted(String hex) {
		Byte result = TransactionTypePrefix.hexToRskSubtype(hex);
		assertNotNull(result);
	}

	@ParameterizedTest
	@ValueSource(strings = {"128", "0x80", "0xff", "-1", "abc", ""})
	void hexToRskSubtype_invalidValues_areRejected(String hex) {
		assertThrows(RskJsonRpcRequestException.class,
				() -> TransactionTypePrefix.hexToRskSubtype(hex));
	}

	@Test
	void processArguments_withRskSubtype_isThreaded() {
		CallArguments args = new CallArguments();
		args.setFrom("0x0000000000000000000000000000000000000001");
		args.setTo("0x0000000000000000000000000000000000000002");
		args.setGas("0x5208");
		args.setGasPrice("0x1");
		args.setValue("0x0");
		args.setType("0x2");
		args.setRskSubtype("0x3");


		assertThrows(NullPointerException.class, () -> RawTransactionEnvelopeParser.parse(args, null, (byte) 33));
		//assertEquals(TransactionType.TYPE_2, parsedTransaction.typePrefix().type());
		//assertEquals((byte) 0x03, parsedTransaction.typePrefix().subtype());
	}

	@Test
	void processArguments_type2Standard_includesMaxPriorityAndMaxFeeFromRequest() {
		CallArguments args = new CallArguments();
		args.setFrom("0x0000000000000000000000000000000000000001");
		args.setTo("0x0000000000000000000000000000000000000002");
		args.setGas("0x5208");
		args.setGasPrice("0x0");
		args.setMaxPriorityFeePerGas("0xa");
		args.setMaxFeePerGas("0x64");
		args.setValue("0x0");
		args.setNonce("0x1");
		args.setType("0x2");
		args.setChainId("0x21");

		ParsedRawTransaction parsedTransaction = RawTransactionEnvelopeParser.parse(args, null, (byte) 33);
		ParsedRawTransactionAdapter adapter = new ParsedRawTransactionAdapter(parsedTransaction);
		assertEquals(TransactionType.TYPE_2, adapter.typePrefix().type());
		assertFalse(adapter.typePrefix().isRskNamespace());
		assertEquals(Coin.valueOf(10), adapter.maxPriorityFeePerGas());
		assertEquals(Coin.valueOf(100), adapter.maxFeePerGas());
		assertEquals(Coin.valueOf(10), adapter.effectiveGasPrice());
	}

	@Test
	void processArguments_type2Standard_missingBothMaxFees_isRejected() {
		CallArguments args = baseType2Args();
		args.setGasPrice("0x7d0");

		RskJsonRpcRequestException ex = assertThrows(RskJsonRpcRequestException.class,
				() ->  RawTransactionEnvelopeParser.parse(args, null, Constants.regtest().getChainId()));
		assertTrue(ex.getMessage().contains("Type 0x02"),
				"Error must mention Type 0x02 context, got: " + ex.getMessage());
	}

	@Test
	void processArguments_type2Standard_missingOnlyMaxPriorityFee_isRejected() {
		CallArguments args = baseType2Args();
		args.setMaxFeePerGas("0x64");

		assertThrows(RskJsonRpcRequestException.class,
				() ->  RawTransactionEnvelopeParser.parse(args, null, Constants.regtest().getChainId()));
	}

	@Test
	void processArguments_type2Standard_missingOnlyMaxFee_isRejected() {
		CallArguments args = baseType2Args();
		args.setMaxPriorityFeePerGas("0x5");

		assertThrows(RskJsonRpcRequestException.class,
				() -> RawTransactionEnvelopeParser.parse(args, null, Constants.regtest().getChainId()));
	}

	@Test
	void processArguments_type2RskNamespace_doesNotRequireMaxFees() {
		CallArguments args = baseType2Args();
		args.setGasPrice("0x1");
		args.setRskSubtype("0x3");


		ParsedRawTransaction parsedTransaction = RawTransactionEnvelopeParser.parse(args, null, Constants.regtest().getChainId());
		ParsedRawTransactionAdapter adapter = new ParsedRawTransactionAdapter(parsedTransaction);
		assertEquals(TransactionType.TYPE_2, adapter.typePrefix().type());
		assertTrue(adapter.typePrefix().isRskNamespace());
	}

	private static CallArguments baseType2Args() {
		CallArguments args = new CallArguments();
		args.setFrom("0x0000000000000000000000000000000000000001");
		args.setTo("0x0000000000000000000000000000000000000002");
		args.setGas("0x5208");
		args.setValue("0x0");
		args.setNonce("0x1");
		args.setType("0x2");
		args.setChainId("0x21");
		return args;
	}

	@ParameterizedTest
	@ValueSource(strings = {"0x0", "0x00", "0"})
	void processArguments_chainIdZero_fallsThroughToDefault(String chainIdHex) {
		CallArguments args = new CallArguments();
		args.setFrom("0x0000000000000000000000000000000000000001");
		args.setTo("0x0000000000000000000000000000000000000002");
		args.setGas("0x5208");
		args.setGasPrice("0x1");
		args.setValue("0x0");
		args.setChainId(chainIdHex);


		ParsedRawTransaction parsedTransaction = RawTransactionEnvelopeParser.parse(args, null, Constants.regtest().getChainId());
		ParsedRawTransactionAdapter adapter = new ParsedRawTransactionAdapter(parsedTransaction);

		assertEquals(Constants.regtest().getChainId(), adapter.chainId(),
				"chainId=" + chainIdHex + " must fall through to the default chainId");
	}

}
