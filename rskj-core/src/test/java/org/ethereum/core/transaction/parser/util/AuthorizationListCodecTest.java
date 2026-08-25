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
package org.ethereum.core.transaction.parser.util;

import co.rsk.core.RskAddress;
import co.rsk.util.HexUtils;
import org.bouncycastle.util.BigIntegers;
import org.ethereum.config.Constants;
import org.ethereum.core.Rskip545TestSupport;
import org.ethereum.core.Transaction;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.crypto.signature.ECDSASignature;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Unit tests for {@link AuthorizationListCodec} (RSKIP-545 / EIP-7702).
 */
class AuthorizationListCodecTest {

    private static final BigInteger SECP256K1N_HALF =
            Constants.getSECP256K1N().divide(BigInteger.valueOf(2));

    // -------------------------------------------------------------------------
    // encodeList / decodeList
    // -------------------------------------------------------------------------

    @Test
    void encodeList_decodeList_roundTrip() {
        SetCodeAuthorization auth = Rskip545TestSupport.minimalAuthorization((byte) 33);
        byte[] encoded = AuthorizationListCodec.encodeList(List.of(auth));

        List<SetCodeAuthorization> decoded = AuthorizationListCodec.decodeList(encoded);

        assertEquals(1, decoded.size());
        assertEquals(auth, decoded.get(0));
    }

    @Test
    void encodeList_null_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> AuthorizationListCodec.encodeList(null));
    }

    @Test
    void encodeList_emptyList_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> AuthorizationListCodec.encodeList(List.of()));
    }

    @Test
    void encodeTuple_decodeTuple_roundTrip() {
        SetCodeAuthorization auth = Rskip545TestSupport.minimalAuthorization((byte) 33);
        byte[] tuple = AuthorizationListCodec.encodeTuple(auth);
        SetCodeAuthorization decoded = AuthorizationListCodec.decodeTuple(
                RLP.decode2(tuple).get(0));

        assertEquals(auth, decoded);
    }

    // -------------------------------------------------------------------------
    // requireAuthorizationListBytes
    // -------------------------------------------------------------------------

    @Test
    void requireAuthorizationListBytes_null_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> AuthorizationListCodec.requireAuthorizationListBytes(null));
    }

    @Test
    void requireAuthorizationListBytes_empty_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> AuthorizationListCodec.requireAuthorizationListBytes(new byte[0]));
    }

    @Test
    void requireAuthorizationListBytes_emptyRlpList_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> AuthorizationListCodec.requireAuthorizationListBytes(RLP.encodeList()));
    }

    @Test
    void requireAuthorizationListBytes_invalidRlp_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AuthorizationListCodec.requireAuthorizationListBytes(new byte[]{(byte) 0xff, 0x01}));

        assertTrue(ex.getMessage().contains("invalid RLP"),
                "Expected invalid RLP error, got: " + ex.getMessage());
    }

    // -------------------------------------------------------------------------
    // parseFromCallArguments
    // -------------------------------------------------------------------------

    @Test
    void parseFromCallArguments_nullEntries_throws() {
        RskJsonRpcRequestException ex = assertThrows(RskJsonRpcRequestException.class,
                () -> AuthorizationListCodec.parseFromCallArguments(null));

        assertTrue(ex.getMessage().contains("authorization_list"));
    }

    @Test
    void parseFromCallArguments_emptyEntries_throws() {
        assertThrows(RskJsonRpcRequestException.class,
                () -> AuthorizationListCodec.parseFromCallArguments(List.of()));
    }

    @Test
    void parseFromCallArguments_propagatesFields() {
        SetCodeAuthorization reference = Rskip545TestSupport.minimalAuthorization((byte) 33);
        ECDSASignature sig = reference.getSignature();

        CallArguments.AuthorizationListEntry entry = validEntry(reference, sig);
        entry.setNonce("0x1");

        SetCodeAuthorization auth = AuthorizationListCodec.parseFromCallArguments(List.of(entry)).get(0);

        assertEquals(BigInteger.valueOf(33), auth.getChainId());
        assertEquals(reference.getAddress(), auth.getAddress());
        assertEquals(BigInteger.ONE, new BigInteger(1, auth.getNonce()));
    }

    @Test
    void parseFromCallArguments_missingNonce_throws() {
        SetCodeAuthorization reference = Rskip545TestSupport.minimalAuthorization((byte) 33);
        CallArguments.AuthorizationListEntry entry = validEntry(reference, reference.getSignature());
        entry.setNonce(null);

        RskJsonRpcRequestException ex = assertThrows(RskJsonRpcRequestException.class,
                () -> AuthorizationListCodec.parseFromCallArguments(List.of(entry)));

        assertTrue(ex.getMessage().contains("nonce"));
    }

    @Test
    void parseFromCallArguments_missingChainId_throws() {
        CallArguments.AuthorizationListEntry entry = new CallArguments.AuthorizationListEntry();
        entry.setAddress("0x0000000000000000000000000000000000000002");
        entry.setNonce("0x0");
        entry.setYParity("0x0");
        entry.setR("0x01");
        entry.setS("0x01");

        RskJsonRpcRequestException ex = assertThrows(RskJsonRpcRequestException.class,
                () -> AuthorizationListCodec.parseFromCallArguments(List.of(entry)));

        assertTrue(ex.getMessage().contains("chainId"));
    }

    @Test
    void parseFromCallArguments_missingAddress_throws() {
        SetCodeAuthorization reference = Rskip545TestSupport.minimalAuthorization((byte) 33);
        CallArguments.AuthorizationListEntry entry = validEntry(reference, reference.getSignature());
        entry.setAddress(null);

        assertThrows(RskJsonRpcRequestException.class,
                () -> AuthorizationListCodec.parseFromCallArguments(List.of(entry)));
    }

    @Test
    void parseFromCallArguments_missingYParity_throws() {
        SetCodeAuthorization reference = Rskip545TestSupport.minimalAuthorization((byte) 33);
        CallArguments.AuthorizationListEntry entry = validEntry(reference, reference.getSignature());
        entry.setYParity(null);

        assertThrows(RskJsonRpcRequestException.class,
                () -> AuthorizationListCodec.parseFromCallArguments(List.of(entry)));
    }

    @Test
    void parseFromCallArguments_missingR_throws() {
        SetCodeAuthorization reference = Rskip545TestSupport.minimalAuthorization((byte) 33);
        CallArguments.AuthorizationListEntry entry = validEntry(reference, reference.getSignature());
        entry.setR(null);

        assertThrows(RskJsonRpcRequestException.class,
                () -> AuthorizationListCodec.parseFromCallArguments(List.of(entry)));
    }

    @Test
    void parseFromCallArguments_missingS_throws() {
        SetCodeAuthorization reference = Rskip545TestSupport.minimalAuthorization((byte) 33);
        CallArguments.AuthorizationListEntry entry = validEntry(reference, reference.getSignature());
        entry.setS(null);

        assertThrows(RskJsonRpcRequestException.class,
                () -> AuthorizationListCodec.parseFromCallArguments(List.of(entry)));
    }

    @Test
    void parseFromCallArguments_invalidAddressLength_throws() {
        SetCodeAuthorization reference = Rskip545TestSupport.minimalAuthorization((byte) 33);
        CallArguments.AuthorizationListEntry entry = validEntry(reference, reference.getSignature());
        entry.setAddress("0x0102");

        RskJsonRpcRequestException ex = assertThrows(RskJsonRpcRequestException.class,
                () -> AuthorizationListCodec.parseFromCallArguments(List.of(entry)));

        assertTrue(ex.getMessage().contains("20-byte"),
                "Expected address length error, got: " + ex.getMessage());
    }

    @Test
    void parseFromCallArguments_invalidSignatureHex_throws() {
        SetCodeAuthorization reference = Rskip545TestSupport.minimalAuthorization((byte) 33);
        CallArguments.AuthorizationListEntry entry = validEntry(reference, reference.getSignature());
        entry.setR("not-hex");

        assertThrows(org.bouncycastle.util.encoders.DecoderException.class,
                () -> AuthorizationListCodec.parseFromCallArguments(List.of(entry)));
    }

    // -------------------------------------------------------------------------
    // decodeTuple / decodeList
    // -------------------------------------------------------------------------

    @Test
    void decodeTuple_emptyTupleBytes_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> AuthorizationListCodec.decodeTuple(RLP.decode2(new byte[]{(byte) 0x80}).get(0)));
    }

    @Test
    void decodeTuple_wrongFieldCount_throws() {
        byte[] badTuple = RLP.encodeList(
                RLP.encodeBigInteger(BigInteger.ZERO),
                RLP.encodeRskAddress(new RskAddress(new byte[20])),
                RLP.encodeElement(new byte[]{0}),
                RLP.encodeByte((byte) 0),
                RLP.encodeElement(new byte[]{1})
        );
        byte[] list = RLP.encodeList(badTuple);

        assertThrows(IllegalArgumentException.class,
                () -> AuthorizationListCodec.decodeList(list));
    }

    @Test
    void decodeTuple_emptyChainId_decodesAsZero() {
        SetCodeAuthorization reference = Rskip545TestSupport.minimalAuthorization((byte) 33);
        byte[] tuple = rebuildTupleField(reference, 0, RLP.encodeElement(new byte[0]));

        SetCodeAuthorization auth = decodeSingleTuple(tuple);

        assertEquals(BigInteger.ZERO, auth.getChainId());
    }

    @Test
    void decodeTuple_chainIdTooLarge_throws() {
        SetCodeAuthorization reference = Rskip545TestSupport.minimalAuthorization((byte) 33);
        byte[] tuple = rebuildTupleField(reference, 0,
                RLP.encodeBigInteger(BigInteger.ONE.shiftLeft(256)));

        assertThrows(IllegalArgumentException.class, () -> decodeSingleTuple(tuple));
    }

    @Test
    void decodeTuple_invalidAddressLength_throws() {
        SetCodeAuthorization reference = Rskip545TestSupport.minimalAuthorization((byte) 33);
        byte[] tuple = rebuildTupleField(reference, 1, RLP.encodeElement(new byte[]{1, 2, 3}));

        assertThrows(IllegalArgumentException.class, () -> decodeSingleTuple(tuple));
    }

    @Test
    void decodeTuple_nonceTooLarge_throws() {
        SetCodeAuthorization reference = Rskip545TestSupport.minimalAuthorization((byte) 33);
        byte[] tuple = rebuildTupleField(reference, 2,
                RLP.encodeBigInteger(BigInteger.ONE.shiftLeft(64)));

        assertThrows(IllegalArgumentException.class, () -> decodeSingleTuple(tuple));
    }

    @Test
    void decodeTuple_nonceAtMaxUint64MinusOne_throws() {
        SetCodeAuthorization reference = Rskip545TestSupport.minimalAuthorization((byte) 33);
        byte[] tuple = rebuildTupleField(reference, 2,
                RLP.encodeBigInteger(BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> decodeSingleTuple(tuple));
        assertTrue(ex.getMessage().contains("2^64"),
                "Expected nonce range error, got: " + ex.getMessage());
    }

    @Test
    void decodeTuple_nonceAtMaxAllowedValue_succeeds() {
        SetCodeAuthorization reference = Rskip545TestSupport.minimalAuthorization((byte) 33);
        BigInteger maxAllowed = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.valueOf(2));
        byte[] tuple = rebuildTupleField(reference, 2, RLP.encodeBigInteger(maxAllowed));

        SetCodeAuthorization decoded = decodeSingleTuple(tuple);
        assertEquals(maxAllowed, new BigInteger(1, decoded.getNonce()));
    }

    @Test
    void decodeTuple_missingSignatureComponents_throws() {
        SetCodeAuthorization reference = Rskip545TestSupport.minimalAuthorization((byte) 33);
        byte[] tuple = rebuildTupleField(reference, 4, RLP.encodeElement(null));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AuthorizationListCodec.decodeTuple(RLP.decode2(tuple).get(0)));

        assertTrue(ex.getMessage().contains("incomplete"),
                "Expected incomplete signature error, got: " + ex.getMessage());
    }

    @Test
    void decodeTuple_yParityMultiByte_throws() {
        SetCodeAuthorization reference = Rskip545TestSupport.minimalAuthorization((byte) 33);
        byte[] tuple = rebuildTupleField(reference, 3, RLP.encodeElement(new byte[]{0, 1}));

        assertThrows(IllegalArgumentException.class, () -> decodeSingleTuple(tuple));
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 5})
    void decodeTuple_invalidYParityValue_throws(int yParityValue) {
        SetCodeAuthorization reference = Rskip545TestSupport.minimalAuthorization((byte) 33);
        byte[] tuple = rebuildTupleField(reference, 3, RLP.encodeByte((byte) yParityValue));

        assertThrows(IllegalArgumentException.class, () -> decodeSingleTuple(tuple));
    }

    @Test
    void decodeTuple_emptyYParity_defaultsToZero() {
        SetCodeAuthorization reference = Rskip545TestSupport.minimalAuthorization((byte) 33);
        byte[] tuple = rebuildTupleField(reference, 3, RLP.encodeElement(new byte[0]));

        SetCodeAuthorization decoded = AuthorizationListCodec.decodeTuple(RLP.decode2(tuple).get(0));

        assertEquals((byte) 0, (byte) (decoded.getSignature().getV() - Transaction.LOWER_REAL_V));
    }

    @Test
    void decodeTuple_oversizeNonceBytes_throws() {
        SetCodeAuthorization reference = Rskip545TestSupport.minimalAuthorization((byte) 33);
        byte[] oversizeNonce = new byte[33];
        oversizeNonce[0] = 0x00;
        oversizeNonce[32] = 0x01;
        byte[] tuple = rebuildTupleField(reference, 2, RLP.encodeElement(oversizeNonce));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> decodeSingleTuple(tuple));
        assertTrue(ex.getMessage().contains("Authorization nonce is not valid"), ex.getMessage());
    }

    @Test
    void decodeTuple_oversizeChainIdBytes_throws() {
        SetCodeAuthorization reference = Rskip545TestSupport.minimalAuthorization((byte) 33);
        byte[] oversizeChainId = new byte[33];
        oversizeChainId[0] = 0x01;
        byte[] tuple = rebuildTupleField(reference, 0, RLP.encodeElement(oversizeChainId));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> decodeSingleTuple(tuple));
        assertTrue(ex.getMessage().contains("Authorization chain_id is not valid"), ex.getMessage());
    }

    @Test
    void decodeTuple_oversizeSignatureR_throws() {
        SetCodeAuthorization reference = Rskip545TestSupport.minimalAuthorization((byte) 33);
        byte[] oversizeR = new byte[33];
        oversizeR[0] = 0x01;
        byte[] tuple = rebuildTupleField(reference, 4, RLP.encodeElement(oversizeR));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> decodeSingleTuple(tuple));
        assertTrue(ex.getMessage().contains("Authorization signature r is not valid"), ex.getMessage());
    }

    @Test
    void decodeTuple_oversizeSignatureS_throws() {
        SetCodeAuthorization reference = Rskip545TestSupport.minimalAuthorization((byte) 33);
        byte[] oversizeS = new byte[33];
        oversizeS[0] = 0x01;
        byte[] tuple = rebuildTupleField(reference, 5, RLP.encodeElement(oversizeS));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> decodeSingleTuple(tuple));
        assertTrue(ex.getMessage().contains("Authorization signature s is not valid"), ex.getMessage());
    }

    @Test
    void encodeTuple_highSignatureS_throws() {
        SetCodeAuthorization auth = Rskip545TestSupport.minimalAuthorization((byte) 33);
        BigInteger highS = SECP256K1N_HALF.add(BigInteger.ONE);
        SetCodeAuthorization bad = new SetCodeAuthorization(
                auth.getChainId(),
                auth.getAddress(),
                auth.getNonce(),
                ECDSASignature.fromComponents(
                        BigIntegers.asUnsignedByteArray(auth.getSignature().getR()),
                        BigIntegers.asUnsignedByteArray(highS),
                        auth.getSignature().getV()));

        assertThrows(IllegalArgumentException.class,
                () -> AuthorizationListCodec.encodeTuple(bad));
    }

    @Test
    void decodeTuple_nullNonceField_defaultsToEmpty() {
        SetCodeAuthorization reference = Rskip545TestSupport.minimalAuthorization((byte) 33);
        byte[] tuple = rebuildTupleField(reference, 2, RLP.encodeElement(null));

        SetCodeAuthorization decoded = decodeSingleTuple(tuple);

        assertEquals(BigInteger.ZERO, new BigInteger(1, decoded.getNonce()));
    }

    @Test
    void parseFromCallArguments_emptyChainId_decodesAsZero() {
        SetCodeAuthorization reference = Rskip545TestSupport.minimalAuthorization((byte) 33);
        CallArguments.AuthorizationListEntry entry = validEntry(reference, reference.getSignature());
        entry.setChainId("0x");

        SetCodeAuthorization auth = AuthorizationListCodec.parseFromCallArguments(List.of(entry)).get(0);

        assertEquals(BigInteger.ZERO, auth.getChainId());
    }

    @Test
    void parseFromCallArguments_decimalNonceZero_succeeds() {
        SetCodeAuthorization reference = Rskip545TestSupport.minimalAuthorization((byte) 33);
        CallArguments.AuthorizationListEntry entry = validEntry(reference, reference.getSignature());
        entry.setNonce("0");

        SetCodeAuthorization auth = AuthorizationListCodec.parseFromCallArguments(List.of(entry)).get(0);

        assertEquals(BigInteger.ZERO, new BigInteger(1, auth.getNonce()));
    }

    @Test
    void decodeListUnchecked_parsesWithoutRevalidation() {
        SetCodeAuthorization auth = Rskip545TestSupport.minimalAuthorization((byte) 33);
        byte[] encoded = AuthorizationListCodec.encodeList(List.of(auth));

        List<SetCodeAuthorization> decoded = AuthorizationListCodec.decodeListUnchecked(encoded);

        assertEquals(1, decoded.size());
        assertEquals(auth, decoded.get(0));
    }

    @Test
    void privateDecodeNonce_null_returnsZero() throws Exception {
        java.lang.reflect.Method decodeNonce = AuthorizationListCodec.class.getDeclaredMethod(
                "decodeNonce", byte[].class);
        decodeNonce.setAccessible(true);

        assertEquals(BigInteger.ZERO, decodeNonce.invoke(null, (Object) null));
    }

    @Test
    void parseFromCallArguments_nullSignatureHex_throws() {
        SetCodeAuthorization reference = Rskip545TestSupport.minimalAuthorization((byte) 33);
        CallArguments.AuthorizationListEntry entry = validEntry(reference, reference.getSignature());
        entry.setR(null);

        assertThrows(RskJsonRpcRequestException.class,
                () -> AuthorizationListCodec.parseFromCallArguments(List.of(entry)));
    }

    @Test
    void parseCallArgumentsEntry_nullNonceBytes_defaultsToEmpty() throws Exception {
        SetCodeAuthorization reference = Rskip545TestSupport.minimalAuthorization((byte) 33);
        CallArguments.AuthorizationListEntry entry = validEntry(reference, reference.getSignature());
        entry.setNonce("0x0");

        Method parseEntry = AuthorizationListCodec.class.getDeclaredMethod(
                "parseCallArgumentsEntry", CallArguments.AuthorizationListEntry.class, int.class);
        parseEntry.setAccessible(true);

        try (MockedStatic<HexUtils> hex = Mockito.mockStatic(HexUtils.class, Mockito.CALLS_REAL_METHODS)) {
            hex.when(() -> HexUtils.strHexOrStrNumberToByteArray(eq("0x0"))).thenReturn(null);

            SetCodeAuthorization auth = (SetCodeAuthorization) parseEntry.invoke(null, entry, 0);

            assertEquals(BigInteger.ZERO, new BigInteger(1, auth.getNonce()));
        }
    }

    @Test
    void parseCallArgumentsEntry_nullSignatureBytes_throws() throws Exception {
        SetCodeAuthorization reference = Rskip545TestSupport.minimalAuthorization((byte) 33);
        CallArguments.AuthorizationListEntry entry = validEntry(reference, reference.getSignature());
        String rHex = entry.getR();

        Method parseEntry = AuthorizationListCodec.class.getDeclaredMethod(
                "parseCallArgumentsEntry", CallArguments.AuthorizationListEntry.class, int.class);
        parseEntry.setAccessible(true);

        try (MockedStatic<HexUtils> hex = Mockito.mockStatic(HexUtils.class, Mockito.CALLS_REAL_METHODS)) {
            hex.when(() -> HexUtils.stringHexToByteArray(eq(rHex))).thenReturn(null);

            InvocationTargetException ex = assertThrows(InvocationTargetException.class,
                    () -> parseEntry.invoke(null, entry, 0));
            assertTrue(ex.getCause() instanceof RskJsonRpcRequestException);
            assertTrue(ex.getCause().getMessage().contains("signature r/s must be hex"));
        }
    }

    @Test
    void decodeChainId_tooLarge_throws() throws Exception {
        Method decodeChainId = AuthorizationListCodec.class.getDeclaredMethod("decodeChainId", byte[].class);
        decodeChainId.setAccessible(true);
        byte[] data = BigIntegers.asUnsignedByteArray(BigInteger.ONE.shiftLeft(256));

        InvocationTargetException ex = assertThrows(InvocationTargetException.class,
                () -> decodeChainId.invoke(null, (Object) data));
        assertTrue(ex.getCause().getMessage().contains("chain_id"), ex.getCause().getMessage());
    }

    @Test
    void validateAuthorization_chainIdTooLarge_throws() throws Exception {
        Method validate = AuthorizationListCodec.class.getDeclaredMethod(
                "validateAuthorization", SetCodeAuthorization.class);
        validate.setAccessible(true);

        SetCodeAuthorization auth = Mockito.mock(SetCodeAuthorization.class);
        Mockito.when(auth.getChainId()).thenReturn(BigInteger.ONE.shiftLeft(256));
        Mockito.when(auth.getNonce()).thenReturn(new byte[]{0x01});

        InvocationTargetException ex = assertThrows(InvocationTargetException.class,
                () -> validate.invoke(null, auth));
        assertTrue(ex.getCause().getMessage().contains("chain_id"));
    }

    @Test
    void validateAuthorization_signatureRTooLarge_throws() throws Exception {
        Method validate = AuthorizationListCodec.class.getDeclaredMethod(
                "validateAuthorization", SetCodeAuthorization.class);
        validate.setAccessible(true);

        SetCodeAuthorization auth = Mockito.mock(SetCodeAuthorization.class);
        ECDSASignature signature = Mockito.mock(ECDSASignature.class);
        Mockito.when(auth.getChainId()).thenReturn(BigInteger.ZERO);
        Mockito.when(auth.getNonce()).thenReturn(new byte[]{0x01});
        Mockito.when(auth.getSignature()).thenReturn(signature);
        Mockito.when(signature.validateComponentsWithoutV()).thenReturn(true);
        Mockito.when(signature.getR()).thenReturn(BigInteger.ONE.shiftLeft(256));
        Mockito.when(signature.getS()).thenReturn(BigInteger.ONE);

        InvocationTargetException ex = assertThrows(InvocationTargetException.class,
                () -> validate.invoke(null, auth));
        assertTrue(ex.getCause().getMessage().contains("2^256"), ex.getCause().getMessage());
    }

    @Test
    void encodeTuple_oversizedR_throws() {
        SetCodeAuthorization auth = Rskip545TestSupport.minimalAuthorization((byte) 33);
        byte[] oversized = new byte[33];
        oversized[0] = 0x01;
        SetCodeAuthorization bad = new SetCodeAuthorization(
                auth.getChainId(),
                auth.getAddress(),
                auth.getNonce(),
                ECDSASignature.fromComponents(
                        oversized,
                        BigIntegers.asUnsignedByteArray(auth.getSignature().getS()),
                        auth.getSignature().getV()));

        assertThrows(IllegalArgumentException.class,
                () -> AuthorizationListCodec.encodeTuple(bad));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static CallArguments.AuthorizationListEntry validEntry(
            SetCodeAuthorization reference,
            ECDSASignature sig) {
        CallArguments.AuthorizationListEntry entry = new CallArguments.AuthorizationListEntry();
        entry.setChainId("0x21");
        entry.setAddress(reference.getAddress().toHexString());
        entry.setNonce("0x0");
        entry.setYParity(HexUtils.toQuantityJsonHex(
                new byte[]{(byte) (sig.getV() - Transaction.LOWER_REAL_V)}));
        entry.setR(HexUtils.toQuantityJsonHex(BigIntegers.asUnsignedByteArray(sig.getR())));
        entry.setS(HexUtils.toQuantityJsonHex(BigIntegers.asUnsignedByteArray(sig.getS())));
        return entry;
    }

    private static byte[] rebuildTupleField(SetCodeAuthorization base, int fieldIndex, byte[] encodedField) {
        byte[] tuple = AuthorizationListCodec.encodeTuple(base);
        RLPList inner = RLP.decodeList(tuple);
        byte[][] fields = new byte[6][];
        for (int i = 0; i < 6; i++) {
            fields[i] = (i == fieldIndex) ? encodedField : reencodeTupleField(inner.get(i), i);
        }
        return RLP.encodeList(fields[0], fields[1], fields[2], fields[3], fields[4], fields[5]);
    }

    private static byte[] reencodeTupleField(org.ethereum.util.RLPElement element, int fieldIndex) {
        byte[] data = element.getRLPData();
        return switch (fieldIndex) {
            case 0 -> data == null || data.length == 0
                    ? RLP.encodeByte((byte) 0)
                    : RLP.encodeBigInteger(new BigInteger(1, data));
            case 1 -> data == null
                    ? RLP.encodeElement(null)
                    : RLP.encodeRskAddress(new RskAddress(data));
            case 2 -> RLP.encodeElement(data == null ? new byte[0] : data);
            case 3 -> data == null || data.length == 0
                    ? RLP.encodeElement(new byte[0])
                    : (data.length == 1 ? RLP.encodeByte(data[0]) : RLP.encodeElement(data));
            case 4, 5 -> data == null ? RLP.encodeElement(null) : RLP.encodeElement(data);
            default -> throw new IllegalArgumentException("Unexpected field index: " + fieldIndex);
        };
    }

    private static SetCodeAuthorization decodeSingleTuple(byte[] tuple) {
        return AuthorizationListCodec.decodeTuple(RLP.decode2(tuple).get(0));
    }
}
