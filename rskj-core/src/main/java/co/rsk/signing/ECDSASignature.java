package co.rsk.signing;

import co.rsk.bitcoinj.core.BtcECKey;
import org.ethereum.crypto.ECKey;

import java.math.BigInteger;

/**
 * An ECDSA signature.
 *
 * It's just a copy of what ECKey.ECDSASignature does,
 * so that we can convert to other ECDSA classes too
 * (e.g., Ethereum or BTC ECDSA signatures) and don't ultimately depend
 * upon ethereum or BTC's specific implementations since the usage
 * of this is broader.
 *
 * @author Ariel Mendelzon
 */
public class ECDSASignature {
    private final BigInteger r;
    private final BigInteger s;
    // For compatibility with ECKey.ECDSASignature
    private byte v;

    private ECDSASignature(BigInteger r, BigInteger s) {
        this.r = r;
        this.s = s;
        this.v = 0;
    }

    private ECDSASignature(BigInteger r, BigInteger s, byte v) {
        this(r, s);
        this.v = v;
    }

    public static ECDSASignature fromEthSignature(ECKey.ECDSASignature signature) {
        return new ECDSASignature(signature.r, signature.s, signature.v);
    }

    public static ECDSASignature fromBtcSignature(BtcECKey.ECDSASignature signature) {
        return new ECDSASignature(signature.r, signature.s);
    }

    public BigInteger getR() {
        return r;
    }

    public BigInteger getS() {
        return s;
    }

    public byte getV() { return v; }

    public ECKey.ECDSASignature toRskSignature() {
        ECKey.ECDSASignature result = new ECKey.ECDSASignature(r, s);
        result.v = v;
        return result;
    }

    public BtcECKey.ECDSASignature toBtcSignature() {
        return new BtcECKey.ECDSASignature(r, s);
    }
}
