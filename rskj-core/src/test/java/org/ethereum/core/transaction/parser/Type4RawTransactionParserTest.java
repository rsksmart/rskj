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
import org.bouncycastle.util.BigIntegers;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Rskip545TestSupport;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionTypePrefix;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.core.transaction.parser.util.AuthorizationListCodec;
import org.ethereum.core.transaction.parser.util.Type4TransactionValidation;
import org.ethereum.crypto.signature.ECDSASignature;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import static org.ethereum.core.Rskip545TestSupport.DEFAULT_MAX_FEE;
import static org.ethereum.core.Rskip545TestSupport.DEFAULT_MAX_PRIORITY;
import static org.ethereum.core.Rskip545TestSupport.EMPTY_ACCESS_LIST;
import static org.ethereum.core.Rskip545TestSupport.EMPTY_AUTH_LIST;
import static org.ethereum.core.Rskip545TestSupport.REGTEST_CHAIN_ID;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link Type4RawTransactionParser} (RSKIP-545 / EIP-7702 set-code).
 */
class Type4RawTransactionParserTest {

    private static final byte[] PRIVATE_KEY = new byte[32];
    private static final BigInteger SECP256K1N_HALF =
            Constants.getSECP256K1N().divide(BigInteger.valueOf(2));
    private static final RskAddress RECEIVER =
            new RskAddress("0x0000000000000000000000000000000000000002");

    static {
        for (int i = 0; i < PRIVATE_KEY.length; i++) {
            PRIVATE_KEY[i] = (byte) (i + 1);
        }
    }

    private final Type4RawTransactionParser parser = new Type4RawTransactionParser();

    // -------------------------------------------------------------------------
    // validate() — fork gates
    // -------------------------------------------------------------------------

    @Test
    void validate_beforeRskip543_throws() {
        ActivationConfig activationConfig = mockActivationConfig(false, false, false);

        RskJsonRpcRequestException ex = assertThrows(RskJsonRpcRequestException.class,
                () -> parser.validate(1L, activationConfig, Constants.regtest()));

        assertTrue(ex.getMessage().contains("RSKIP-543"),
                "Expected RSKIP-543 gate, got: " + ex.getMessage());
    }

    @Test
    void validate_rskip543Only_throwsRskip546() {
        ActivationConfig activationConfig = mockActivationConfig(true, false, false);

        RskJsonRpcRequestException ex = assertThrows(RskJsonRpcRequestException.class,
                () -> parser.validate(1L, activationConfig, Constants.regtest()));

        assertTrue(ex.getMessage().contains("RSKIP-546"),
                "Expected RSKIP-546 gate, got: " + ex.getMessage());
    }

    @Test
    void validate_rskip543And546Without545_throwsRskip545() {
        ActivationConfig activationConfig = mockActivationConfig(true, true, false);

        RskJsonRpcRequestException ex = assertThrows(RskJsonRpcRequestException.class,
                () -> parser.validate(1L, activationConfig, Constants.regtest()));

        assertTrue(ex.getMessage().contains("RSKIP-545"),
                "Expected RSKIP-545 gate, got: " + ex.getMessage());
    }

    @Test
    void validate_allRequiredForksActive_doesNotThrow() {
        ActivationConfig activationConfig = mockActivationConfig(true, true, true);

        parser.validate(1L, activationConfig, Constants.regtest());
    }

    // -------------------------------------------------------------------------
    // parse(CallArguments)
    // -------------------------------------------------------------------------

    @Test
    void parse_callArguments_prefersGasOverGasLimit() {
        CallArguments args = type4Args();
        args.setGas("0x5208");
        args.setGasLimit("0x7530");

        ParsedType4Transaction parsed = parseFromCallArguments(args);

        assertEquals(BigInteger.valueOf(21_000), new BigInteger(1, parsed.gasLimit()));
    }

    @Test
    void parse_callArguments_nullDestination_throws() {
        CallArguments args = type4Args();
        args.setTo(null);

        RskJsonRpcRequestException ex = assertThrows(RskJsonRpcRequestException.class,
                () -> parseFromCallArguments(args));

        assertTrue(ex.getMessage().contains("destination"),
                "Expected destination error, got: " + ex.getMessage());
    }

    @Test
    void parse_callArguments_emptyAuthorizationList_throws() {
        CallArguments args = type4Args();
        args.setAuthorizationList(List.of());

        assertThrows(RskJsonRpcRequestException.class,
                () -> parseFromCallArguments(args));
    }

    @Test
    void parse_callArguments_maxPriorityFeeExceedsMaxFee_throws() {
        CallArguments args = type4Args();
        args.setMaxPriorityFeePerGas("0x64");
        args.setMaxFeePerGas("0x32");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> parseFromCallArguments(args));

        assertTrue(ex.getMessage().contains("maxPriorityFeePerGas"),
                "Expected fee-cap error, got: " + ex.getMessage());
    }

    @Test
    void parse_callArguments_propagatesAuthorizationList() {
        ParsedType4Transaction parsed = parseFromCallArguments(type4Args());

        assertEquals(1, parsed.authorizationList().size());
        assertEquals(BigInteger.valueOf(33), parsed.authorizationList().get(0).getChainId());
    }

    // -------------------------------------------------------------------------
    // parse(RLPList)
    // -------------------------------------------------------------------------

    @Test
    void parse_rlp_signedTransaction_roundTripsCoreFields() {
        Transaction original = buildSignedType4();
        RLPList fields = decodePayload(original);

        ParsedType4Transaction parsed = parser.parse(
                TransactionTypePrefix.typed(TransactionType.TYPE_4), fields);

        assertArrayEquals(original.getNonce(), parsed.nonce());
        assertArrayEquals(original.getGasLimit(), parsed.gasLimit());
        assertEquals(original.getReceiveAddress(), parsed.receiveAddress());
        assertEquals(original.getMaxPriorityFeePerGas(), parsed.maxPriorityFeePerGas());
        assertEquals(original.getMaxFeePerGas(), parsed.maxFeePerGas());
        assertEquals(1, parsed.authorizationList().size());
    }

    @Test
    void parse_rlp_nullDestination_throws() {
        RLPList fields = buildUnsignedType4Payload(RskAddress.nullAddress());

        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(TransactionTypePrefix.typed(TransactionType.TYPE_4), fields));
    }

    @Test
    void parse_rlp_emptyAuthorizationList_throws() {
        RLPList fields = buildUnsignedType4Payload(RECEIVER, RLP.encodeList());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> parser.parse(TransactionTypePrefix.typed(TransactionType.TYPE_4), fields));

        assertTrue(ex.getMessage().contains("authorization_list must not be empty"),
                "Expected empty authorization_list error, got: " + ex.getMessage());
    }

    @Test
    void parse_rlp_wrongFieldCount_throws() {
        byte[] authList = AuthorizationListCodec.encodeList(
                List.of(Rskip545TestSupport.minimalAuthorization(REGTEST_CHAIN_ID)));
        RLPList tooMany = RLP.decodeList(RLP.encodeList(
                RLP.encodeByte(REGTEST_CHAIN_ID),
                RLP.encodeElement(new byte[]{0x01}),
                RLP.encodeCoinNonNullZero(Coin.valueOf(10)),
                RLP.encodeCoinNonNullZero(Coin.valueOf(100)),
                RLP.encodeBigInteger(BigInteger.valueOf(21_000)),
                RLP.encodeRskAddress(RECEIVER),
                RLP.encodeBigInteger(BigInteger.ZERO),
                RLP.encodeElement(new byte[0]),
                RLP.encodeList(),
                authList,
                RLP.encodeByte((byte) 0),
                RLP.encodeElement(new byte[32]),
                RLP.encodeElement(new byte[32]),
                RLP.encodeElement(new byte[]{0x01})
        ));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> parser.parse(TransactionTypePrefix.typed(TransactionType.TYPE_4), tooMany));

        assertTrue(ex.getMessage().contains("13 elements"),
                "Expected field-count error, got: " + ex.getMessage());
    }

    @Test
    void parse_rlp_tooFewFields_throws() {
        byte[] authList = Rskip545TestSupport.defaultAuthListBytes();
        RLPList tooFew = RLP.decodeList(RLP.encodeList(
                RLP.encodeByte(REGTEST_CHAIN_ID),
                RLP.encodeElement(new byte[0]),
                RLP.encodeCoinNonNullZero(DEFAULT_MAX_PRIORITY),
                RLP.encodeCoinNonNullZero(DEFAULT_MAX_FEE),
                RLP.encodeElement(BigInteger.valueOf(21_000).toByteArray()),
                RLP.encodeRskAddress(RECEIVER),
                RLP.encodeElement(new byte[0]),
                RLP.encodeElement(new byte[0]),
                EMPTY_ACCESS_LIST,
                authList,
                RLP.encodeByte((byte) 0),
                RLP.encodeElement(new byte[32])
        ));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> parser.parse(TransactionTypePrefix.typed(TransactionType.TYPE_4), tooFew));

        assertTrue(ex.getMessage().contains("13 elements"));
    }

    @Test
    void parse_rlp_emptyAuthorizationListRawBytes_throws() {
        RLPList fields = Rskip545TestSupport.buildType4RlpList(RECEIVER, EMPTY_AUTH_LIST);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> parser.parse(TransactionTypePrefix.typed(TransactionType.TYPE_4), fields));

        assertTrue(ex.getMessage().contains("authorization_list"));
    }

    @ParameterizedTest(name = "parse_rlp rejects outer yParity={0}")
    @ValueSource(bytes = {2, 3, 5, 10, 100, (byte) 127})
    void parse_rlp_invalidOuterYParity_throws(byte yParity) {
        byte[][] base = Rskip545TestSupport.defaultSignedType4Fields(RECEIVER, Rskip545TestSupport.defaultAuthListBytes());
        base[10] = RLP.encodeByte(yParity);
        RLPList fields = RLP.decodeList(RLP.encodeList(base));

        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(TransactionTypePrefix.typed(TransactionType.TYPE_4), fields));
    }

    @Test
    void parse_rlp_incompleteOuterSignature_throws() {
        byte[][] base = Rskip545TestSupport.defaultSignedType4Fields(RECEIVER, Rskip545TestSupport.defaultAuthListBytes());
        base[12] = RLP.encodeElement(null);
        RLPList fields = RLP.decodeList(RLP.encodeList(base));

        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(TransactionTypePrefix.typed(TransactionType.TYPE_4), fields));
    }

    @Test
    void parse_rlp_chainIdZero_throws() {
        byte[][] base = Rskip545TestSupport.defaultSignedType4Fields(RECEIVER, Rskip545TestSupport.defaultAuthListBytes());
        base[0] = RLP.encodeElement(new byte[0]);
        RLPList fields = RLP.decodeList(RLP.encodeList(base));

        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(TransactionTypePrefix.typed(TransactionType.TYPE_4), fields));
    }

    @ParameterizedTest(name = "parse_rlp rejects oversize chainId={0}")
    @ValueSource(ints = {256, 286, 65535})
    void parse_rlp_chainIdExceeding255_throws(int chainIdValue) {
        byte[][] base = Rskip545TestSupport.defaultSignedType4Fields(RECEIVER, Rskip545TestSupport.defaultAuthListBytes());
        base[0] = RLP.encodeElement(BigIntegers.asUnsignedByteArray(BigInteger.valueOf(chainIdValue)));
        RLPList fields = RLP.decodeList(RLP.encodeList(base));

        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(TransactionTypePrefix.typed(TransactionType.TYPE_4), fields));
    }

    @Test
    void parse_rlp_maxPriorityExceedsMaxFee_throws() {
        byte[][] base = Rskip545TestSupport.defaultSignedType4Fields(RECEIVER, Rskip545TestSupport.defaultAuthListBytes());
        base[2] = RLP.encodeCoinNonNullZero(Coin.valueOf(200));
        base[3] = RLP.encodeCoinNonNullZero(Coin.valueOf(100));
        RLPList fields = RLP.decodeList(RLP.encodeList(base));

        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(TransactionTypePrefix.typed(TransactionType.TYPE_4), fields));
    }

    @Test
    void parse_rlp_truncatedAccessList_throws() {
        byte[][] base = Rskip545TestSupport.defaultSignedType4Fields(RECEIVER, Rskip545TestSupport.defaultAuthListBytes());
        base[8] = new byte[]{(byte) 0xC4, 0x01};
        RLPList fields = RLP.decodeList(RLP.encodeList(base));

        assertThrows(Exception.class,
                () -> parser.parse(TransactionTypePrefix.typed(TransactionType.TYPE_4), fields));
    }

    @Test
    void parse_rlp_highSOuterSignature_throws() {
        byte[] oversizedS = BigIntegers.asUnsignedByteArray(SECP256K1N_HALF.add(BigInteger.ONE));
        byte[][] base = Rskip545TestSupport.defaultSignedType4Fields(RECEIVER, Rskip545TestSupport.defaultAuthListBytes());
        base[11] = RLP.encodeElement(BigIntegers.asUnsignedByteArray(BigInteger.ONE));
        base[12] = RLP.encodeElement(oversizedS);
        RLPList fields = RLP.decodeList(RLP.encodeList(base));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> parser.parse(TransactionTypePrefix.typed(TransactionType.TYPE_4), fields));

        assertTrue(ex.getMessage().contains("secp256k1n/2")
                        || ex.getMessage().contains("signature components are invalid"),
                "Expected high-s rejection, got: " + ex.getMessage());
    }

    @Test
    void parse_rlp_authChainIdZero_allowed() {
        SetCodeAuthorization authWithZeroChain = Rskip545TestSupport.authorizationWithChainId(BigInteger.ZERO);
        byte[][] base = Rskip545TestSupport.defaultSignedType4Fields(
                RECEIVER, AuthorizationListCodec.encodeList(List.of(authWithZeroChain)));
        base[10] = RLP.encodeByte((byte) 0);
        base[11] = RLP.encodeElement(null);
        base[12] = RLP.encodeElement(null);
        RLPList fields = RLP.decodeList(RLP.encodeList(base));

        assertDoesNotThrow(() -> parser.parse(TransactionTypePrefix.typed(TransactionType.TYPE_4), fields));
    }

    @Test
    void parse_rlp_authTupleWrongFieldCount_throws() {
        byte[] badTuple = RLP.encodeList(
                RLP.encodeBigInteger(BigInteger.ZERO),
                RLP.encodeRskAddress(new RskAddress(new byte[20])),
                RLP.encodeElement(new byte[]{0}),
                RLP.encodeByte((byte) 0),
                RLP.encodeElement(new byte[]{1})
        );
        RLPList fields = Rskip545TestSupport.buildType4RlpList(RECEIVER, RLP.encodeList(badTuple));

        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(TransactionTypePrefix.typed(TransactionType.TYPE_4), fields));
    }

    @Test
    void parse_rlp_authChainIdTooLarge_throws() {
        byte[] authList = Rskip545TestSupport.authListWithModifiedTupleField(
                0, RLP.encodeBigInteger(BigInteger.ONE.shiftLeft(256)));
        RLPList fields = Rskip545TestSupport.buildType4RlpList(RECEIVER, authList);

        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(TransactionTypePrefix.typed(TransactionType.TYPE_4), fields));
    }

    @Test
    void parse_rlp_authNonceTooLarge_throws() {
        byte[] authList = Rskip545TestSupport.authListWithModifiedTupleField(
                2, RLP.encodeBigInteger(BigInteger.ONE.shiftLeft(64)));
        RLPList fields = Rskip545TestSupport.buildType4RlpList(RECEIVER, authList);

        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(TransactionTypePrefix.typed(TransactionType.TYPE_4), fields));
    }

    @Test
    void parse_rlp_authAddressWrongLength_throws() {
        byte[] authList = Rskip545TestSupport.authListWithModifiedTupleField(
                1, RLP.encodeElement(new byte[]{1, 2, 3}));
        RLPList fields = Rskip545TestSupport.buildType4RlpList(RECEIVER, authList);

        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(TransactionTypePrefix.typed(TransactionType.TYPE_4), fields));
    }

    @ParameterizedTest(name = "parse_rlp rejects auth yParity={0}")
    @ValueSource(ints = {2, 5})
    void parse_rlp_authYParityInvalid_throws(int yParityValue) {
        byte[] authList = Rskip545TestSupport.authListWithModifiedTupleField(
                3, RLP.encodeByte((byte) yParityValue));
        RLPList fields = Rskip545TestSupport.buildType4RlpList(RECEIVER, authList);

        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(TransactionTypePrefix.typed(TransactionType.TYPE_4), fields));
    }

    @Test
    void parse_rlp_authOversizedS_throws() {
        byte[] oversizedS = BigIntegers.asUnsignedByteArray(SECP256K1N_HALF.add(BigInteger.ONE));
        byte[] authList = Rskip545TestSupport.authListWithModifiedTupleField(5, RLP.encodeElement(oversizedS));
        RLPList fields = Rskip545TestSupport.buildType4RlpList(RECEIVER, authList);

        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(TransactionTypePrefix.typed(TransactionType.TYPE_4), fields));
    }

    @Test
    void parse_rlp_authOversizedR_throws() {
        byte[] oversizedR = new byte[33];
        oversizedR[0] = 0x01;
        byte[] authList = Rskip545TestSupport.authListWithModifiedTupleField(4, RLP.encodeElement(oversizedR));
        RLPList fields = Rskip545TestSupport.buildType4RlpList(RECEIVER, authList);

        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(TransactionTypePrefix.typed(TransactionType.TYPE_4), fields));
    }

    @Test
    void validateOuterSignatureFormat_rejectsHighS() {
        ECDSASignature highS = ECDSASignature.fromComponents(
                BigIntegers.asUnsignedByteArray(BigInteger.ONE),
                BigIntegers.asUnsignedByteArray(SECP256K1N_HALF.add(BigInteger.ONE)),
                (byte) 27);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> Type4TransactionValidation.validateOuterSignatureFormat(highS));

        assertTrue(ex.getMessage().contains("secp256k1n/2"));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static ActivationConfig mockActivationConfig(boolean rskip543, boolean rskip546, boolean rskip545) {
        ActivationConfig.ForBlock forBlock = mock(ActivationConfig.ForBlock.class);
        when(forBlock.isActive(ConsensusRule.RSKIP543)).thenReturn(rskip543);
        when(forBlock.isActive(ConsensusRule.RSKIP546)).thenReturn(rskip546);
        when(forBlock.isActive(ConsensusRule.RSKIP545)).thenReturn(rskip545);

        ActivationConfig activationConfig = mock(ActivationConfig.class);
        when(activationConfig.forBlock(anyLong())).thenReturn(forBlock);
        return activationConfig;
    }

    private static RLPList decodePayload(Transaction tx) {
        byte[] encoded = tx.getEncoded();
        return RLP.decodeList(Arrays.copyOfRange(encoded, 1, encoded.length));
    }

    private static Transaction buildSignedType4() {
        Transaction tx = Rskip545TestSupport.unsignedType4();
        tx.sign(PRIVATE_KEY);
        return tx;
    }

    private static RLPList buildUnsignedType4Payload(RskAddress to) {
        byte[] authList = AuthorizationListCodec.encodeList(
                List.of(Rskip545TestSupport.minimalAuthorization(REGTEST_CHAIN_ID)));
        return buildUnsignedType4Payload(to, authList);
    }

    private static RLPList buildUnsignedType4Payload(RskAddress to, byte[] authListBytes) {
        byte[][] fields = new byte[][]{
                RLP.encodeByte(REGTEST_CHAIN_ID),
                RLP.encodeElement(new byte[]{0x01}),
                RLP.encodeCoinNonNullZero(Coin.valueOf(10)),
                RLP.encodeCoinNonNullZero(Coin.valueOf(100)),
                RLP.encodeBigInteger(BigInteger.valueOf(21_000)),
                RLP.encodeRskAddress(to),
                RLP.encodeBigInteger(BigInteger.ZERO),
                RLP.encodeElement(new byte[0]),
                RLP.encodeList(),
                authListBytes,
                RLP.encodeByte((byte) 0),
                RLP.encodeElement(new byte[32]),
                RLP.encodeElement(new byte[32])
        };
        return RLP.decodeList(RLP.encodeList(fields));
    }

    private ParsedType4Transaction parseFromCallArguments(CallArguments args) {
        return parser.parse(
                TransactionTypePrefix.typed(TransactionType.TYPE_4),
                TransactionInput.fromCallArguments(args, null),
                REGTEST_CHAIN_ID);
    }

    private static CallArguments type4Args() {
        CallArguments args = new CallArguments();
        args.setFrom("0x0000000000000000000000000000000000000001");
        args.setTo(RECEIVER.toHexString());
        args.setGas("0x5208");
        args.setValue("0x0");
        args.setNonce("0x1");
        args.setType("0x4");
        args.setChainId("0x21");
        args.setMaxPriorityFeePerGas("0xa");
        args.setMaxFeePerGas("0x64");

        CallArguments.AuthorizationListEntry entry = new CallArguments.AuthorizationListEntry();
        entry.setChainId("0x21");
        entry.setAddress("0x0000000000000000000000000000000000000003");
        entry.setNonce("0x0");
        entry.setYParity("0x0");
        entry.setR("0x01");
        entry.setS("0x01");
        args.setAuthorizationList(List.of(entry));
        return args;
    }
}
