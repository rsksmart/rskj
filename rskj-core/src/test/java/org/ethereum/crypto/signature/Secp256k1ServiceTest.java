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

package org.ethereum.crypto.signature;

import co.rsk.util.HexUtils;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.core.ImmutableTransaction;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.security.SignatureException;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;

@Disabled
public abstract class Secp256k1ServiceTest {

    private static final Logger logger = LoggerFactory.getLogger(Secp256k1ServiceTest.class);

    private final String pubString = "0497466f2b32bc3bb76d4741ae51cd1d8578b48d3f1e68da206d47321aec267ce78549b514e4453d74ef11b0cd5e4e4c364effddac8b51bcfc8de80682f952896f";
    private final byte[] pubKey = Hex.decode(pubString);

    private final String privString = "3ecb44df2159c26e0f995712d4f39b6f6e499b40749b1cf1246c37f9516cb6a4";
    private final BigInteger privateKey = new BigInteger(Hex.decode(privString));

    private final String exampleMessage = "This is an example of a signed message.";

    // Signature components
    private final BigInteger r = new BigInteger("28157690258821599598544026901946453245423343069728565040002908283498585537001");
    private final BigInteger s = new BigInteger("30212485197630673222315826773656074299979444367665131281281249560925428307087");
    private final byte v = 28;

    private final String v1y = "29896722852569046015560700294576055776214335159245303116488692907525646231534";
    // private final String v2y = "69211104694897500952317515077652022726490027694212560352756646854116994689233";
    private String v1by2x = "90462569716653277674664832038037428010367175520031690655826237506178777087235";
    private String v1by2y = "30122570767565969031174451675354718271714177419582540229636601003470726681395";
    private String v1by9x = "46171929588085016379679198610744759757996296651373714437564035753833216770329";
    private String v1by9y = "4076329532618667641907419885981677362511359868272295070859229146922980867493";

    private Secp256k1Service secp256k1;

    protected Secp256k1ServiceTest(Secp256k1Service secp256k1) {
        this.secp256k1 = secp256k1;
    }

    protected Secp256k1Service getSecp256k1() {
        return this.secp256k1;
    }

    @Test
    void test_verify() {
        String dataHashed = "53cb8e93030183c5ba198433e8cd1f013f3d113e0f4d1756de0d1f124ead155a";
        String rString = "8dba957877d5bdcb26d551dfa2fa509dfe3fe327caf0166130b9f467a0a0c249";
        String sString = "dab3fdf2031515d2de1d420310c69153fcc356f22b50dfd53c6e13e74e346eee";
        String pubKeyString = "04330037e82c177d7108077c80440821e13c1c62105f85e030214b48d7b5dff0b8e7c158b171546a71139e4de56c8535c964514033b89a669a8e87a5e8770c147c";

        checkVerify(dataHashed, 1, rString, sString, pubKeyString);

        String rString1 = "f0e8aab4fdd83382292a1bbc5480e2ae8084dc245f000f4bc4534d383a3a7919";
        String sString1 = "a30891f2176bd87b4a3ac5c75167f2442453c17c6e2fbfb36c3b972ee67a4c2d";
        String pubKeyString1 = "0473602083afe175e7cae12dbc27da54ec5ac77f99920787f3e891e7af303aaed480770c0de4c991aea1712729260175e158fa73f63c60f0f1de057139c52714de";

        checkVerify(dataHashed, 0, rString1, sString1, pubKeyString1);
    }

    void checkVerify(String dataHashed, int recId, String rString, String sString, String pubKeyString) {
        byte[] dbHash = Hex.decode(dataHashed);
        ECDSASignature signature = ECDSASignature.fromComponents(Hex.decode(rString), Hex.decode(sString));
        byte[] pubKey = Hex.decode(pubKeyString);
        assertTrue(getSecp256k1().verify(dbHash, signature, pubKey));
        ECKey ecKey = getSecp256k1().recoverFromSignature(recId, signature, dbHash, false);
        assertEquals(pubKeyString, Hex.toHexString(ecKey.getPubKey()));
    }

    @Test
    void test_recoveryFromSignature_pointAtInfinity_returnZeroPK() {
        String messageHash = "f7cf90057f86838e5efd677f4741003ab90910e4e2736ff4d7999519d162d1ed";
        BigInteger r = new BigInteger("28824799845160661199077176548860063813328724131408018686643359460017962873020");
        BigInteger s = new BigInteger("48456094880180616145578324187715054843822774625773874469802229460318542735739");
        ECDSASignature signature = ECDSASignature.fromComponents(r.toByteArray(), s.toByteArray());
        ECKey k = this.getSecp256k1().recoverFromSignature((byte) 0, signature, Hex.decode(messageHash), false);
        assertEquals( "00", Hex.toHexString(k.getPubKey()));
        assertEquals( "dcc703c0e500b653ca82273b7bfad8045d85a470", Hex.toHexString(k.getAddress()));
    }

    @Test
    void testVerify_from_signatureToKey() {
        BigInteger r = new BigInteger("c52c114d4f5a3ba904a9b3036e5e118fe0dbb987fe3955da20f2cd8f6c21ab9c", 16);
        BigInteger s = new BigInteger("6ba4c2874299a55ad947dbc98a25ee895aabf6b625c26c435e84bfd70edf2f69", 16);
        ECDSASignature sig = ECDSASignature.fromComponents(r.toByteArray(), s.toByteArray(), (byte) 0x1b);
        byte[] rawtx = Hex.decode("f82804881bc16d674ec8000094cd2a3d9f938e13cd947ec05abc7fe734df8dd8268609184e72a0006480");
        try {
            ECKey key = this.getSecp256k1().signatureToKey(HashUtil.keccak256(rawtx), sig);
            logger.debug("Signature public key\t: {}", ByteUtil.toHexString(key.getPubKey()));
            logger.debug("Sender is\t\t: {}", ByteUtil.toHexString(key.getAddress()));
            assertEquals("cd2a3d9f938e13cd947ec05abc7fe734df8dd826", ByteUtil.toHexString(key.getAddress()));
            assertTrue(this.getSecp256k1().verify(HashUtil.keccak256(rawtx), sig, key.getPubKey()));
        } catch (SignatureException e) {
            fail();
        }
    }

    @Test
    void testVerify_fixed_values() {
        ECKey key = ECKey.fromPublicOnly(pubKey);
        BigInteger r = new BigInteger("28157690258821599598544026901946453245423343069728565040002908283498585537001");
        BigInteger s = new BigInteger("30212485197630673222315826773656074299979444367665131281281249560925428307087");
        byte v = (byte) 28;
        ECDSASignature sig = ECDSASignature.fromComponents(r.toByteArray(), s.toByteArray(), v);
        assertTrue(this.getSecp256k1().verify(HashUtil.keccak256(exampleMessage.getBytes()), sig, key.getPubKey()));
    }

    @Test
    void testVerify_after_doSign() {
        ECKey key = ECKey.fromPrivate(privateKey);
        String message = "This is an example of a signed message.";
        byte[] messageBytes = HashUtil.keccak256(message.getBytes());
        ECDSASignature signature = ECDSASignature.fromSignature(key.doSign(messageBytes));
        assertTrue(this.getSecp256k1().verify(messageBytes, signature, key.getPubKey()));
    }

    @Test
    void testSignatureToKey_invalid_params() {
        byte[] messageHash = HashUtil.keccak256(exampleMessage.getBytes());
        try {
            byte v = (byte) 35;
            this.getSecp256k1().signatureToKey(messageHash, ECDSASignature.fromComponents(r.toByteArray(), s.toByteArray(), v));
            fail();
        } catch (SignatureException e) {
            MatcherAssert.assertThat(e.getMessage(), containsString("Header byte out of range"));
        }
        try {
            byte v = (byte) 26;
            this.getSecp256k1().signatureToKey(messageHash, ECDSASignature.fromComponents(r.toByteArray(), s.toByteArray(), v));
            fail();
        } catch (SignatureException e) {
            MatcherAssert.assertThat(e.getMessage(), containsString("Header byte out of range"));
        }
    }

    @Test
    void testSignatureToKey_from_Tx() throws SignatureException {

        byte[] messageHash = HashUtil.keccak256(exampleMessage.getBytes());
        byte[] pk = this.privateKey.toByteArray();
        String receiver = "CD2A3D9F938E13CD947EC05ABC7FE734DF8DD826";
        ECKey fromPrivate = ECKey.fromPrivate(pk);
        ECKey fromPrivateDecompress = fromPrivate.decompress();
        String pubKeyExpected = Hex.toHexString(fromPrivateDecompress.getPubKey());
        String addressExpected = Hex.toHexString(fromPrivateDecompress.getAddress());

        // Create tx and sign, then recover from serialized.
        Transaction newTx = Transaction
                .builder()
                .nonce(BigInteger.valueOf(2L))
                .gasPrice(BigInteger.valueOf(2L))
                .gasLimit(BigInteger.valueOf(2L))
                .destination(Hex.decode(receiver))
                .data(messageHash)
                .value(BigInteger.valueOf(2L))
                .build();
        newTx.sign(pk);
        ImmutableTransaction recoveredTx = new ImmutableTransaction(newTx.getEncoded());
        // Recover Pub Key from recovered tx
        ECKey actualKey = this.getSecp256k1().signatureToKey(HashUtil.keccak256(recoveredTx.getEncodedRaw()), recoveredTx.getSignature());

        // Recover PK and Address.
        String pubKeyActual = Hex.toHexString(actualKey.getPubKey());
        logger.debug("Signature public key\t: {}", pubKeyActual);
        assertEquals(pubKeyExpected, pubKeyActual);
        assertEquals(pubString, pubKeyActual);
        assertArrayEquals(pubKey, actualKey.getPubKey());

        String addressActual = Hex.toHexString(actualKey.getAddress());
        logger.debug("Sender is\t\t: {}", addressActual);
        assertEquals(addressExpected, addressActual);
    }

    @Test
    void testSignatureToKey_fixed_values() throws SignatureException {
        byte[] messageHash = HashUtil.keccak256(exampleMessage.getBytes());
        byte[] s = ByteUtil.bigIntegerToBytes(this.s, 32);
        byte[] r = ByteUtil.bigIntegerToBytes(this.r, 32);
        ECDSASignature signature = ECDSASignature.fromComponents(r, s, v);
        ECKey key = this.getSecp256k1().signatureToKey(messageHash, signature);
        assertNotNull(key);
        assertArrayEquals(pubKey, key.getPubKey());
    }

    @Test
    void testSignatureToKey_fixed_values_garbage()  {
        byte[] messageHash = HashUtil.keccak256(exampleMessage.getBytes());
        byte[] s = Arrays.concatenate(new byte[]{1}, ByteUtil.bigIntegerToBytes(this.s, 64));
        byte[] r = Arrays.concatenate(new byte[]{1}, ByteUtil.bigIntegerToBytes(this.r, 64));
        ECDSASignature signature = ECDSASignature.fromComponents(r, s, v);
        Assertions.assertThrows(SignatureException.class, () -> this.getSecp256k1().signatureToKey(messageHash, signature));
    }

    @Test
    void testRecoverFromSignature_fixed_values_garbage() throws SignatureException {
        byte[] messageHash = HashUtil.keccak256(exampleMessage.getBytes());
        byte[] s = Arrays.concatenate(new byte[]{1}, ByteUtil.bigIntegerToBytes(this.s, 64));
        byte[] r = Arrays.concatenate(new byte[]{1}, ByteUtil.bigIntegerToBytes(this.r, 64));
        ECDSASignature signature = ECDSASignature.fromComponents(r, s, v);
        ECKey key = this.getSecp256k1().recoverFromSignature(v - 27, signature, messageHash, true);
        assertNull(key);
    }

    @Test
    void testRecoverFromSignature_invalid_params() {

        BigInteger validBigInt = BigInteger.valueOf(0l);
        byte[] validBytes = validBigInt.toByteArray();
        BigInteger invalidBigInt = BigInteger.valueOf(-1l);
        byte[] invalidNullBytes = null;
        boolean validBoolean = false;
        int validRecId = 0;
        int invalidRecId = -1;

        Secp256k1Service secp256k11 = this.getSecp256k1();

        ECDSASignature sig = ECDSASignature.fromComponents(validBytes, validBytes);
        try {
            secp256k11.recoverFromSignature(invalidRecId, sig, validBytes, validBoolean);
            fail();
        }catch (IllegalArgumentException e){MatcherAssert.assertThat(e.getMessage(), containsString("recId must be positive"));}

        sig = new ECDSASignature(invalidBigInt, validBigInt);
        try {
            secp256k11.recoverFromSignature(validRecId, sig, validBytes, validBoolean);
            fail();
        }catch (IllegalArgumentException e){MatcherAssert.assertThat(e.getMessage(), containsString("r must be positive"));}

        sig = new ECDSASignature(validBigInt, invalidBigInt);
        try {
            secp256k11.recoverFromSignature(validRecId, sig, validBytes, validBoolean);
            fail();
        }catch (IllegalArgumentException e){MatcherAssert.assertThat(e.getMessage(), containsString("s must be positive"));}

        sig = new ECDSASignature(validBigInt, validBigInt);
        try {
            secp256k11.recoverFromSignature(validRecId, sig, invalidNullBytes, validBoolean);
            fail();
        }catch (IllegalArgumentException e){MatcherAssert.assertThat(e.getMessage(), containsString("messageHash must not be null"));}

    }

    @Test
    void testRecoverFromSignature_without_V() {
        ECKey key = new ECKey();
        String message = "Hello World!";
        byte[] hash = HashUtil.sha256(message.getBytes());
        ECDSASignature sig = ECDSASignature.fromSignature(key.doSign(hash));
        key = ECKey.fromPublicOnly(key.getPubKeyPoint());
        boolean found = false;
        for (int i = 0; i < 4; i++) {
            ECKey key2 = this.getSecp256k1().recoverFromSignature(i, sig, hash, true);
            checkNotNull(key2);
            if (key.equals(key2)) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }

    @Test
    void test_sign_signatureToKey_pk1() {
        ECKey privateKey = ECKey.fromPrivate(BigInteger.ONE);
        ECKey publicKey = ECKey.fromPublicOnly(privateKey.getPubKeyPoint());
        sign_signatureToKey_assert(privateKey, publicKey);
    }

    @Test
    void test_sign_signatureToKey_pk10() {
        ECKey privateKey = ECKey.fromPrivate(BigInteger.TEN);
        ECKey publicKey = ECKey.fromPublicOnly(privateKey.getPubKeyPoint());
        sign_signatureToKey_assert(privateKey, publicKey);
    }

    @Test
    void test_sign_signatureToKey_pk1M() {
        ECKey privateKey = ECKey.fromPrivate(BigInteger.valueOf(1_000_000_000));
        ECKey publicKey = ECKey.fromPublicOnly(privateKey.getPubKeyPoint());
        sign_signatureToKey_assert(privateKey, publicKey);
    }

    @Test
    void testSecpAddTwoPoints() {
        /*
         Test should return correct result (taken from parity impl)
         */
        final var inputStr = "0000000000000000000000000000000000000000000000000000000000000001" + TestUtils.bigIntegerToHexDW(v1y) +
                "0000000000000000000000000000000000000000000000000000000000000001" + TestUtils.bigIntegerToHexDW(v1y);

        final var input = HexUtils.stringHexToByteArray(inputStr);
        final var ox = new BigInteger(v1by2x);
        final var oy = new BigInteger(v1by2y);
        final var outputStr = TestUtils.bigIntegerToHex(ox) + TestUtils.bigIntegerToHex(oy);
        final var output = HexUtils.stringHexToByteArray(outputStr);

        final var result = secp256k1.add(input);

        Assertions.assertArrayEquals(result, output);
    }

    @Test
    void testSecpAddZeroPointsShouldBeZero() {
        /*
         Test should return zero
         */
        final var inputStr = "0000000000000000000000000000000000000000000000000000000000000000" +
                "0000000000000000000000000000000000000000000000000000000000000000" +
                "0000000000000000000000000000000000000000000000000000000000000000" +
                "0000000000000000000000000000000000000000000000000000000000000000";

        final var outputStr = "0000000000000000000000000000000000000000000000000000000000000000" +
                "0000000000000000000000000000000000000000000000000000000000000000";

        final var input = HexUtils.stringHexToByteArray(inputStr);
        final var output = HexUtils.stringHexToByteArray(outputStr);

        final var result = secp256k1.add(input);

        Assertions.assertArrayEquals(result, output);
    }

    @Test
    void testSecpAddEmptyInputShouldBeZero() {
        /*
         Test should return zero
         */
        final var inputStr = "";

        final var outputStr = "0000000000000000000000000000000000000000000000000000000000000000" +
                "0000000000000000000000000000000000000000000000000000000000000000";

        final var input = HexUtils.stringHexToByteArray(inputStr);
        final var output = HexUtils.stringHexToByteArray(outputStr);

        final var result = secp256k1.add(input);

        Assertions.assertArrayEquals(result, output);
    }

    @Test
    void testSecpAddPointPlusInfinityIsPoint() {
        /*
         Test should return same point
         */
        final var outputStr = "0000000000000000000000000000000000000000000000000000000000000001" + TestUtils.bigIntegerToHexDW(v1y);

        final var inputStr = outputStr +
                "0000000000000000000000000000000000000000000000000000000000000000" +
                "0000000000000000000000000000000000000000000000000000000000000000";

        final var input = HexUtils.stringHexToByteArray(inputStr);
        final var output = HexUtils.stringHexToByteArray(outputStr);

        final var result = secp256k1.add(input);

        Assertions.assertArrayEquals(result, output);
    }

    @Test
    void testSecpAddInfinityPlusPointIsPoint() {
        /*
         Test should return same point
         */
        final var outputStr = "0000000000000000000000000000000000000000000000000000000000000001" +
                TestUtils.bigIntegerToHexDW(v1y);

        final var inputStr = "0000000000000000000000000000000000000000000000000000000000000000" +
                "0000000000000000000000000000000000000000000000000000000000000000" + outputStr;

        final var input = HexUtils.stringHexToByteArray(inputStr);
        final var output = HexUtils.stringHexToByteArray(outputStr);

        final var result = secp256k1.add(input);

        Assertions.assertArrayEquals(result, output);
    }

    @Test
    void testSecpAddPointNotOnCurveShouldFail() {
        /*
         Test should return empty byte array because point is not on curve
         */
        final var inputStr =
                "1111111111111111111111111111111111111111111111111111111111111111" +
                        "1111111111111111111111111111111111111111111111111111111111111111" +
                        "1111111111111111111111111111111111111111111111111111111111111111" +
                        "1111111111111111111111111111111111111111111111111111111111111111";

        final var input = HexUtils.stringHexToByteArray(inputStr);

        final var result = secp256k1.add(input);

        Assertions.assertNull(result);
    }

    @Test
    void shouldReturnInfinityOnIdenticalInputPointValuesOfX() {
        final var p0x = new BigInteger("3");
        final var p0y = new BigInteger("21320899557911560362763253855565071047772010424612278905734793689199612115787");
        final var p1x = new BigInteger("3");
        final var p1y = new BigInteger("-21320899557911560362763253855565071047772010424612278905734793689203907084060");

        final var input = new byte[128];

        final var x1Bytes = ByteUtil.stripLeadingZeroes(p0x.toByteArray());
        final var y1Bytes = ByteUtil.stripLeadingZeroes(p0y.toByteArray());
        final var x2Bytes = ByteUtil.stripLeadingZeroes(p1x.toByteArray());
        final var y2Bytes = ByteUtil.stripLeadingZeroes(p1y.toByteArray());

        System.arraycopy(x1Bytes, 0, input, 32 - x1Bytes.length, x1Bytes.length);
        System.arraycopy(y1Bytes, 0, input, 64 - y1Bytes.length, y1Bytes.length);
        System.arraycopy(x2Bytes, 0, input, 96 - x2Bytes.length, x2Bytes.length);
        System.arraycopy(y2Bytes, 0, input, 128 - y2Bytes.length, y2Bytes.length);

        final var outputStr = "0000000000000000000000000000000000000000000000000000000000000000" +
                "0000000000000000000000000000000000000000000000000000000000000000";
        final var output = HexUtils.stringHexToByteArray(outputStr);

        final var result = secp256k1.add(input);

        Assertions.assertArrayEquals(result, output);
    }

    @Test
    void shouldReturnTrueAddAndComputeSlope()  {
        final var p0x = new BigInteger("4");
        final var p0y = new BigInteger("40508090799132825824753983223610497876805216745196355809233758402754120847507");

        final var p1x = new BigInteger("1624070059937464756887933993293429854168590106605707304006200119738501412969");
        final var p1y = new BigInteger("48810817106871756219742442189260392858217846784043974224646271552914041676099");

        final var input = new byte[128];

        final var x1Bytes = ByteUtil.stripLeadingZeroes(p0x.toByteArray());
        final var y1Bytes = ByteUtil.stripLeadingZeroes(p0y.toByteArray());
        final var x2Bytes = ByteUtil.stripLeadingZeroes(p1x.toByteArray());
        final var y2Bytes = ByteUtil.stripLeadingZeroes(p1y.toByteArray());

        System.arraycopy(x1Bytes, 0, input, 32 - x1Bytes.length, x1Bytes.length);
        System.arraycopy(y1Bytes, 0, input, 64 - y1Bytes.length, y1Bytes.length);
        System.arraycopy(x2Bytes, 0, input, 96 - x2Bytes.length, x2Bytes.length);
        System.arraycopy(y2Bytes, 0, input, 128 - y2Bytes.length, y2Bytes.length);

        final var ox = new BigInteger("59470963110652214182270290319243047549711080187995156844066669631124720856270");
        final var oy = new BigInteger("75549874947483386113764723043915448105868538368156141886808196158351727282824");
        final var outputStr = TestUtils.bigIntegerToHex(ox) + TestUtils.bigIntegerToHex(oy);
        final var output = HexUtils.stringHexToByteArray(outputStr);

        final var result = secp256k1.add(input);

        Assertions.assertArrayEquals(result, output);

    }

    @Test
    void shouldReturnTrueMultiplyScalarAndPoint() {
        final var x = BigInteger.valueOf(1);
        final var y = new BigInteger(v1y);

        final var multiplier = new BigInteger("115792089237316195423570985008687907853269984665640564039457584007913129639935");

        final var input = new byte[96];

        final var x1Bytes = ByteUtil.stripLeadingZeroes(x.toByteArray());
        final var y1Bytes = ByteUtil.stripLeadingZeroes(y.toByteArray());
        final var scalarBytes = ByteUtil.stripLeadingZeroes(multiplier.toByteArray());

        System.arraycopy(x1Bytes, 0, input, 32 - x1Bytes.length, x1Bytes.length);
        System.arraycopy(y1Bytes, 0, input, 64 - y1Bytes.length, y1Bytes.length);
        System.arraycopy(scalarBytes, 0, input, 96 - scalarBytes.length, scalarBytes.length);

        final var ox = new BigInteger("68306631035792818416930554521980007078198693994042647901813352646899028694565");
        final var oy = new BigInteger("763410389832780290161227297165449309800016629866253823160953352172730927280");
        final var outputStr = TestUtils.bigIntegerToHex(ox) + TestUtils.bigIntegerToHex(oy);
        final var output = HexUtils.stringHexToByteArray(outputStr);

        final var result = secp256k1.mul(input);

        Assertions.assertArrayEquals(result, output);
    }

    @Test
    void shouldReturnIdentityWhenMultipliedByScalarValueOne() {
        final var x = new BigInteger("1");
        final var y = new BigInteger(v1y);

        final var multiplier = BigInteger.valueOf(1);

        final var input = new byte[96];

        final var x1Bytes = ByteUtil.stripLeadingZeroes(x.toByteArray());
        final var y1Bytes = ByteUtil.stripLeadingZeroes(y.toByteArray());
        final var scalarBytes = ByteUtil.stripLeadingZeroes(multiplier.toByteArray());

        System.arraycopy(x1Bytes, 0, input, 32 - x1Bytes.length, x1Bytes.length);
        System.arraycopy(y1Bytes, 0, input, 64 - y1Bytes.length, y1Bytes.length);
        System.arraycopy(scalarBytes, 0, input, 96 - scalarBytes.length, scalarBytes.length);

        final var outputStr = TestUtils.bigIntegerToHexDW("1") + TestUtils.bigIntegerToHexDW(v1y);
        final var output = HexUtils.stringHexToByteArray(outputStr);

        final var result = secp256k1.mul(input);

        Assertions.assertArrayEquals(result, output);
    }

    @Test
    void shouldReturnTrueMultiplyPointByScalar() {
        final var x = BigInteger.valueOf(1);
        final var y = new BigInteger(v1y);

        final var multiplier = BigInteger.valueOf(9);

        final var input = new byte[96];

        final var x1Bytes = ByteUtil.stripLeadingZeroes(x.toByteArray());
        final var y1Bytes = ByteUtil.stripLeadingZeroes(y.toByteArray());
        final var scalarBytes = ByteUtil.stripLeadingZeroes(multiplier.toByteArray());

        System.arraycopy(x1Bytes, 0, input, 32 - x1Bytes.length, x1Bytes.length);
        System.arraycopy(y1Bytes, 0, input, 64 - y1Bytes.length, y1Bytes.length);
        System.arraycopy(scalarBytes, 0, input, 96 - scalarBytes.length, scalarBytes.length);

        final var ox = new BigInteger(v1by9x);
        final var oy = new BigInteger(v1by9y);
        final var outputStr = TestUtils.bigIntegerToHex(ox) + TestUtils.bigIntegerToHex(oy);
        final var output = HexUtils.stringHexToByteArray(outputStr);

        final var result = secp256k1.mul(input);

        Assertions.assertArrayEquals(result, output);
    }

    @Test
    void shouldReturnSumMultiplyPointByScalar() {
        final var x = new BigInteger("1");
        final var y = new BigInteger(v1y);

        final var multiplier = BigInteger.valueOf(2);

        final var input = new byte[96];

        final var x1Bytes = ByteUtil.stripLeadingZeroes(x.toByteArray());
        final var y1Bytes = ByteUtil.stripLeadingZeroes(y.toByteArray());
        final var scalarBytes = ByteUtil.stripLeadingZeroes(multiplier.toByteArray());

        System.arraycopy(x1Bytes, 0, input, 32 - x1Bytes.length, x1Bytes.length);
        System.arraycopy(y1Bytes, 0, input, 64 - y1Bytes.length, y1Bytes.length);
        System.arraycopy(scalarBytes, 0, input, 96 - scalarBytes.length, scalarBytes.length);

        final var ox = new BigInteger(v1by2x);
        final var oy = new BigInteger(v1by2y);
        String outputStr = TestUtils.bigIntegerToHex(ox) + TestUtils.bigIntegerToHex(oy);
        final var output = HexUtils.stringHexToByteArray(outputStr);

        final var result = secp256k1.mul(input);

        Assertions.assertArrayEquals(result, output);
    }

    @Test
    void shouldFailForPointNotOnCurve() {
        String inputStr = "1111111111111111111111111111111111111111111111111111111111111111" +
                "1111111111111111111111111111111111111111111111111111111111111111" +
                "1111111111111111111111111111111111111111111111111111111111111111";

        final var input = HexUtils.stringHexToByteArray(inputStr);

        final var result = secp256k1.mul(input);

        Assertions.assertNull(result);
    }

    @Test
    void mulShouldFailForNotEnoughParams() {
        String inputStr = "1111111111111111111111111111111111111111111111111111111111111111" +
                "1111111111111111111111111111111111111111111111111111111111111111";

        final var input = HexUtils.stringHexToByteArray(inputStr);

        final var result = secp256k1.mul(input);

        Assertions.assertNull(result);
    }

    @Test
    void mulShouldFailEmptyParams() {
        /*
         * Behaviour on empty params establishes that correct output is zero byte array
         */

        final var inputStr = "";
        final var outputStr = "0000000000000000000000000000000000000000000000000000000000000000" +
                "0000000000000000000000000000000000000000000000000000000000000000";

        final var input = HexUtils.stringHexToByteArray(inputStr);
        final var output = HexUtils.stringHexToByteArray(outputStr);

        final var result = secp256k1.mul(input);

        Assertions.assertArrayEquals(result, output);
    }

    private void sign_signatureToKey_assert(ECKey privateKey, ECKey publicKey) {
        String message = "Hello World!";
        byte[] hash = HashUtil.sha256(message.getBytes());
        ECDSASignature sig = ECDSASignature.fromSignature(privateKey.doSign(hash));
        boolean found = false;
        for (int i = 0; i < 4; i++) {
            ECKey key2 = this.getSecp256k1().recoverFromSignature(i, sig, hash, true);
            checkNotNull(key2);
            if (publicKey.equals(key2)) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }

}
