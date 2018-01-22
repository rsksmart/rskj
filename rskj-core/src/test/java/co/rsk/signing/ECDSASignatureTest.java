package co.rsk.signing;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.Sha256Hash;
import org.ethereum.crypto.ECKey;
import org.junit.Assert;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;

public class ECDSASignatureTest {
    @Test
    public void FromToEth() {
        ECKey key = ECKey.fromPrivate(BigInteger.valueOf(12345));

        byte[] toSign = Hex.decode("00112233445566778899aabbccddeeff");

        ECKey.ECDSASignature sig1 = key.sign(toSign);

        Assert.assertFalse(ECKey.verify(Hex.decode("11223344"), sig1, key.getPubKey()));
        Assert.assertTrue(ECKey.verify(toSign, sig1, key.getPubKey()));

        ECDSASignature sig2 = ECDSASignature.fromEthSignature(sig1);

        ECKey.ECDSASignature sig3 = sig2.toRskSignature();

        Assert.assertFalse(ECKey.verify(Hex.decode("11223344"), sig3, key.getPubKey()));
        Assert.assertTrue(ECKey.verify(toSign, sig3, key.getPubKey()));
    }

    @Test
    public void FromToBtc() {
        BtcECKey key = BtcECKey.fromPrivate(BigInteger.valueOf(12345));

        Sha256Hash toSign = Sha256Hash.of(Hex.decode("00112233445566778899aabbccddeeff"));

        BtcECKey.ECDSASignature sig1 = key.sign(toSign);

        Assert.assertFalse(BtcECKey.verify(Hex.decode("11223344"), sig1, key.getPubKey()));
        Assert.assertTrue(BtcECKey.verify(toSign.getBytes(), sig1, key.getPubKey()));

        ECDSASignature sig2 = ECDSASignature.fromBtcSignature(sig1);

        BtcECKey.ECDSASignature sig3 = sig2.toBtcSignature();

        Assert.assertFalse(BtcECKey.verify(Hex.decode("11223344"), sig3, key.getPubKey()));
        Assert.assertTrue(BtcECKey.verify(toSign.getBytes(), sig3, key.getPubKey()));
    }
}
