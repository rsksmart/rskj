package org.ethereum.crypto.signature;

import org.bitcoin.Secp256k1Context;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.ethereum.crypto.ECKey;
import org.bitcoin.NativeSecp256k1;
import org.bitcoin.NativeSecp256k1Exception;

import java.math.BigInteger;
import java.util.Arrays;
import java.security.SignatureException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;

import org.junit.jupiter.api.Tag;

class Secp256k1ServiceNativeFuzzTest extends Secp256k1ServiceTest {
    @BeforeAll
     static void beforeMethod() {
        assumeTrue(Secp256k1Context.isEnabled());
    }

    public Secp256k1ServiceNativeFuzzTest() {
        super(new Secp256k1ServiceNative());
    }

    protected Secp256k1ServiceNative getSecp256k1() {
        return (Secp256k1ServiceNative) super.getSecp256k1();
    }

    @Tag("Secp256k1ServiceNativeFuzzRecoverFromSignatureRecId")
    @FuzzTest
    void fuzzRecoverFromSignatureRecId(FuzzedDataProvider data) throws SignatureException {
        String dataHashed = "53cb8e93030183c5ba198433e8cd1f013f3d113e0f4d1756de0d1f124ead155a";
        String rString = "f0e8aab4fdd83382292a1bbc5480e2ae8084dc245f000f4bc4534d383a3a7919";
        String sString = "a30891f2176bd87b4a3ac5c75167f2442453c17c6e2fbfb36c3b972ee67a4c2d";
        
        int recId = data.consumeInt();
        ECKey ecKey;
   
        byte[] dbHash = Hex.decode(dataHashed);
        ECDSASignature signature = ECDSASignature.fromComponents(Hex.decode(rString), Hex.decode(sString));
        // for (recId = 0; recId <= 3; recId++)
        {
            try {
                ecKey = getSecp256k1().recoverFromSignature(recId, signature, dbHash, false);    
            } catch (java.lang.IllegalArgumentException e) {
                ;
            }
            
        }
        // assertEquals(pubKeyString, Hex.toHexString(ecKey.getPubKey()));
    }

    @Tag("Secp256k1ServiceNativeFuzzRecoverFromSignatureDirectly")
    @FuzzTest
    void fuzzRecoverFromSignatureDirectly(FuzzedDataProvider data) throws SignatureException {
        int recId;
        byte[] pbKey;
        byte[] sigBytes = data.consumeBytes(64);
        byte[] messageHash = data.consumeBytes(32);
        if (sigBytes.length != 64 || messageHash.length != 32) {
            return;
        }
        
        for (recId = 0; recId <= 3; recId++) {
            try {
                NativeSecp256k1.isInfinity(sigBytes, messageHash, recId);
            } catch (NativeSecp256k1Exception e) {
                ;
            }
            try {
                pbKey = NativeSecp256k1.ecdsaRecover(sigBytes, messageHash, recId, true);
            } catch (NativeSecp256k1Exception e) {
                ;
            }
            try {
                pbKey = NativeSecp256k1.ecdsaRecover(sigBytes, messageHash, recId, false);
            } catch (NativeSecp256k1Exception e) {
                ;
            }
        }
    }

}
