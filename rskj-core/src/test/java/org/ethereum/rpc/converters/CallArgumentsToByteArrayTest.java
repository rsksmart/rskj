/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package org.ethereum.rpc.converters;

import org.ethereum.core.Rskip545TestSupport;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.util.ByteUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import co.rsk.config.TestSystemProperties;
import co.rsk.util.HexUtils;

/**
 * Created by martin.medina on 3/7/17.
 */
class CallArgumentsToByteArrayTest {

    private TestSystemProperties config = new TestSystemProperties();

    @Test
    void getGasPriceWhenValueIsNull() throws Exception {
        CallArguments args = new CallArguments();

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        Assertions.assertArrayEquals(new byte[] {0}, byteArrayArgs.getGasPrice());
    }

    @Test
    void getGasPriceWhenValueIsEmpty() throws Exception {
        CallArguments args = new CallArguments();
        args.setGasPrice("");

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        Assertions.assertArrayEquals(new byte[] {0}, byteArrayArgs.getGasPrice());
    }

    @Test
    void getGasLimitWhenValueIsNull() throws Exception {
        CallArguments args = new CallArguments();

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        String maxGasLimit = "0x5AF3107A4000";
        byte[] expectedGasLimit = HexUtils.stringHexToByteArray(maxGasLimit);
        Assertions.assertArrayEquals(expectedGasLimit, byteArrayArgs.getGasLimit());
    }

    @Test
    void getGasLimitWhenValueIsEmpty() throws Exception {
        CallArguments args = new CallArguments();
        args.setGas("");

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        String maxGasLimit = "0x5AF3107A4000";
        byte[] expectedGasLimit = HexUtils.stringHexToByteArray(maxGasLimit);
        Assertions.assertArrayEquals(expectedGasLimit, byteArrayArgs.getGasLimit());
    }

    @Test
    void getToAddressWhenValueIsNull() throws Exception {
        CallArguments args = new CallArguments();

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        Assertions.assertNull(byteArrayArgs.getToAddress());
    }

    @Test
    void getValueWhenValueIsNull() throws Exception {
        CallArguments args = new CallArguments();

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        Assertions.assertArrayEquals(new byte[] {0}, byteArrayArgs.getValue());
    }

    @Test
    void getValueWhenValueIsEmpty() throws Exception {
        CallArguments args = new CallArguments();
        args.setValue("");

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        Assertions.assertArrayEquals(new byte[] {0}, byteArrayArgs.getValue());
    }

    @Test
    void getDataWhenValueIsNull() throws Exception {
        CallArguments args = new CallArguments();

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        Assertions.assertNull(byteArrayArgs.getData());
    }

    @Test
    void getDataWhenValueIsEmpty() throws Exception {
        CallArguments args = new CallArguments();
        args.setData("");

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        Assertions.assertNull(byteArrayArgs.getData());
    }

    @Test
    void gasLimitForGasExceedingGasCap() {
        long hugeAmountOfGas = 900000000000000L;
        long callGasCap = config.getCallGasCap();

        CallArguments callArguments = new CallArguments();
        callArguments.setGas(HexUtils.toQuantityJsonHex(hugeAmountOfGas));

        Assertions.assertEquals(hugeAmountOfGas, Long.decode(callArguments.getGas()).longValue());

        CallArgumentsToByteArray callArgumentsToByteArray = new CallArgumentsToByteArray(callArguments);

        Assertions.assertEquals(hugeAmountOfGas, ByteUtil.byteArrayToLong(callArgumentsToByteArray.getGasLimit()));
        Assertions.assertEquals(callGasCap, ByteUtil.byteArrayToLong(callArgumentsToByteArray.gasLimitForCall(callGasCap)));
    }

    @Test
    void gasLimitForGasBelowGasCap() {
        CallArguments callArguments = new CallArguments();
        callArguments.setGas(HexUtils.toQuantityJsonHex(1));

        CallArgumentsToByteArray callArgumentsToByteArray = new CallArgumentsToByteArray(callArguments);

        Assertions.assertEquals(1, ByteUtil.byteArrayToLong(
                callArgumentsToByteArray.gasLimitForCall(config.getCallGasCap())));
    }

    @Test
    void getGasPrice_whenMaxFeeHasHighBitSet_returnsUnsignedEncoding() {
        CallArguments args = new CallArguments();
        args.setMaxFeePerGas("0xff");
        args.setMaxPriorityFeePerGas("0x80"); // 128 — high bit set; valid tip <= fee cap

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        Assertions.assertArrayEquals(new byte[] {(byte) 0x80}, byteArrayArgs.getGasPrice(),
                "effective gas price must be encoded as unsigned bytes; no 0x00 sign byte prefix");
    }

    @Test
    void getGasPrice_whenPriorityIsLowerAndHasHighBitSet_returnsUnsignedEncoding() {
        CallArguments args = new CallArguments();
        args.setMaxFeePerGas("0xff");
        args.setMaxPriorityFeePerGas("0x81");

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        Assertions.assertArrayEquals(new byte[] {(byte) 0x81}, byteArrayArgs.getGasPrice(),
                "priority fee selected and must be unsigned");
    }

    @Test
    void getGasPrice_whenOnlyMaxFeeIsSet_rejectsRequest() {
        CallArguments args = new CallArguments();
        args.setMaxFeePerGas("0x64");

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        RskJsonRpcRequestException ex = Assertions.assertThrows(
                RskJsonRpcRequestException.class,
                byteArrayArgs::getGasPrice);
        Assertions.assertEquals(-32602, ex.getCode());
        Assertions.assertEquals("maxFeePerGas requires maxPriorityFeePerGas", ex.getMessage());
    }

    @Test
    void getGasPrice_whenOnlyMaxPriorityFeeIsSet_rejectsRequest() {
        CallArguments args = new CallArguments();
        args.setMaxPriorityFeePerGas("0x64");

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        RskJsonRpcRequestException ex = Assertions.assertThrows(
                RskJsonRpcRequestException.class,
                byteArrayArgs::getGasPrice);
        Assertions.assertEquals(-32602, ex.getCode());
        Assertions.assertEquals("maxPriorityFeePerGas requires maxFeePerGas", ex.getMessage());
    }

    @Test
    void getGasPrice_whenMaxPriorityFeeSetAndMaxFeeEmpty_rejectsRequest() {
        CallArguments args = new CallArguments();
        args.setMaxPriorityFeePerGas("0x64");
        args.setMaxFeePerGas("");

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        RskJsonRpcRequestException ex = Assertions.assertThrows(
                RskJsonRpcRequestException.class,
                byteArrayArgs::getGasPrice);
        Assertions.assertEquals(-32602, ex.getCode());
        Assertions.assertEquals("maxPriorityFeePerGas requires maxFeePerGas", ex.getMessage());
    }

    @Test
    void getGasPrice_whenMaxPriorityFeeExceedsMaxFee_rejectsRequest() {
        CallArguments args = new CallArguments();
        args.setMaxFeePerGas("0x1");
        args.setMaxPriorityFeePerGas("0x64");

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        RskJsonRpcRequestException ex = Assertions.assertThrows(
                RskJsonRpcRequestException.class,
                byteArrayArgs::getGasPrice);
        Assertions.assertEquals(-32602, ex.getCode());
        Assertions.assertEquals(
                "maxPriorityFeePerGas (100) must not exceed maxFeePerGas (1)",
                ex.getMessage());
    }

    @Test
    void getGasPrice_whenMaxPriorityFeeEqualsMaxFee_usesPriorityFee() {
        CallArguments args = new CallArguments();
        args.setMaxFeePerGas("0x64");
        args.setMaxPriorityFeePerGas("0x64");

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        Assertions.assertArrayEquals(new byte[] {0x64}, byteArrayArgs.getGasPrice());
    }

    @Test
    void getGasPrice_whenGasPriceSetAndOnlyMaxPriorityFee_usesGasPriceWithoutError() {
        CallArguments args = new CallArguments();
        args.setGasPrice("0x7");
        args.setMaxPriorityFeePerGas("0x64");

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        Assertions.assertArrayEquals(new byte[] {0x7}, byteArrayArgs.getGasPrice());
    }

    @Test
    void getGasPrice_whenGasPriceExplicitlySet_takesPrecedenceOverMaxFees() {
        CallArguments args = new CallArguments();
        args.setGasPrice("0x7");
        args.setMaxFeePerGas("0xff");
        args.setMaxPriorityFeePerGas("0xff");

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        Assertions.assertArrayEquals(new byte[] {0x7}, byteArrayArgs.getGasPrice());
    }

    @Test
    void getGasPrice_whenGasPriceSetAndMaxPriorityExceedsMaxFee_usesGasPriceWithoutError() {
        CallArguments args = new CallArguments();
        args.setGasPrice("0x7");
        args.setMaxFeePerGas("0x1");
        args.setMaxPriorityFeePerGas("0x64");

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        Assertions.assertArrayEquals(new byte[] {0x7}, byteArrayArgs.getGasPrice());
    }

    @Test
    void getAuthorizationListWhenValueIsNull_returnsEmptyList() {
        CallArguments args = new CallArguments();

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        Assertions.assertTrue(byteArrayArgs.getAuthorizationList().isEmpty());
    }

    @Test
    void getAuthorizationListWhenValueIsEmpty_rejectsRequest() {
        CallArguments args = new CallArguments();
        args.setAuthorizationList(List.of());

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        RskJsonRpcRequestException ex = Assertions.assertThrows(
                RskJsonRpcRequestException.class,
                byteArrayArgs::getAuthorizationList);
        Assertions.assertEquals(-32602, ex.getCode());
        Assertions.assertEquals("Set-code transaction authorization_list must not be empty", ex.getMessage());
    }

    @Test
    void getAuthorizationListParsesEntriesIntoSetCodeAuthorizations() {
        CallArguments.AuthorizationListEntry entry = Rskip545TestSupport.defaultType4AuthorizationEntry();
        CallArguments args = new CallArguments();
        args.setAuthorizationList(List.of(entry));

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        List<SetCodeAuthorization> authorizations = byteArrayArgs.getAuthorizationList();
        Assertions.assertEquals(1, authorizations.size());
        Assertions.assertEquals(entry.getAddress(), authorizations.get(0).getAddress().toJsonString());
    }

    @ParameterizedTest(name = "chainId=\"{0}\", default={1} -> {2}")
    @MethodSource("chainIdDefaultingCases")
    void getChainIdWhenValueIsAbsentOrSet_returnsExpected(String chainId, byte defaultChainId, byte expected) {
        CallArguments args = new CallArguments();
        if (chainId != null) {
            args.setChainId(chainId);
        }

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        Assertions.assertEquals(expected, byteArrayArgs.getChainId(TransactionType.LEGACY, defaultChainId));
    }

    private static Stream<Arguments> chainIdDefaultingCases() {
        return Stream.of(
                Arguments.of(null, (byte) 33, (byte) 33),
                Arguments.of("", (byte) 33, (byte) 33),
                Arguments.of("0x21", (byte) 1, (byte) 33)
        );
    }

    @Test
    void getChainIdWhenValueIsInvalid_rejectsRequest() {
        CallArguments args = new CallArguments();
        args.setChainId("0x1234");

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        RskJsonRpcRequestException ex = Assertions.assertThrows(
                RskJsonRpcRequestException.class,
                () -> byteArrayArgs.getChainId(TransactionType.LEGACY, (byte) 1));
        Assertions.assertEquals(-32602, ex.getCode());
        Assertions.assertEquals("Invalid chainId: 0x1234", ex.getMessage());
    }

    @Test
    void getChainIdWhenValueIsExplicitZeroForTypedTransaction_rejectsRequest() {
        CallArguments args = new CallArguments();
        args.setChainId("0x0");

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        RskJsonRpcRequestException ex = Assertions.assertThrows(RskJsonRpcRequestException.class,
                () -> byteArrayArgs.getChainId(TransactionType.TYPE_2, (byte) 33));
        Assertions.assertEquals(-32602, ex.getCode());
        Assertions.assertEquals("Invalid chainId: 0x0", ex.getMessage());
    }

    @Test
    void getChainIdWhenValueIsExplicitZeroForLegacyTransaction_returnsZero() {
        CallArguments args = new CallArguments();
        args.setChainId("0x0");

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        Assertions.assertEquals((byte) 0, byteArrayArgs.getChainId(TransactionType.LEGACY, (byte) 33));
    }

    @Test
    void resolveType_noAttributesPresent_returnsLegacy() {
        CallArguments args = new CallArguments();

        Assertions.assertEquals(TransactionType.LEGACY, new CallArgumentsToByteArray(args).resolveType());
    }

    @Test
    void resolveType_typeFieldAlone_isIgnored() {
        CallArguments args = new CallArguments();
        args.setType("0x2");
        Assertions.assertEquals(TransactionType.LEGACY, new CallArgumentsToByteArray(args).resolveType());
    }

    @Test
    void resolveType_accessListPresentButEmpty_returnsType1() {
        CallArguments args = new CallArguments();
        args.setAccessList(List.of());
        Assertions.assertEquals(TransactionType.TYPE_1, new CallArgumentsToByteArray(args).resolveType());
    }

    @Test
    void resolveType_accessListNonEmpty_returnsType1() {
        CallArguments args = new CallArguments();
        args.setAccessList(List.of(new CallArguments.AccessListEntry()));

        Assertions.assertEquals(TransactionType.TYPE_1, new CallArgumentsToByteArray(args).resolveType());
    }

    @Test
    void resolveType_feeCapsPresent_returnsType2_evenWithAccessListAlsoPresent() {
        CallArguments args = new CallArguments();
        args.setMaxPriorityFeePerGas("0x1");
        args.setMaxFeePerGas("0x2");
        args.setAccessList(List.of());

        Assertions.assertEquals(TransactionType.TYPE_2, new CallArgumentsToByteArray(args).resolveType());
    }

    @Test
    void resolveType_authorizationListPresentButEmpty_returnsType4_evenWithEverythingElsePresent() {
        CallArguments args = new CallArguments();
        args.setMaxPriorityFeePerGas("0x1");
        args.setMaxFeePerGas("0x2");
        args.setAccessList(List.of());
        args.setAuthorizationList(List.of());

        Assertions.assertEquals(TransactionType.TYPE_4, new CallArgumentsToByteArray(args).resolveType());
    }
}
