/*
 * This file is part of RskJ
 * Copyright (C) 2020 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package org.ethereum.crypto.signature;

import org.ethereum.crypto.ECKey;

import javax.annotation.Nullable;
import java.security.SignatureException;

/**
 * Service in charge of the implementations of all the Signature related functionality.
 * Functions:
 * - Sign data (future)
 * - Recover PK from signature
 * - Verify
 */
public interface Secp256k1Service {


    /**
     * Given a piece of text and a message signature encoded in base64, returns an ECKey
     * containing the public key that was used to sign it. This can then be compared to the expected public key to
     * determine if the signature was correct.
     *
     * signature.v  => from 31 to 34 is compressed
     *              => from 27 to 30 is uncompressed
     *
     * @param messageHash a piece of human readable text that was signed
     * @param signature   The message signature
     * @return -
     * @throws SignatureException If the public key could not be recovered or if there was a signature format error.
     */
    default ECKey signatureToKey(byte[] messageHash, ECDSASignature signature) throws SignatureException {
        int header = signature.getV() & 0xFF;
        // The header byte: 0x1B = first key with even y, 0x1C = first key with odd y,
        //                  0x1D = second key with even y, 0x1E = second key with odd y
        if (header < 27 || header > 34) {
            throw new SignatureException("Header byte out of range: " + header);
        }

        boolean compressed = false;
        if (header >= 31) {
            compressed = true;
            header -= 4;
        }
        int recId = header - 27;
        ECKey key = this.recoverFromSignature(recId, signature, messageHash, compressed);
        if (key == null) {
            throw new SignatureException("Could not recover public key from signature");
        }
        return key;
    }
    /**
     * <p>Given the components of a signature and a selector value, recover and return the public key
     * that generated the signature according to the algorithm in SEC1v2 section 4.1.6.</p>
     *
     * <p>The recId is an index from 0 to 3 which indicates which of the 4 possible keys is the correct one. Because
     * the key recovery operation yields multiple potential keys, the correct key must either be stored alongside the
     * signature, or you must be willing to try each recId in turn until you find one that outputs the key you are
     * expecting.</p>
     *
     * <p>If this method returns null it means recovery was not possible and recId should be iterated.</p>
     *
     * <p>Given the above two points, a correct usage of this method is inside a for loop from 0 to 3, and if the
     * output is null OR a key that is not the one you expect, you try again with the next recId.</p>
     *
     * @param recId       Which possible key to recover.
     * @param sig         the R and S components of the signature, wrapped.
     * @param messageHash Hash of the data that was signed.
     * @param compressed  Whether or not the original pubkey was compressed.
     * @return An ECKey containing only the public part, or null if recovery wasn't possible.
     */
    @Nullable
    ECKey recoverFromSignature(int recId, ECDSASignature sig, byte[] messageHash, boolean compressed);

    /**
     * <p>Verifies the given ECDSA signature against the message bytes using the public key bytes.</p>
     *
     * <p>When using native ECDSA verification, data must be 32 bytes, and no element may be
     * larger than 520 bytes.</p>
     *
     * @param data      Hash of the data to verify.
     * @param signature signature.
     * @param pub       The public key bytes to use.
     * @return -
     */
    boolean verify(byte[] data, ECDSASignature signature, byte[] pub);

    /**
     * Utility method to check params.
     *
     * @param test
     * @param message
     */
    default void check(boolean test, String message) {
        if (!test) {
            throw new IllegalArgumentException(message);
        }
    }

}