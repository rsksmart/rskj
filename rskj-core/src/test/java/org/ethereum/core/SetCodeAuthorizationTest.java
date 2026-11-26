package org.ethereum.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.rsk.core.RskAddress;
import org.bouncycastle.util.BigIntegers;
import org.ethereum.config.Constants;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.crypto.HashUtil;
import org.ethereum.crypto.signature.ECDSASignature;
import org.ethereum.util.RLP;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;

class SetCodeAuthorizationTest {

    private static final BigInteger TWO_POW_64 = BigInteger.ONE.shiftLeft(64);
    private static final BigInteger TWO_POW_256 = BigInteger.ONE.shiftLeft(256);
    private static final BigInteger CHAIN_ID = BigInteger.valueOf(31);
    private static final RskAddress ADDRESS = new RskAddress("0000000000000000000000000000000000000001");
    private static final byte[] NONCE = new byte[] {0x01};

    @Test
    void constructorShouldRejectNullValues() {
        assertThrows(NullPointerException.class, () -> new SetCodeAuthorization(null, ADDRESS, NONCE, validSignature()));
        assertThrows(NullPointerException.class, () -> new SetCodeAuthorization(CHAIN_ID, null, NONCE, validSignature()));
        assertThrows(NullPointerException.class, () -> new SetCodeAuthorization(CHAIN_ID, ADDRESS, null, validSignature()));
        assertThrows(NullPointerException.class, () -> new SetCodeAuthorization(CHAIN_ID, ADDRESS, NONCE, null));
    }

    @Test
    void constructorShouldAcceptChainIdJustBelowTwoPow256() {
        BigInteger chainId = TWO_POW_256.subtract(BigInteger.ONE);

        assertDoesNotThrow(() -> new SetCodeAuthorization(chainId, ADDRESS, NONCE, validSignature()));
    }

    @Test
    void constructorShouldRejectChainIdAtTwoPow256() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new SetCodeAuthorization(TWO_POW_256, ADDRESS, NONCE, validSignature()));

        assertEquals("Authorization chain_id must be non-negative and less than 2^256", ex.getMessage());
    }

    @Test
    void constructorShouldRejectNegativeChainId() {
        BigInteger negative = BigInteger.valueOf(-1);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new SetCodeAuthorization(negative, ADDRESS, NONCE, validSignature()));

        assertEquals("Authorization chain_id must be non-negative and less than 2^256", ex.getMessage());
    }

    @Test
    void constructorShouldAcceptNonceJustBelowTwoPow64() {
        byte[] nonce = BigIntegers.asUnsignedByteArray(TWO_POW_64.subtract(BigInteger.ONE));

        assertDoesNotThrow(() -> new SetCodeAuthorization(CHAIN_ID, ADDRESS, nonce, validSignature()));
    }

    @Test
    void constructorShouldAcceptNonceWithLeadingZerosBelowTwoPow64() {
        byte[] nonce = new byte[] {
                0x00,
                (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
                (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xfe
        };

        SetCodeAuthorization authorization = new SetCodeAuthorization(CHAIN_ID, ADDRESS, nonce, validSignature());

        assertEquals(TWO_POW_64.subtract(BigInteger.TWO), authorization.getNonceAsInteger());
    }

    @Test
    void constructorShouldRejectNonceAtTwoPow64() {
        byte[] nonce = BigIntegers.asUnsignedByteArray(TWO_POW_64);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new SetCodeAuthorization(CHAIN_ID, ADDRESS, nonce, validSignature()));

        assertEquals("Authorization nonce must be less than 2^64", ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"r", "s"})
    void constructorShouldAcceptSignatureComponentJustBelowTwoPow256(String component) {
        ECDSASignature signature = signatureWithComponent(component, TWO_POW_256.subtract(BigInteger.ONE));

        assertDoesNotThrow(() -> new SetCodeAuthorization(CHAIN_ID, ADDRESS, NONCE, signature));
    }

    @ParameterizedTest
    @ValueSource(strings = {"r", "s"})
    void constructorShouldRejectSignatureComponentAtTwoPow256(String component) {
        ECDSASignature signature = signatureWithComponent(component, TWO_POW_256);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new SetCodeAuthorization(CHAIN_ID, ADDRESS, NONCE, signature));

        assertEquals("Authorization signature r and s must be less than 2^256", ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"r", "s"})
    void constructorShouldRejectNegativeSignatureComponent(String component) {
        ECDSASignature signature = signatureWithComponent(component, BigInteger.valueOf(-1));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new SetCodeAuthorization(CHAIN_ID, ADDRESS, NONCE, signature));

        assertEquals("Authorization signature r and s must be non-negative", ex.getMessage());
    }

    private static ECDSASignature signatureWithComponent(String component, BigInteger value) {
        return new ECDSASignature(component.equals("r") ? value : BigInteger.ONE, component.equals("s") ? value : BigInteger.ONE, Transaction.LOWER_REAL_V);
    }

    @Test
    void getNonceShouldReturnDefensiveCopy() {
        SetCodeAuthorization authorization = new SetCodeAuthorization(CHAIN_ID, ADDRESS, NONCE, validSignature());
        byte[] returnedNonce = authorization.getNonceBytes();
        returnedNonce[0] = 0x02;
        assertArrayEquals(NONCE, authorization.getNonceBytes());
    }

    @Test
    void constructorShouldDefensivelyCopyNonce() {
        byte[] nonce = new byte[] {0x01};
        SetCodeAuthorization authorization = new SetCodeAuthorization(CHAIN_ID, ADDRESS, nonce, validSignature());
        nonce[0] = 0x02;
        assertArrayEquals(new byte[] {0x01}, authorization.getNonceBytes());
    }

    @Test
    void getSigningHashShouldReturnExpectedHash() {
        SetCodeAuthorization authorization = new SetCodeAuthorization(CHAIN_ID, ADDRESS, NONCE, validSignature());

        byte[] rlpEncoded = RLP.encodeList(
                RLP.encodeBigInteger(CHAIN_ID),
                RLP.encodeElement(ADDRESS.getBytes()),
                RLP.encodeElement(NONCE)
        );

        byte[] payload = new byte[1 + rlpEncoded.length];
        payload[0] = 0x05;
        System.arraycopy(rlpEncoded, 0, payload, 1, rlpEncoded.length);

        byte[] expectedHash = HashUtil.keccak256(payload);

        assertArrayEquals(expectedHash, authorization.getSigningHash());
    }

    @Test
    void verifyNonceRangeShouldAcceptValidNonce() {
        SetCodeAuthorization authorization = new SetCodeAuthorization(CHAIN_ID, ADDRESS, new byte[] {0x01}, validSignature());
        authorization.verifyNonceRange();
    }

    @Test
    void verifyNonceRangeShouldRejectNonceGreaterThanOrEqualToMaxNonce() {
        byte[] maxNonce = new BigInteger("FFFFFFFFFFFFFFFF", 16).toByteArray();

        SetCodeAuthorization authorization = new SetCodeAuthorization(CHAIN_ID, ADDRESS, maxNonce, validSignature());
        IllegalStateException exception = assertThrows(IllegalStateException.class, authorization::verifyNonceRange);

        assertEquals("Nonce must be < 2^64 - 1", exception.getMessage());
    }

    @Test
    void verifyNonceRangeShouldAcceptMaxAllowedNonce() {
        byte[] maxAllowedNonce = new BigInteger("FFFFFFFFFFFFFFFE", 16).toByteArray();

        SetCodeAuthorization authorization = new SetCodeAuthorization(
                CHAIN_ID,
                ADDRESS,
                maxAllowedNonce,
                validSignature()
        );

        authorization.verifyNonceRange();
    }

    @Test
    void verifyLowSShouldAcceptLowS() {
        SetCodeAuthorization authorization = new SetCodeAuthorization(
                CHAIN_ID,
                ADDRESS,
                NONCE,
                signatureWithS(BigInteger.ONE)
        );

        assertDoesNotThrow(authorization::verifyLowS);
    }

    @Test
    void verifyLowSShouldAcceptJustBelowHalfCurveOrderS() {
        BigInteger justBelowHalf = Constants.getSECP256K1N().divide(BigInteger.valueOf(2)).subtract(BigInteger.ONE);

        SetCodeAuthorization authorization = new SetCodeAuthorization(
                CHAIN_ID,
                ADDRESS,
                NONCE,
                signatureWithS(justBelowHalf)
        );

        assertDoesNotThrow(authorization::verifyLowS);
    }

    @Test
    void verifyLowSShouldAcceptHalfCurveOrderS() {
        BigInteger halfCurveOrder = Constants.getSECP256K1N().divide(BigInteger.valueOf(2));

        SetCodeAuthorization authorization = new SetCodeAuthorization(
                CHAIN_ID,
                ADDRESS,
                NONCE,
                signatureWithS(halfCurveOrder)
        );

        assertDoesNotThrow(authorization::verifyLowS);
    }

    @Test
    void verifyLowSShouldRejectHighS() {
        BigInteger highS = Constants.getSECP256K1N().divide(BigInteger.valueOf(2)).add(BigInteger.ONE);

        SetCodeAuthorization authorization = new SetCodeAuthorization(
                CHAIN_ID,
                ADDRESS,
                NONCE,
                signatureWithS(highS)
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                authorization::verifyLowS
        );

        assertEquals("Signature s exceeds secp256k1n / 2", exception.getMessage());
    }

    @Test
    void equalsShouldReturnTrueForSameValues() {
        ECDSASignature signature = validSignature();

        SetCodeAuthorization first = new SetCodeAuthorization(CHAIN_ID, ADDRESS, NONCE, signature);
        SetCodeAuthorization second = new SetCodeAuthorization(CHAIN_ID, ADDRESS, NONCE, signature);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void equalsShouldReturnFalseForDifferentNonce() {
        SetCodeAuthorization first = new SetCodeAuthorization(CHAIN_ID, ADDRESS, new byte[] {0x01}, validSignature());
        SetCodeAuthorization second = new SetCodeAuthorization(CHAIN_ID, ADDRESS, new byte[] {0x02}, validSignature());
        assertNotEquals(first, second);
    }

    @Test
    void getNonceAsIntegerShouldTreatEmptyNonceAsZero() {
        SetCodeAuthorization authorization = new SetCodeAuthorization(CHAIN_ID, ADDRESS, new byte[0], validSignature());
        assertEquals(BigInteger.ZERO, authorization.getNonceAsInteger());
    }

    @Test
    void verifyNonceRangeShouldAcceptEmptyNonceAsZero() {
        SetCodeAuthorization authorization = new SetCodeAuthorization(CHAIN_ID, ADDRESS, new byte[0], validSignature());
        assertDoesNotThrow(authorization::verifyNonceRange);
    }

    @Test
    void getNonceAsIntegerShouldDecodeUnsignedNonceWithTopBitSet() {
        SetCodeAuthorization authorization =
                new SetCodeAuthorization(CHAIN_ID, ADDRESS, new byte[] {(byte) 0x80}, validSignature());

        assertEquals(BigInteger.valueOf(128), authorization.getNonceAsInteger());
    }

    @Test
    void verifyNonceRangeShouldAcceptNonce128() {
        SetCodeAuthorization authorization =
                new SetCodeAuthorization(CHAIN_ID, ADDRESS, new byte[] {(byte) 0x80}, validSignature());
        authorization.verifyNonceRange();
    }

    @Test
    void verifyNonceRangeShouldAcceptNonce255() {
        SetCodeAuthorization authorization = new SetCodeAuthorization(CHAIN_ID, ADDRESS, new byte[] {(byte) 0xff}, validSignature());
        authorization.verifyNonceRange();
    }

    @Test
    void verifyNonceRangeShouldAcceptNonce256() {
        SetCodeAuthorization authorization =
                new SetCodeAuthorization(CHAIN_ID, ADDRESS, new byte[] {0x01, 0x00}, validSignature());

        assertDoesNotThrow(authorization::verifyNonceRange);
    }

    @Test
    void verifyNonceRangeShouldRejectMaxNonce() {
        byte[] maxNonce = new byte[] {
                (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
                (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff
        };

        SetCodeAuthorization authorization = new SetCodeAuthorization(CHAIN_ID, ADDRESS, maxNonce, validSignature());
        IllegalStateException exception = assertThrows(IllegalStateException.class, authorization::verifyNonceRange);
        assertEquals("Nonce must be < 2^64 - 1", exception.getMessage());
    }

    @Test
    void getNonceAsIntegerShouldTreatZeroByteAndEmptyByteArrayAsZero() {
        SetCodeAuthorization empty =
                new SetCodeAuthorization(CHAIN_ID, ADDRESS, new byte[0], validSignature());

        SetCodeAuthorization zeroByte =
                new SetCodeAuthorization(CHAIN_ID, ADDRESS, new byte[] {0x00}, validSignature());

        assertEquals(BigInteger.ZERO, empty.getNonceAsInteger());
        assertEquals(BigInteger.ZERO, zeroByte.getNonceAsInteger());
    }

    @Test
    void verifyNonceRangeShouldAcceptMaxAllowedNonceUsingByteArray() {
        byte[] maxAllowedNonce = new byte[] {
                (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
                (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xfe
        };

        SetCodeAuthorization authorization =
                new SetCodeAuthorization(CHAIN_ID, ADDRESS, maxAllowedNonce, validSignature());

        authorization.verifyNonceRange();
    }

    @Test
    void getNonceAsIntegerShouldIgnoreLeadingZeroBytes() {
        SetCodeAuthorization authorization =
                new SetCodeAuthorization(CHAIN_ID, ADDRESS, new byte[] {0x00, 0x00, 0x01}, validSignature());

        assertEquals(BigInteger.ONE, authorization.getNonceAsInteger());
    }

    private static ECDSASignature validSignature() {
        return signatureWithS(BigInteger.ONE);
    }

    private static ECDSASignature signatureWithS(BigInteger s) {
        return ECDSASignature.fromComponents(
                BigInteger.ONE.toByteArray(),
                s.toByteArray(),
                (byte) 0
        );
    }
}
