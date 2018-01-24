/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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
    private final byte v;

    private ECDSASignature(BigInteger r, BigInteger s) {
        this(r, s, (byte) 0);
    }

    private ECDSASignature(BigInteger r, BigInteger s, byte v) {
        this.r = r;
        this.s = s;
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
