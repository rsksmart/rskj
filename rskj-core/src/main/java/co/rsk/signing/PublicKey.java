/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

import java.util.Arrays;

/**
 * Represents an immutable secp256k1 curve public key.
 * Key is always stored in the compressed format.
 * Exposes conversion methods to different EC key implementations.
 *
 * @author Ariel Mendelzon
 */
public class PublicKey {
    private final byte[] compressedKeyBytes;

    public PublicKey(byte[] bytes) {
        // Save a copy
        this.compressedKeyBytes = validateAndCompress(bytes);
    }

    public byte[] getCompressedKeyBytes() {
        return copy(compressedKeyBytes);
    }

    public ECKey toEthKey() {
        return ECKey.fromPublicOnly(copy(compressedKeyBytes));
    }

    public BtcECKey toBtcKey() {
        return BtcECKey.fromPublicOnly(copy(compressedKeyBytes));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }

        return Arrays.equals(this.compressedKeyBytes, ((PublicKey) o).compressedKeyBytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(compressedKeyBytes);
    }

    private byte[] copy(byte[] a) {
        return Arrays.copyOf(a, a.length);
    }

    private byte[] validateAndCompress(byte[] bytes) {
        // Use ethereum's ECKey to validate and compress
        // the public key.
        ECKey publicKey = ECKey.fromPublicOnly(bytes);

        return publicKey.getPubKey(true);
    }
}
