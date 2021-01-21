/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package org.ethereum.crypto;
/**
 * Copyright 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.modes.SICBlockCipher;
import org.bouncycastle.crypto.params.*;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.crypto.signers.HMacDSAKCalculator;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.BigIntegers;
import org.ethereum.crypto.signature.Secp256k1;
import org.ethereum.util.ByteUtil;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.util.Arrays;

import static org.ethereum.util.ByteUtil.bigIntegerToBytes;

/**
 * <p>Represents an elliptic curve public and (optionally) private key, usable for digital signatures but not encryption.
 * Creating a new ECKey with the empty constructor will generate a new random keypair. Other static methods can be used
 * when you already have the public or private parts. If you create a key with only the public part, you can check
 * signatures but not create them.</p>
 *
 * <p>The ECDSA algorithm supports <i>key recovery</i> in which a signature plus a couple of discriminator bits can
 * be reversed to find the public key used to calculate it. This can be convenient when you have a message and a
 * signature and want to find out who signed it, rather than requiring the user to provide the expected identity.</p>
 *
 * <p>A key can be <i>compressed</i> or <i>uncompressed</i>. This refers to whether the public key is represented
 * when encoded into bytes as an (x, y) coordinate on the elliptic curve, or whether it's represented as just an X
 * co-ordinate and an extra byte that carries a sign bit. With the latter form the Y coordinate can be calculated
 * dynamically, however, <b>because the binary serialization is different the address of a key changes if its
 * compression status is changed</b>. If you deviate from the defaults it's important to understand this: money sent
 * to a compressed version of the key will have a different address to the same key in uncompressed form. Whether
 * a public key is compressed or not is recorded in the SEC binary serialisation format, and preserved in a flag in
 * this class so round-tripping preserves state. Unless you're working with old software or doing unusual things, you
 * can usually ignore the compressed/uncompressed distinction.</p>
 *
 * This code is borrowed from the bitcoinj project and altered to fit Ethereum.<br>
 * See <a href="https://github.com/bitcoinj/bitcoinj/blob/master/core/src/main/java/com/google/bitcoin/core/ECKey.java">
 * bitcoinj on GitHub</a>.
 */
public class ECKey {

    /**
     * The parameters of the secp256k1 curve that Ethereum uses.
     */
    public static final ECDomainParameters CURVE;

    /**
     * Equal to CURVE.getN().shiftRight(1), used for canonicalising the S value of a signature. If you aren't
     * sure what this is about, you can ignore it.
     */
    public static final BigInteger HALF_CURVE_ORDER;

    private static final SecureRandom secureRandom;

    static {
        // All clients must agree on the curve to use by agreement. Ethereum uses secp256k1.
        X9ECParameters params = SECNamedCurves.getByName("secp256k1");
        CURVE = new ECDomainParameters(params.getCurve(), params.getG(), params.getN(), params.getH());
        HALF_CURVE_ORDER = params.getN().shiftRight(1);
        secureRandom = new SecureRandom();
    }

    // The two parts of the key. If "priv" is set, "pub" can always be calculated. If "pub" is set but not "priv", we
    // can only verify signatures not make them.
    // TODO: Redesign this class to use consistent internals and more efficient serialization.
    private final BigInteger priv;
    private final ECPoint pub;

    // Transient because it's calculated on demand.
    private byte[] pubKeyHash;
    private byte[] nodeId;

    /**
     * Generates an entirely new keypair. Point compression is used so the resulting public key will be 33 bytes
     * (32 for the co-ordinate and 1 byte to represent the y bit).
     */
    public ECKey() {
        this(secureRandom);
    }

    /**
     * Generates an entirely new keypair with the given {@link SecureRandom} object. Point compression is used so the
     * resulting public key will be 33 bytes (32 for the co-ordinate and 1 byte to represent the y bit).
     *
     * @param secureRandom -
     */
    public ECKey(SecureRandom secureRandom) {
        ECKeyPairGenerator generator = new ECKeyPairGenerator();
        ECKeyGenerationParameters keygenParams = new ECKeyGenerationParameters(CURVE, secureRandom);
        generator.init(keygenParams);
        AsymmetricCipherKeyPair keypair = generator.generateKeyPair();
        ECPrivateKeyParameters privParams = (ECPrivateKeyParameters) keypair.getPrivate();
        ECPublicKeyParameters pubParams = (ECPublicKeyParameters) keypair.getPublic();
        priv = privParams.getD();
        pub = CURVE.getCurve().decodePoint(pubParams.getQ().getEncoded(true));
    }

    public ECKey(@Nullable BigInteger priv, ECPoint pub) {
        this.priv = priv;
        if (pub == null) {
            throw new IllegalArgumentException("Public key may not be null");
        }
        this.pub = pub;
    }

    /**
     * Utility for compressing an elliptic curve point. Returns the same point if it's already compressed.
     * See the ECKey class docs for a discussion of point compression.
     *
     * @param uncompressed -
     *
     * @return -
     */
    public static ECPoint compressPoint(ECPoint uncompressed) {
        return CURVE.getCurve().decodePoint(uncompressed.getEncoded(true));
    }

    /**
     * Utility for decompressing an elliptic curve point. Returns the same point if it's already compressed.
     * See the ECKey class docs for a discussion of point compression.
     *
     * @param compressed -
     *
     * @return  -
     */
    public static ECPoint decompressPoint(ECPoint compressed) {
        return CURVE.getCurve().decodePoint(compressed.getEncoded(false));
    }

    /**
     * Creates an ECKey given the private key only.  The public key is calculated from it (this is slow). Note that
     * the resulting public key is compressed.
     *
     * @param privKey -
     *
     *
     * @return  -
     */
    public static ECKey fromPrivate(BigInteger privKey) {
        return new ECKey(privKey, compressPoint(CURVE.getG().multiply(privKey)));
    }

    /**
     * Creates an ECKey given the private key only.  The public key is calculated from it (this is slow). The resulting
     * public key is compressed.
     *
     * @param privKeyBytes -
     *
     * @return -
     */
    public static ECKey fromPrivate(byte[] privKeyBytes) {
        return fromPrivate(new BigInteger(1, privKeyBytes));
    }

    /**
     * Creates an ECKey that cannot be used for signing, only verifying signatures, from the given point. The
     * compression state of pub will be preserved.
     *
     * @param pub -
     * @return -
     */
    public static ECKey fromPublicOnly(ECPoint pub) {
        return new ECKey(null, pub);
    }

    /**
     * Creates an ECKey that cannot be used for signing, only verifying signatures, from the given encoded point.
     * The compression state of pub will be preserved.
     *
     * @param pub -
     * @return -
     */
    public static ECKey fromPublicOnly(byte[] pub) {
        return new ECKey(null, CURVE.getCurve().decodePoint(pub));
    }

    /**
     * Returns a copy of this key, but with the public point represented in uncompressed form. Normally you would
     * never need this: it's for specialised scenarios or when backwards compatibility in encoded form is necessary.
     *
     * @return  -
     */

    public ECKey decompress() {
        return new ECKey(priv, decompressPoint(pub));
    }

    /**
     * Returns true if this key doesn't have access to private key bytes. This may be because it was never
     * given any private key bytes to begin with (a watching key).
     *
     * @return -
     */
    public boolean isPubKeyOnly() {
        return priv == null;
    }

    /**
     * Returns true if this key has access to private key bytes. Does the opposite of
     * {@link #isPubKeyOnly()}.
     *
     * @return  -
     */
    public boolean hasPrivKey() {
        return priv != null;
    }

    /**
     * Returns public key bytes from the given private key. To convert a byte array into a BigInteger, use <tt>
     * new BigInteger(1, bytes);</tt>
     *
     * @param privKey -
     * @param compressed -
     * @return -
     */
    public static byte[] publicKeyFromPrivate(BigInteger privKey, boolean compressed) {
        ECPoint point = CURVE.getG().multiply(privKey);
        return point.getEncoded(compressed);
    }

    /**
     * Gets the hash160 form of the public key (as seen in addresses).
     *
     * When we hash the PK to get the address -> Keccak256(PK),
     * Some things are important:
     *  - 12 bytes are omitted, to get 20bytes for the address.
     *  - first byte of the public key is omitted (generally that byte is the format of the PK - 2/3/4)
     *  - In case of PoI when PK = [0], as the first byte is omitted, we hash an empty byte array.
     *
     * @return -
     */
    public byte[] getAddress() {
        if (pubKeyHash == null) {
            byte[] pubBytes = this.pub.getEncoded(false);
            pubKeyHash = HashUtil.keccak256Omit12(Arrays.copyOfRange(pubBytes, 1, pubBytes.length));
        }
        return pubKeyHash;
    }

    /**
     * Generates the NodeID based on this key, that is the public key without first format byte
     */
    public byte[] getNodeId() {
        if (nodeId == null) {
            byte[] nodeIdWithFormat = getPubKey();
            nodeId = new byte[nodeIdWithFormat.length - 1];
            System.arraycopy(nodeIdWithFormat, 1, nodeId, 0, nodeId.length);
        }
        return nodeId;
    }

    /**
     * Gets the raw public key value. This appears in transaction scriptSigs. Note that this is <b>not</b> the same
     * as the pubKeyHash/address.
     *
     * @return  -
     */
    public byte[] getPubKey() {
        return pub.getEncoded(false);
    }

    public byte[] getPubKey(boolean compressed) {
        return pub.getEncoded(compressed);
    }

    /**
     * Gets the public key in the form of an elliptic curve point object from Bouncy Castle.
     *
     * @return  -
     */
    public ECPoint getPubKeyPoint() {
        return pub;
    }

    public boolean equalsPub(ECKey other) {
        return this.pub.equals(other.pub);
    }

    /**
     * Gets the private key in the form of an integer field element. The public key is derived by performing EC
     * point addition this number of times (i.e. point multiplying).
     *
     *
     * @return  -
     *
     * @throws java.lang.IllegalStateException if the private key bytes are not available.
     */
    public BigInteger getPrivKey() {
        if (priv == null) {
            throw new MissingPrivateKeyException();
        }
        return priv;
    }

    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append("pub:").append(ByteUtil.toHexString(pub.getEncoded(false)));
        return b.toString();
    }

    /**
     * Groups the two components that make up a signature, and provides a way to encode to Base64 form, which is
     * how ECDSA signatures are represented when embedded in other data structures in the Ethereum protocol. The raw
     * components can be useful for doing further EC maths on them.
     *
     * @deprecated( in favor of {@link org.ethereum.crypto.signature.ECDSASignature})
     */
    @Deprecated
    public static class ECDSASignature {
        /**
         * The two components of the signature.
         */
        public final BigInteger r;
        public final BigInteger s;
        public byte v;

        /**
         * Constructs a signature with the given components. Does NOT automatically canonicalise the signature.
         *
         * @param r -
         * @param s -
         */
        public ECDSASignature(BigInteger r, BigInteger s) {
            this.r = r;
            this.s = s;
        }

        /**
         *t
         * @param r
         * @param s
         * @return -
         */
        private static ECDSASignature fromComponents(byte[] r, byte[] s) {
            return new ECDSASignature(new BigInteger(1, r), new BigInteger(1, s));
        }

        /**
         *
         * @param r -
         * @param s -
         * @param v -
         * @return -
         */
        public static ECDSASignature fromComponents(byte[] r, byte[] s, byte v) {
            ECDSASignature signature = fromComponents(r, s);
            signature.v = v;
            return signature;
        }

        /**
         *
         * @param r -
         * @param s -
         * @param hash - the hash used to compute this signature
         * @param pub - public key bytes, used to calculate the recovery byte 'v'
         * @return -
         */
        public static ECDSASignature fromComponentsWithRecoveryCalculation(byte[] r, byte[] s, byte[] hash, byte[] pub) {
            return ECDSASignature.fromSignature(org.ethereum.crypto.signature.ECDSASignature.fromComponentsWithRecoveryCalculation(r, s, hash, pub));
        }

        /**
         * Only for compatibility should be removed with the entire deprecated class.
         * @param sig
         * @return
         */
        private static ECDSASignature fromSignature(org.ethereum.crypto.signature.ECDSASignature sig) {
            ECDSASignature result = new ECDSASignature(sig.getR(), sig.getS());
            result.v = sig.getV();
            return result;
        }

        public boolean validateComponents() {
            return org.ethereum.crypto.signature.ECDSASignature.validateComponents(r, s, v);
        }

        /**
         * Will automatically adjust the S component to be less than or equal to half the curve order, if necessary.
         * This is required because for every signature (r,s) the signature (r, -s (mod N)) is a valid signature of
         * the same message. However, we dislike the ability to modify the bits of a Ethereum transaction after it's
         * been signed, as that violates various assumed invariants. Thus in future only one of those forms will be
         * considered legal and the other will be banned.
         *
         * @return  -
         */
        public ECDSASignature toCanonicalised() {
            if (s.compareTo(HALF_CURVE_ORDER) > 0) {
                // The order of the curve is the number of valid points that exist on that curve. If S is in the upper
                // half of the number of valid points, then bring it back to the lower half. Otherwise, imagine that
                //    N = 10
                //    s = 8, so (-8 % 10 == 2) thus both (r, 8) and (r, 2) are valid solutions.
                //    10 - 8 == 2, giving us always the latter solution, which is canonical.
                return new ECDSASignature(r, CURVE.getN().subtract(s));
            } else {
                return this;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }

            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ECDSASignature signature = (ECDSASignature) o;

            if (!r.equals(signature.r)) {
                return false;
            }

            if (!s.equals(signature.s)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = r.hashCode();
            result = 31 * result + s.hashCode();
            return result;
        }
    }

    /**
     * Signs the given hash and returns the R and S components as BigIntegers
     * and put them in ECDSASignature
     *
     * @param input to sign
     * @return ECDSASignature signature that contains the R and S components
     */
    public ECDSASignature doSign(byte[] input) {
        // No decryption of private key required.
        if (priv == null) {
            throw new MissingPrivateKeyException();
        }
        ECDSASigner signer = new ECDSASigner(new HMacDSAKCalculator(new SHA256Digest()));
        ECPrivateKeyParameters privKey = new ECPrivateKeyParameters(priv, CURVE);
        signer.init(true, privKey);
        BigInteger[] components = signer.generateSignature(input);
        return new ECDSASignature(components[0], components[1]).toCanonicalised();
    }


    /**
     * Takes the sha3 hash (32 bytes) of data and returns the ECDSA signature
     *
     * @param messageHash -
     * @return -
     * @throws IllegalStateException if this ECKey does not have the private part.
     */
    public ECDSASignature sign(byte[] messageHash) {
        if (priv == null) {
            throw new MissingPrivateKeyException();
        }
        ECDSASignature sig = doSign(messageHash);
        // Now we have to work backwards to figure out the recId needed to recover the signature.
        int recId = -1;
        for (int i = 0; i < 4; i++) {
            ECKey k = Secp256k1.getInstance().recoverFromSignature(i, org.ethereum.crypto.signature.ECDSASignature.fromSignature(sig), messageHash, false);
            if (k != null && k.pub.equals(pub)) {
                recId = i;
                break;
            }
        }
        if (recId == -1) {
            throw new RuntimeException("Could not construct a recoverable key. This should never happen.");
        }
        sig.v = (byte) (recId + 27);
        return sig;
    }


    /**
     * Given a piece of text and a message signature encoded in base64, returns an ECKey
     * containing the public key that was used to sign it. This can then be compared to the expected public key to
     * determine if the signature was correct.
     *
     * @deprecated( in favor of {@link org.ethereum.crypto.signature.Secp256k1Service#signatureToKey(byte[], org.ethereum.crypto.signature.ECDSASignature)} )
     *
     * @param messageHash a piece of human readable text that was signed
     * @param signature The message signature
     *
     * @return -
     * @throws java.security.SignatureException If the public key could not be recovered or if there was a signature format error.
     */
    @Deprecated
    public static ECKey signatureToKey(byte[] messageHash, ECDSASignature signature) throws SignatureException {
        return Secp256k1.getInstance().signatureToKey(messageHash, org.ethereum.crypto.signature.ECDSASignature.fromSignature(signature));
    }

    /**
     * Decrypt cipher by AES in SIC(also know as CTR) mode
     *
     * @param cipher -proper cipher
     * @return decrypted cipher, equal length to the cipher.
     */
    public byte[] decryptAES(byte[] cipher){

        if (priv == null) {
            throw new MissingPrivateKeyException();
        }

        AESEngine engine = new AESEngine();
        SICBlockCipher ctrEngine = new SICBlockCipher(engine);

        KeyParameter key = new KeyParameter(BigIntegers.asUnsignedByteArray(priv));
        ParametersWithIV params = new ParametersWithIV(key, new byte[16]);

        ctrEngine.init(false, params);

        int i = 0;
        byte[] out = new byte[cipher.length];
        while(i < cipher.length){
            ctrEngine.processBlock(cipher, i, out, i);
            i += engine.getBlockSize();
            if (cipher.length - i  < engine.getBlockSize()) {
                break;
            }
        }

        // process left bytes
        if (cipher.length - i > 0){
            byte[] tmpBlock = new byte[16];
            System.arraycopy(cipher, i, tmpBlock, 0, cipher.length - i);
            ctrEngine.processBlock(tmpBlock, 0, tmpBlock, 0);
            System.arraycopy(tmpBlock, 0, out, i, cipher.length - i);
        }

        return out;
    }



    /**
     * <p>Verifies the given ECDSA signature against the message bytes using the public key bytes.</p>
     *
     * <p>When using native ECDSA verification, data must be 32 bytes, and no element may be
     * larger than 520 bytes.</p>
     * @deprecated( in favor of {@link org.ethereum.crypto.signature.Secp256k1Service#verify(byte[], org.ethereum.crypto.signature.ECDSASignature, byte[])})
     *
     * @param data Hash of the data to verify.
     * @param signature signature.
     * @param pub The public key bytes to use.
     *
     * @return -
     */
    @Deprecated
    public static boolean verify(byte[] data, ECDSASignature signature, byte[] pub) {
        return Secp256k1.getInstance().verify(data, org.ethereum.crypto.signature.ECDSASignature.fromSignature(signature), pub);
    }

    /**
     * Verifies the given R/S pair (signature) against a hash using the public key.
     *
     * @deprecated( in favor of {@link #verify(byte[], org.ethereum.crypto.signature.ECDSASignature)})
     *
     * @param sigHash -
     * @param signature -
     * @return -
     */
    @Deprecated
    public boolean verify(byte[] sigHash, ECDSASignature signature) {
        return Secp256k1.getInstance().verify(sigHash, org.ethereum.crypto.signature.ECDSASignature.fromSignature(signature), getPubKey());
    }

    /**
     * Verifies the given R/S pair (signature) against a hash using the public key.
     *
     * @param sigHash -
     * @param signature -
     * @return -
     */
    public boolean verify(byte[] sigHash, org.ethereum.crypto.signature.ECDSASignature signature) {
        return Secp256k1.getInstance().verify(sigHash, signature, getPubKey());
    }


    /**
     * Returns true if this pubkey is canonical, i.e. the correct length taking into account compression.
     *
     * @return -
     */
    public boolean isPubKeyCanonical() {
        return isPubKeyCanonical(pub.getEncoded(false));
    }


    /**
     * Returns true if the given pubkey is canonical, i.e. the correct length taking into account compression.
     * @param pubkey -
     * @return -
     */
    public static boolean isPubKeyCanonical(byte[] pubkey) {
        if (pubkey[0] == 0x04) {
            // Uncompressed pubkey
            if (pubkey.length != 65) {
                return false;
            }
        } else if (pubkey[0] == 0x02 || pubkey[0] == 0x03) {
            // Compressed pubkey
            if (pubkey.length != 33) {
                return false;
            }
        } else {
            return false;
        }
        return true;
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
     * @deprecated (in favor of {@link org.ethereum.crypto.signature.Secp256k1Service#recoverFromSignature(int, org.ethereum.crypto.signature.ECDSASignature, byte[], boolean)}
     *
     * @param recId Which possible key to recover.
     * @param sig the R and S components of the signature, wrapped.
     * @param messageHash Hash of the data that was signed.
     * @param compressed Whether or not the original pubkey was compressed.
     * @return An ECKey containing only the public part, or null if recovery wasn't possible.
     */
    @Deprecated
    @Nullable
    public static ECKey recoverFromSignature(int recId, ECDSASignature sig, byte[] messageHash, boolean compressed) {
        return Secp256k1.getInstance().recoverFromSignature(recId, org.ethereum.crypto.signature.ECDSASignature.fromSignature(sig), messageHash, compressed);
    }

    /**
     * Returns a 32 byte array containing the private key, or null if the key is encrypted or public only
     *
     *  @return  -
     */
    @Nullable
    public byte[] getPrivKeyBytes() {
        return bigIntegerToBytes(priv, 32);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || !(o instanceof ECKey)) {
            return false;
        }

        ECKey ecKey = (ECKey) o;

        if (priv != null && !priv.equals(ecKey.priv)) {
            return false;
        }

        if (pub != null && !pub.equals(ecKey.pub)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        // Public keys are random already so we can just use a part of them as the hashcode. Read from the start to
        // avoid picking up the type code (compressed vs uncompressed) which is tacked on the end.
        byte[] bits = getPubKey(true);
        return (bits[0] & 0xFF) | ((bits[1] & 0xFF) << 8) | ((bits[2] & 0xFF) << 16) | ((bits[3] & 0xFF) << 24);
    }

    @SuppressWarnings("serial")
    public static class MissingPrivateKeyException extends RuntimeException {
    }

}
