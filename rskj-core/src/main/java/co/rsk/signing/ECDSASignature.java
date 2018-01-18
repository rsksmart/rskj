package co.rsk.signing;

import co.rsk.bitcoinj.core.BtcECKey;
import org.ethereum.crypto.ECKey;

import java.math.BigInteger;

/**
 * An ECDSA signature.
 *
 * It's just a wrapper around org.ethereum.crypto.ECKey.ECDSASignature,
 * but the idea is that we can convert to other ECDSA classes too
 * (e.g., BTC ECDSA signatures) and don't ultimately depend
 * upon ethereum's specific implementation since the usage
 * of this is broader.
 *
 * @author Ariel Mendelzon
 */
public class ECDSASignature {
    private final ECKey.ECDSASignature signature;

    private ECDSASignature(BigInteger r, BigInteger s) {
        this.signature = new ECKey.ECDSASignature(r, s);
    }

    public static ECDSASignature fromComponents(BigInteger r, BigInteger s) {
        return new ECDSASignature(r, s);
    }

    public static ECDSASignature fromEthSignature(ECKey.ECDSASignature signature) {
        return ECDSASignature.fromComponents(signature.r, signature.s);
    }

    public BigInteger getR() {
        return signature.r;
    }

    public BigInteger getS() {
        return signature.s;
    }

    public ECKey.ECDSASignature toRskSignature() {
        return new ECKey.ECDSASignature(signature.r, signature.s);
    }

    public BtcECKey.ECDSASignature toBtcSignature() {
        return new BtcECKey.ECDSASignature(signature.r, signature.s);
    }
}
