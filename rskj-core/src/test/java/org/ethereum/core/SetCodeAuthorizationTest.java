package org.ethereum.core;

import co.rsk.core.RskAddress;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.RLP;
import org.junit.jupiter.api.Test;
import java.math.BigInteger;


import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.ethereum.config.Constants;
import org.ethereum.crypto.signature.ECDSASignature;

public class SetCodeAuthorizationTest {

    private static final BigInteger CHAIN_ID = BigInteger.valueOf(31);
    private static final RskAddress ADDRESS = new RskAddress("0000000000000000000000000000000000000001");
    private static final byte[] NONCE = new byte[] {0x01};

    @Test
    public void constructorShouldRejectNullValues() {
        assertThrows(NullPointerException.class, () -> new SetCodeAuthorization(null, ADDRESS, NONCE, validSignature()));
        assertThrows(NullPointerException.class, () -> new SetCodeAuthorization(CHAIN_ID, null, NONCE, validSignature()));
        assertThrows(NullPointerException.class, () -> new SetCodeAuthorization(CHAIN_ID, ADDRESS, null, validSignature()));
        assertThrows(NullPointerException.class, () -> new SetCodeAuthorization(CHAIN_ID, ADDRESS, NONCE, null));
    }

    @Test
    public void getNonceShouldReturnDefensiveCopy() {
        SetCodeAuthorization authorization = new SetCodeAuthorization(CHAIN_ID, ADDRESS, NONCE, validSignature());
        byte[] returnedNonce = authorization.getNonce();
        returnedNonce[0] = 0x02;
        assertArrayEquals(NONCE, authorization.getNonce());
    }

    @Test
    public void constructorShouldDefensivelyCopyNonce() {
        byte[] nonce = new byte[] {0x01};
        SetCodeAuthorization authorization = new SetCodeAuthorization(CHAIN_ID, ADDRESS, nonce, validSignature());
        nonce[0] = 0x02;
        assertArrayEquals(new byte[] {0x01}, authorization.getNonce());
    }

    @Test
    public void getSigningHashShouldReturnExpectedHash() {
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
    public void verifyNonceRangeShouldAcceptValidNonce() {
        SetCodeAuthorization authorization = new SetCodeAuthorization(CHAIN_ID, ADDRESS, new byte[] {0x01}, validSignature());
        authorization.verifyNonceRange();
    }

    @Test
    public void verifyNonceRangeShouldRejectEmptyNonce() {
        SetCodeAuthorization authorization = new SetCodeAuthorization(CHAIN_ID, ADDRESS, new byte[0], validSignature());
        IllegalStateException exception = assertThrows(IllegalStateException.class, authorization::verifyNonceRange);
        assertEquals("Nonce is empty", exception.getMessage());
    }

    @Test
    public void verifyNonceRangeShouldRejectNonceGreaterThanOrEqualToMaxNonce() {
        byte[] maxNonce = new BigInteger("FFFFFFFFFFFFFFFF", 16).toByteArray();

        SetCodeAuthorization authorization = new SetCodeAuthorization(CHAIN_ID, ADDRESS, maxNonce, validSignature());
        IllegalStateException exception = assertThrows(IllegalStateException.class, authorization::verifyNonceRange);

        assertEquals("Nonce must be < 2^64 - 1", exception.getMessage());
    }

    @Test
    public void verifyNonceRangeShouldAcceptMaxAllowedNonce() {
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
    public void verifyLowSShouldAcceptLowS() {
        SetCodeAuthorization authorization = new SetCodeAuthorization(
                CHAIN_ID,
                ADDRESS,
                NONCE,
                signatureWithS(BigInteger.ONE)
        );

        authorization.verifyLowS();
    }

    @Test
    public void verifyLowSShouldAcceptHalfCurveOrderS() {
        BigInteger halfCurveOrder = Constants.getSECP256K1N().divide(BigInteger.valueOf(2));

        SetCodeAuthorization authorization = new SetCodeAuthorization(
                CHAIN_ID,
                ADDRESS,
                NONCE,
                signatureWithS(halfCurveOrder)
        );

        authorization.verifyLowS();
    }

    @Test
    public void verifyLowSShouldRejectHighS() {
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
    public void equalsShouldReturnTrueForSameValues() {
        ECDSASignature signature = validSignature();

        SetCodeAuthorization first = new SetCodeAuthorization(CHAIN_ID, ADDRESS, NONCE, signature);
        SetCodeAuthorization second = new SetCodeAuthorization(CHAIN_ID, ADDRESS, NONCE, signature);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    public void equalsShouldReturnFalseForDifferentNonce() {
        SetCodeAuthorization first = new SetCodeAuthorization(CHAIN_ID, ADDRESS, new byte[] {0x01}, validSignature());
        SetCodeAuthorization second = new SetCodeAuthorization(CHAIN_ID, ADDRESS, new byte[] {0x02}, validSignature());

        assertNotEquals(first, second);
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
