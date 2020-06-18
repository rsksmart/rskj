package org.ethereum.crypto.signature;

import org.bitcoin.Secp256k1Context;
import org.junit.BeforeClass;

import static org.junit.Assume.assumeTrue;

public class Secp256k1ServiceNativeTest extends Secp256k1ServiceTest {

    @BeforeClass
    public static void beforeMethod() {
        assumeTrue(Secp256k1Context.isEnabled());
    }

    public Secp256k1ServiceNativeTest() {
        super(new Secp256k1ServiceNative());
    }
}
