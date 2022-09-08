package org.ethereum.crypto.signature;

import org.bitcoin.Secp256k1Context;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class Secp256k1ServiceNativeTest extends Secp256k1ServiceTest {

    private static final BigInteger BIG_NUMBER = new BigInteger(Hex.decode("3ecb44df2159c26e0f995712d4f39b6f6e499b40749b1cf1246c37f9516cb6a4"));

    @BeforeAll
    public static void beforeMethod() {
        assumeTrue(Secp256k1Context.isEnabled());
    }

    public Secp256k1ServiceNativeTest() {
        super(new Secp256k1ServiceNative());
    }

    @Test
    public void testConcatenate() {
        concatenateAssert(BigInteger.ZERO, BigInteger.ZERO);
        concatenateAssert(BigInteger.ZERO, BigInteger.ONE);
        concatenateAssert(BigInteger.ZERO, BigInteger.TEN);
        concatenateAssert(BigInteger.ZERO, BIG_NUMBER);
        concatenateAssert(BigInteger.ONE, BigInteger.ZERO);
        concatenateAssert(BigInteger.ONE, BigInteger.ONE);
        concatenateAssert(BigInteger.ONE, BigInteger.TEN);
        concatenateAssert(BigInteger.ONE, BIG_NUMBER);
        concatenateAssert(BigInteger.TEN, BigInteger.ZERO);
        concatenateAssert(BigInteger.TEN, BigInteger.ONE);
        concatenateAssert(BigInteger.TEN, BigInteger.TEN);
        concatenateAssert(BigInteger.TEN, BIG_NUMBER);
        concatenateAssert(BIG_NUMBER, BigInteger.ZERO);
        concatenateAssert(BIG_NUMBER, BigInteger.ONE);
        concatenateAssert(BIG_NUMBER, BigInteger.TEN);
        concatenateAssert(BIG_NUMBER, BIG_NUMBER);
    }

    private void concatenateAssert(BigInteger r, BigInteger s) {
        final ECDSASignature ecdsaSignature = ECDSASignature.fromComponents(r.toByteArray(), s.toByteArray());
        final byte[] result = this.getSecp256k1().concatenate(ecdsaSignature);
        assertEquals(r, new BigInteger(Arrays.copyOfRange(result, 0, 32)));
        assertEquals(s, new BigInteger(Arrays.copyOfRange(result, 32, 64)));
    }

    protected Secp256k1ServiceNative getSecp256k1() {
        return (Secp256k1ServiceNative) super.getSecp256k1();
    }
}
