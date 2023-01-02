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

import org.bouncycastle.crypto.*;
import org.bouncycastle.crypto.generators.EphemeralKeyPairGenerator;
import org.bouncycastle.crypto.params.*;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.BigIntegers;
import org.bouncycastle.util.Pack;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;

/**
 * Support class for constructing integrated encryption cipher
 * for doing basic message exchanges on top of key agreement ciphers.
 * Follows the description given in IEEE Std 1363a with a couple of changes
 * specific to Ethereum:
 * - Hash the MAC key before use
 * - Include the encryption iv in the MAC computation
 */
public class EthereumIESEngine
{
    private final Digest hash;
    BasicAgreement agree;
    DerivationFunction kdf;
    Mac mac;
    BufferedBlockCipher cipher;
    byte[] macBuf;

    boolean forEncryption;
    CipherParameters privParam;
    CipherParameters pubParam;

    IESParameters param;

    byte[] v;
    private EphemeralKeyPairGenerator keyPairGenerator;
    private KeyParser keyParser;
    private byte[] iv;
    boolean hashK2 = true;

    /**
     * set up for use with stream mode, where the key derivation function
     * is used to provide a stream of bytes to xor with the message.
     *  @param agree the key agreement used as the basis for the encryption
     * @param kdf    the key derivation function used for byte generation
     * @param mac    the message authentication code generator for the message
     * @param hash   hash ing function
     * @param cipher the actual cipher
     */
    public EthereumIESEngine(
            BasicAgreement agree,
            DerivationFunction kdf,
            Mac mac, Digest hash, BufferedBlockCipher cipher)
    {
        this.agree = agree;
        this.kdf = kdf;
        this.mac = mac;
        this.hash = hash;
        this.macBuf = new byte[mac.getMacSize()];
        this.cipher = cipher;
    }


    public void setHashMacKey(boolean hashK2) {
        this.hashK2 = hashK2;
    }

    /**
     * Initialise the encryptor.
     *
     * @param forEncryption whether or not this is encryption/decryption.
     * @param privParam     our private key parameters
     * @param pubParam      the recipient's/sender's public key parameters
     * @param params        encoding and derivation parameters, may be wrapped to include an iv for an underlying block cipher.
     */
    public void init(
        boolean forEncryption,
        CipherParameters privParam,
        CipherParameters pubParam,
        CipherParameters params)
    {
        this.forEncryption = forEncryption;
        this.privParam = privParam;
        this.pubParam = pubParam;
        this.v = new byte[0];

        extractParams(params);
    }


    /**
     * Initialise the encryptor.
     *
     * @param publicKey      the recipient's/sender's public key parameters
     * @param params         encoding and derivation parameters, may be wrapped to include an iv for an underlying block cipher.
     * @param ephemeralKeyPairGenerator             the ephemeral key pair generator to use.
     */
    public void init(AsymmetricKeyParameter publicKey, CipherParameters params, EphemeralKeyPairGenerator ephemeralKeyPairGenerator)
    {
        this.forEncryption = true;
        this.pubParam = publicKey;
        this.keyPairGenerator = ephemeralKeyPairGenerator;

        extractParams(params);
    }

    /**
     * Initialise the encryptor.
     *
     * @param privateKey      the recipient's private key.
     * @param params          encoding and derivation parameters, may be wrapped to include an iv for an underlying block cipher.
     * @param publicKeyParser the parser for reading the ephemeral public key.
     */
    public void init(AsymmetricKeyParameter privateKey, CipherParameters params, KeyParser publicKeyParser)
    {
        this.forEncryption = false;
        this.privParam = privateKey;
        this.keyParser = publicKeyParser;

        extractParams(params);
    }

    private void extractParams(CipherParameters params)
    {
        if (params instanceof ParametersWithIV)
        {
            this.iv = ((ParametersWithIV)params).getIV();
            this.param = (IESParameters)((ParametersWithIV)params).getParameters();
        }
        else
        {
            this.iv = null;
            this.param = (IESParameters)params;
        }
    }

    public BufferedBlockCipher getCipher()
    {
        return cipher;
    }

    public Mac getMac()
    {
        return mac;
    }

    private byte[] encryptBlock(
        byte[] in,
        int inOff,
        int inLen,
        byte[] macData)
        throws InvalidCipherTextException
    {
        byte[] c = null;
        byte[] k = null;
        byte[] k1 = null;
        byte[] k2 = null;

        int len;

        if (cipher == null)
        {
            // Streaming mode.
            k1 = new byte[inLen];
            k2 = new byte[param.getMacKeySize() / 8];
            k = new byte[k1.length + k2.length];

            kdf.generateBytes(k, 0, k.length);


            System.arraycopy(k, 0, k1, 0, k1.length);
            System.arraycopy(k, inLen, k2, 0, k2.length);

            c = new byte[inLen];

            for (int i = 0; i != inLen; i++)
            {
                c[i] = (byte)(in[inOff + i] ^ k1[i]);
            }
            len = inLen;
        }
        else
        {
            // Block cipher mode.
            k1 = new byte[((IESWithCipherParameters)param).getCipherKeySize() / 8];
            k2 = new byte[param.getMacKeySize() / 8];
            k = new byte[k1.length + k2.length];

            kdf.generateBytes(k, 0, k.length);
            System.arraycopy(k, 0, k1, 0, k1.length);
            System.arraycopy(k, k1.length, k2, 0, k2.length);

            // If iv provided use it to initialise the cipher
            if (iv != null)
            {
                cipher.init(true, new ParametersWithIV(new KeyParameter(k1), iv));
            }
            else
            {
                cipher.init(true, new KeyParameter(k1));
            }

            c = new byte[cipher.getOutputSize(inLen)];
            len = cipher.processBytes(in, inOff, inLen, c, 0);
            len += cipher.doFinal(c, len);
        }


        // Convert the length of the encoding vector into a byte array.
        byte[] p2 = param.getEncodingV();

        // Apply the MAC.
        byte[] t = new byte[mac.getMacSize()];

        byte[] k2A;
        if (hashK2) {
            k2A = new byte[hash.getDigestSize()];
            hash.reset();
            hash.update(k2, 0, k2.length);
            hash.doFinal(k2A, 0);
        } else {
            k2A = k2;
        }
        mac.init(new KeyParameter(k2A));
        if(iv != null) {
            mac.update(iv, 0, iv.length);
        }
        mac.update(c, 0, c.length);
        if (p2 != null)
        {
            mac.update(p2, 0, p2.length);
        }
        if (v.length != 0 && p2 != null) {
            byte[] l2 = new byte[4];
            Pack.intToBigEndian(p2.length * 8, l2, 0);
            mac.update(l2, 0, l2.length);
        }

        if (macData != null) {
            mac.update(macData, 0, macData.length);
        }

        mac.doFinal(t, 0);

        // Output the triple (v,C,T).
        byte[] output = new byte[v.length + len + t.length];
        System.arraycopy(v, 0, output, 0, v.length);
        System.arraycopy(c, 0, output, v.length, len);
        System.arraycopy(t, 0, output, v.length + len, t.length);
        return output;
    }

    private byte[] decryptBlock(
        byte[] inEnc,
        int inOff,
        int inLen,
        byte[] macData)
        throws InvalidCipherTextException
    {
        byte[] m = null;
        byte[] k = null;
        byte[] k1 = null;
        byte[] k2 = null;

        int len;

        // Ensure that the length of the input is greater than the MAC in bytes
        if (inLen <= (param.getMacKeySize() / 8))
        {
            throw new InvalidCipherTextException("Length of input must be greater than the MAC");
        }

        if (cipher == null)
        {
            // Streaming mode.
            k1 = new byte[inLen - v.length - mac.getMacSize()];
            k2 = new byte[param.getMacKeySize() / 8];
            k = new byte[k1.length + k2.length];

            kdf.generateBytes(k, 0, k.length);

            System.arraycopy(k, 0, k1, 0, k1.length);
            System.arraycopy(k, k1.length, k2, 0, k2.length);

            m = new byte[k1.length];

            for (int i = 0; i != k1.length; i++)
            {
                m[i] = (byte)(inEnc[inOff + v.length + i] ^ k1[i]);
            }

            len = k1.length;
        }
        else
        {
            // Block cipher mode.
            k1 = new byte[((IESWithCipherParameters)param).getCipherKeySize() / 8];
            k2 = new byte[param.getMacKeySize() / 8];
            k = new byte[k1.length + k2.length];

            kdf.generateBytes(k, 0, k.length);
            System.arraycopy(k, 0, k1, 0, k1.length);
            System.arraycopy(k, k1.length, k2, 0, k2.length);

            // If iv provide use it to initialize the cipher
            if (iv != null)
            {
                cipher.init(false, new ParametersWithIV(new KeyParameter(k1), iv));
            }
            else
            {
                cipher.init(false, new KeyParameter(k1));
            }

            m = new byte[cipher.getOutputSize(inLen - v.length - mac.getMacSize())];
            len = cipher.processBytes(inEnc, inOff + v.length, inLen - v.length - mac.getMacSize(), m, 0);
            len += cipher.doFinal(m, len);
        }


        // Convert the length of the encoding vector into a byte array.
        byte[] p2 = param.getEncodingV();

        // Verify the MAC.
        int end = inOff + inLen;
        byte[] t1 = Arrays.copyOfRange(inEnc, end - mac.getMacSize(), end);

        byte[] t2 = new byte[t1.length];
        byte[] k2A;
        if (hashK2) {
            k2A = new byte[hash.getDigestSize()];
            hash.reset();
            hash.update(k2, 0, k2.length);
            hash.doFinal(k2A, 0);
        } else {
            k2A = k2;
        }
        mac.init(new KeyParameter(k2A));
        if(iv != null) {
            mac.update(iv, 0, iv.length);
        }
        mac.update(inEnc, inOff + v.length, inLen - v.length - t2.length);

        if (p2 != null)
        {
            mac.update(p2, 0, p2.length);
        }

        if (v.length != 0 && p2 != null) {
            byte[] l2 = new byte[4];
            Pack.intToBigEndian(p2.length * 8, l2, 0);
            mac.update(l2, 0, l2.length);
        }

        if (macData != null) {
            mac.update(macData, 0, macData.length);
        }

        mac.doFinal(t2, 0);

        if (!Arrays.constantTimeAreEqual(t1, t2))
        {
            throw new InvalidCipherTextException("Invalid MAC.");
        }


        // Output the message.
        return Arrays.copyOfRange(m, 0, len);
    }

    public byte[] processBlock(byte[] in, int inOff, int inLen) throws InvalidCipherTextException {
        return processBlock(in, inOff, inLen, null);
    }

    public byte[] processBlock(
        byte[] in,
        int inOff,
        int inLen,
        byte[] macData)
        throws InvalidCipherTextException
    {
        if (forEncryption)
        {
            if (keyPairGenerator != null)
            {
                EphemeralKeyPair ephKeyPair = keyPairGenerator.generate();

                this.privParam = ephKeyPair.getKeyPair().getPrivate();
                this.v = ephKeyPair.getEncodedPublicKey();
            }
        }
        else
        {
            if (keyParser != null)
            {
                ByteArrayInputStream bIn = new ByteArrayInputStream(in, inOff, inLen);

                try
                {
                    this.pubParam = keyParser.readKey(bIn);
                }
                catch (IOException e)
                {
                    throw new InvalidCipherTextException("unable to recover ephemeral public key: " + e.getMessage(), e);
                }

                int encLength = (inLen - bIn.available());
                this.v = Arrays.copyOfRange(in, inOff, inOff + encLength);
            }
        }

        // Compute the common value and convert to byte array.
        agree.init(privParam);
        BigInteger z = agree.calculateAgreement(pubParam);
        // Create input to KDF.
        byte[] vz = BigIntegers.asUnsignedByteArray(agree.getFieldSize(), z);

        // Initialise the KDF.
        DerivationParameters kdfParam;
        if (kdf instanceof MGF1BytesGeneratorExt) {
            kdfParam = new MGFParameters(vz);
        } else {
            kdfParam = new KDFParameters(vz, param.getDerivationV());
        }
        kdf.init(kdfParam);

        return forEncryption
            ? encryptBlock(in, inOff, inLen, macData)
            : decryptBlock(in, inOff, inLen, macData);
    }
}
