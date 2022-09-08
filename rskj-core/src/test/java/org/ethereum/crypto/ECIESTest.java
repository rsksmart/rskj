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

import org.ethereum.ConcatKDFBytesGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.*;
import org.bouncycastle.crypto.agreement.ECDHBasicAgreement;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.modes.SICBlockCipher;
import org.bouncycastle.crypto.params.*;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.encoders.Hex;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class ECIESTest {
    public static final int KEY_SIZE = 128;
    static Logger log = LoggerFactory.getLogger("test");
    private static ECDomainParameters curve;
    private static final String CIPHERTEXT1 = "042a851331790adacf6e64fcb19d0872fcdf1285a899a12cdc897da941816b0ea6485402aaf6c2e0a5d98ae3af1b05c68b307d1e0eb7a426a46f1617ba5b94f90b606eee3b5e9d2b527a9ee52cfa377bcd118b9390ed27ffe7d48e8155004375cae209012c3e057bb13a478a64a201d79ad4ae83";
    private static final X9ECParameters IES_CURVE_PARAM = SECNamedCurves.getByName("secp256r1");
    private static final BigInteger PRIVATE_KEY1 = new BigInteger("51134539186617376248226283012294527978458758538121566045626095875284492680246");

    private static ECPoint pub(BigInteger d) {
        return curve.getG().multiply(d);
    }

    @BeforeAll
    public static void beforeAll() {
        curve = new ECDomainParameters(IES_CURVE_PARAM.getCurve(), IES_CURVE_PARAM.getG(), IES_CURVE_PARAM.getN(), IES_CURVE_PARAM.getH());
    }

    @Test
    public void testKDF() {
        ConcatKDFBytesGenerator kdf = new ConcatKDFBytesGenerator(new SHA256Digest());
        kdf.init(new KDFParameters("Hello".getBytes(), new byte[0]));
        byte[] bytes = new byte[2];
        kdf.generateBytes(bytes, 0, bytes.length);
        assertArrayEquals(new byte[]{-66, -89}, bytes);
    }

    @Test
    public void testDecryptTestVector() throws IOException, InvalidCipherTextException {
        ECPoint pub1 = pub(PRIVATE_KEY1);
        byte[] ciphertext = Hex.decode(CIPHERTEXT1);
        byte[] plaintext = decrypt(PRIVATE_KEY1, ciphertext);
        assertArrayEquals(new byte[]{1,1,1}, plaintext);
    }

    @Test
    public void testRoundTrip() throws InvalidCipherTextException, IOException {
        ECPoint pub1 = pub(PRIVATE_KEY1);
        byte[] plaintext = "Hello world".getBytes();
        byte[] ciphertext = encrypt(pub1, plaintext);
        byte[] plaintext1 = decrypt(PRIVATE_KEY1, ciphertext);
        assertArrayEquals(plaintext, plaintext1);
    }

    public static byte[] decrypt(BigInteger prv, byte[] cipher) throws InvalidCipherTextException, IOException {
        ByteArrayInputStream is = new ByteArrayInputStream(cipher);
        byte[] ephemBytes = new byte[2*((curve.getCurve().getFieldSize()+7)/8) + 1];
        is.read(ephemBytes);
        ECPoint ephem = curve.getCurve().decodePoint(ephemBytes);
        byte[] IV = new byte[KEY_SIZE /8];
        is.read(IV);
        byte[] cipherBody = new byte[is.available()];
        is.read(cipherBody);

        EthereumIESEngine iesEngine = makeIESEngine(false, ephem, prv, IV);

        byte[] message = iesEngine.processBlock(cipherBody, 0, cipherBody.length);
        return message;
    }

    public static byte[] encrypt(ECPoint toPub, byte[] plaintext) throws InvalidCipherTextException, IOException {

        ECKeyPairGenerator eGen = new ECKeyPairGenerator();
        SecureRandom random = new SecureRandom();
        KeyGenerationParameters gParam = new ECKeyGenerationParameters(curve, random);

        eGen.init(gParam);

        byte[] IV = new byte[KEY_SIZE/8];
        new SecureRandom().nextBytes(IV);

        AsymmetricCipherKeyPair ephemPair = eGen.generateKeyPair();
        BigInteger prv = ((ECPrivateKeyParameters)ephemPair.getPrivate()).getD();
        ECPoint pub = ((ECPublicKeyParameters)ephemPair.getPublic()).getQ();
        EthereumIESEngine iesEngine = makeIESEngine(true, toPub, prv, IV);


        ECKeyGenerationParameters keygenParams = new ECKeyGenerationParameters(curve, random);
        ECKeyPairGenerator generator = new ECKeyPairGenerator();
        generator.init(keygenParams);

        ECKeyPairGenerator gen = new ECKeyPairGenerator();
        gen.init(new ECKeyGenerationParameters(ECKey.CURVE, random));

        byte[] cipher = iesEngine.processBlock(plaintext, 0, plaintext.length);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(pub.getEncoded(false));
        bos.write(IV);
        bos.write(cipher);
        return bos.toByteArray();
    }

    private static EthereumIESEngine makeIESEngine(boolean isEncrypt, ECPoint pub, BigInteger prv, byte[] IV) {
        AESEngine aesEngine = new AESEngine();

        EthereumIESEngine iesEngine = new EthereumIESEngine(
                new ECDHBasicAgreement(),
                new ConcatKDFBytesGenerator(new SHA256Digest()),
                new HMac(new SHA256Digest()),
                new SHA256Digest(),
                new BufferedBlockCipher(new SICBlockCipher(aesEngine)));


        byte[]         d = new byte[] {};
        byte[]         e = new byte[] {};

        IESParameters p = new IESWithCipherParameters(d, e, KEY_SIZE, KEY_SIZE);
        ParametersWithIV parametersWithIV = new ParametersWithIV(p, IV);

        iesEngine.init(isEncrypt, new ECPrivateKeyParameters(prv, curve), new ECPublicKeyParameters(pub, curve), parametersWithIV);
        return iesEngine;
    }

}
