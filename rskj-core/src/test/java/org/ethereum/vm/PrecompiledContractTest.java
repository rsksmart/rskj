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

package org.ethereum.vm;

import co.rsk.config.TestSystemProperties;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.util.BIUtil;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.PrecompiledContracts.PrecompiledContract;
import org.ethereum.vm.exception.VMException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Roman Mandeleil
 */
public class PrecompiledContractTest {


    private final TestSystemProperties config = new TestSystemProperties();
    private final PrecompiledContracts precompiledContracts = new PrecompiledContracts(config, null);

    @Test
    public void identityTest1() throws VMException {

        DataWord addr = DataWord.valueFromHex("0000000000000000000000000000000000000000000000000000000000000004");
        PrecompiledContract contract = precompiledContracts.getContractForAddress(null, addr);
        byte[] data = Hex.decode("112233445566");
        byte[] expected = Hex.decode("112233445566");

        byte[] result = contract.execute(data);

        assertArrayEquals(expected, result);
    }


    @Test
    public void sha256Test1() throws VMException {

        DataWord addr = DataWord.valueFromHex("0000000000000000000000000000000000000000000000000000000000000002");
        PrecompiledContract contract = precompiledContracts.getContractForAddress(null, addr);
        byte[] data = null;
        String expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

        byte[] result = contract.execute(data);

        assertEquals(expected, ByteUtil.toHexString(result));
    }

    @Test
    public void sha256Test2() throws VMException {

        DataWord addr = DataWord.valueFromHex("0000000000000000000000000000000000000000000000000000000000000002");
        PrecompiledContract contract = precompiledContracts.getContractForAddress(null, addr);
        byte[] data = ByteUtil.EMPTY_BYTE_ARRAY;
        String expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

        byte[] result = contract.execute(data);

        assertEquals(expected, ByteUtil.toHexString(result));
    }

    @Test
    public void sha256Test3() throws VMException {

        DataWord addr = DataWord.valueFromHex("0000000000000000000000000000000000000000000000000000000000000002");
        PrecompiledContract contract = precompiledContracts.getContractForAddress(null, addr);
        byte[] data = Hex.decode("112233");
        String expected = "49ee2bf93aac3b1fb4117e59095e07abe555c3383b38d608da37680a406096e8";

        byte[] result = contract.execute(data);

        assertEquals(expected, ByteUtil.toHexString(result));
    }

    @Test
    public void Ripempd160Test1() throws VMException {

        DataWord addr = DataWord.valueFromHex("0000000000000000000000000000000000000000000000000000000000000003");
        PrecompiledContract contract = precompiledContracts.getContractForAddress(null, addr);
        byte[] data = Hex.decode("0000000000000000000000000000000000000000000000000000000000000001");
        String expected = "000000000000000000000000ae387fcfeb723c3f5964509af111cf5a67f30661";

        byte[] result = contract.execute(data);

        assertEquals(expected, ByteUtil.toHexString(result));
    }

    @Test @Disabled("expected != result, inherited test")
    public void ecRecoverTest1() throws VMException {

        byte[] data = Hex.decode("18c547e4f7b0f325ad1e56f57e26c745b09a3e503d86e00e5255ff7f715d3d1c000000000000000000000000000000000000000000000000000000000000001c73b1693892219d736caba55bdb67216e485557ea6b6af75f37096c9aa6a5a75feeb940b1d03b21e36b0e47e79769f095fe2ab855bd91e3a38756b7d75a9c4549");
        DataWord addr = DataWord.valueFromHex("0000000000000000000000000000000000000000000000000000000000000001");
        PrecompiledContract contract = precompiledContracts.getContractForAddress(null, addr);
        String expected = "000000000000000000000000ae387fcfeb723c3f5964509af111cf5a67f30661";

        byte[] result = contract.execute(data);

        System.out.println(ByteUtil.toHexString(result));

        // todo(fedejinich) analyse this case
        assertEquals(expected, ByteUtil.toHexString(result));
    }
    @Test
    public void modExpTest() throws VMException {

        DataWord addr = DataWord.valueFromHex("0000000000000000000000000000000000000000000000000000000000000005");

        PrecompiledContract contract = precompiledContracts.getContractForAddress(null, addr);
        assertNotNull(contract);

        byte[] data1 = Hex.decode(
                "0000000000000000000000000000000000000000000000000000000000000001" +
                        "0000000000000000000000000000000000000000000000000000000000000020" +
                        "0000000000000000000000000000000000000000000000000000000000000020" +
                        "03" +
                        "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2e" +
                        "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f");

        assertEquals(13056, contract.getGasForData(data1));

        byte[] res1 = contract.execute(data1);
        assertEquals(32, res1.length);
        assertEquals(BigInteger.ONE, BIUtil.toBI(res1));

        byte[] data2 = Hex.decode(
                "0000000000000000000000000000000000000000000000000000000000000000" +
                        "0000000000000000000000000000000000000000000000000000000000000020" +
                        "0000000000000000000000000000000000000000000000000000000000000020" +
                        "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2e" +
                        "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f");

        assertEquals(13056, contract.getGasForData(data2));

        byte[] res2 = contract.execute(data2);
        assertEquals(32, res2.length);
        assertEquals(BigInteger.ZERO, BIUtil.toBI(res2));

        byte[] data3 = Hex.decode(
                "0000000000000000000000000000000000000000000000000000000000000000" +
                        "0000000000000000000000000000000000000000000000000000000000000020" +
                        "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff" +
                        "fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffe" +
                        "fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffd");

        // hardly imagine this value could be a real one
        assertEquals(3_674_950_435_109_146_392L, contract.getGasForData(data3));

        byte[] data4 = Hex.decode(
                "0000000000000000000000000000000000000000000000000000000000000001" +
                        "0000000000000000000000000000000000000000000000000000000000000002" +
                        "0000000000000000000000000000000000000000000000000000000000000020" +
                        "03" +
                        "ffff" +
                        "8000000000000000000000000000000000000000000000000000000000000000" +
                        "07"); // "07" should be ignored by data parser

        assertEquals(768, contract.getGasForData(data4));

        byte[] res4 = contract.execute(data4);
        assertEquals(32, res4.length);
        assertEquals(new BigInteger("26689440342447178617115869845918039756797228267049433585260346420242739014315"), BIUtil.toBI(res4));

        byte[] data5 = Hex.decode(
                "0000000000000000000000000000000000000000000000000000000000000001" +
                        "0000000000000000000000000000000000000000000000000000000000000002" +
                        "0000000000000000000000000000000000000000000000000000000000000020" +
                        "03" +
                        "ffff" +
                        "80"); // "80" should be parsed as "8000000000000000000000000000000000000000000000000000000000000000"
        // cause call data is infinitely right-padded with zero bytes

        assertEquals(768, contract.getGasForData(data5));

        byte[] res5 = contract.execute(data5);
        assertEquals(32, res5.length);
        assertEquals(new BigInteger("26689440342447178617115869845918039756797228267049433585260346420242739014315"), BIUtil.toBI(res5));

        // check overflow handling in gas calculation
        byte[] data6 = Hex.decode(
                "0000000000000000000000000000000000000000000000000000000000000020" +
                        "0000000000000000000000000000000020000000000000000000000000000000" +
                        "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff" +
                        "fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffe" +
                        "fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffd" +
                        "fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffd");

        assertEquals(Long.MAX_VALUE, contract.getGasForData(data6));

        // check rubbish data
        byte[] data7 = Hex.decode(
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff" +
                        "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff" +
                        "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff" +
                        "fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffe" +
                        "fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffd" +
                        "fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffd");

        assertEquals(Long.MAX_VALUE, contract.getGasForData(data7));

        // check empty data
        byte[] data8 = new byte[0];

        assertEquals(0, contract.getGasForData(data8));

        byte[] res8 = contract.execute(data8);
        assertArrayEquals(EMPTY_BYTE_ARRAY, res8);

        assertEquals(0, contract.getGasForData(null));
        assertArrayEquals(EMPTY_BYTE_ARRAY, contract.execute(null));
    }

    /**
     * Test Vectors for BLAKE2F are described at EIP-152 (https://github.com/ethereum/EIPs/blob/master/EIPS/eip-152.md)
     */

    @Test
    public void blake2fTestVector0() {
        internalFailBlake2fTestVector(
                "",
                PrecompiledContracts.Blake2F.BLAKE2F_ERROR_INPUT_LENGHT
        );
    }

    @Test
    public void blake2fTestVector1() {
        internalFailBlake2fTestVector(
                "00000c48c9bdf267e6096a3ba7ca8485ae67bb2bf894fe72f36e3cf1361d5f3af54fa5d182e6ad7f520e511f6c3e2b8c68059b6bbd41fbabd9831f79217e1319cde05b61626300000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000300000000000000000000000000000001",
                PrecompiledContracts.Blake2F.BLAKE2F_ERROR_INPUT_LENGHT
        );
    }

    @Test
    public void blake2fTestVector2() {
        internalFailBlake2fTestVector(
                "000000000c48c9bdf267e6096a3ba7ca8485ae67bb2bf894fe72f36e3cf1361d5f3af54fa5d182e6ad7f520e511f6c3e2b8c68059b6bbd41fbabd9831f79217e1319cde05b61626300000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000300000000000000000000000000000001",
                PrecompiledContracts.Blake2F.BLAKE2F_ERROR_INPUT_LENGHT
        );
    }

    @Test
    public void blake2fTestVector3() {
        internalFailBlake2fTestVector(
                "0000000c48c9bdf267e6096a3ba7ca8485ae67bb2bf894fe72f36e3cf1361d5f3af54fa5d182e6ad7f520e511f6c3e2b8c68059b6bbd41fbabd9831f79217e1319cde05b61626300000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000300000000000000000000000000000002",
                PrecompiledContracts.Blake2F.BLAKE2F_ERROR_FINAL_BLOCK_BYTES
        );
    }

    @Test
    public void blake2fTestVector4() throws VMException {
        internalBlake2fTestVector(
                "0000000048c9bdf267e6096a3ba7ca8485ae67bb2bf894fe72f36e3cf1361d5f3af54fa5d182e6ad7f520e511f6c3e2b8c68059b6bbd41fbabd9831f79217e1319cde05b61626300000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000300000000000000000000000000000001",
                "08c9bcf367e6096a3ba7ca8485ae67bb2bf894fe72f36e3cf1361d5f3af54fa5d282e6ad7f520e511f6c3e2b8c68059b9442be0454267ce079217e1319cde05b",
                0
        );
    }
    @Test
    public void blake2fTestVector5() throws VMException {
        internalBlake2fTestVector(
                "0000000c48c9bdf267e6096a3ba7ca8485ae67bb2bf894fe72f36e3cf1361d5f3af54fa5d182e6ad7f520e511f6c3e2b8c68059b6bbd41fbabd9831f79217e1319cde05b61626300000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000300000000000000000000000000000001",
                "ba80a53f981c4d0d6a2797b69f12f6e94c212f14685ac4b74b12bb6fdbffa2d17d87c5392aab792dc252d5de4533cc9518d38aa8dbf1925ab92386edd4009923",
                12
        );
    }
    @Test
    public void blake2fTestVector6() throws VMException {
        internalBlake2fTestVector(
                "0000000c48c9bdf267e6096a3ba7ca8485ae67bb2bf894fe72f36e3cf1361d5f3af54fa5d182e6ad7f520e511f6c3e2b8c68059b6bbd41fbabd9831f79217e1319cde05b61626300000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000300000000000000000000000000000000",
                "75ab69d3190a562c51aef8d88f1c2775876944407270c42c9844252c26d2875298743e7f6d5ea2f2d3e8d226039cd31b4e426ac4f2d3d666a610c2116fde4735",
                12
        );
    }
    @Test
    public void blake2fTestVector7() throws VMException {
        internalBlake2fTestVector(
                "0000000148c9bdf267e6096a3ba7ca8485ae67bb2bf894fe72f36e3cf1361d5f3af54fa5d182e6ad7f520e511f6c3e2b8c68059b6bbd41fbabd9831f79217e1319cde05b61626300000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000300000000000000000000000000000001",
                "b63a380cb2897d521994a85234ee2c181b5f844d2c624c002677e9703449d2fba551b3a8333bcdf5f2f7e08993d53923de3d64fcc68c034e717b9293fed7a421",
                1
        );
    }
    @Test
    @Disabled("blake2fTestVector8: This test takes a lot of time to be executed (up to 3 minutes)")
    public void blake2fTestVector8() throws VMException {
        internalBlake2fTestVector(
                "ffffffff48c9bdf267e6096a3ba7ca8485ae67bb2bf894fe72f36e3cf1361d5f3af54fa5d182e6ad7f520e511f6c3e2b8c68059b6bbd41fbabd9831f79217e1319cde05b61626300000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000300000000000000000000000000000001",
                "fc59093aafa9ab43daae0e914c57635c5402d8e3d2130eb9b3cc181de7f0ecf9b22bf99a7815ce16419e200e01846e6b5df8cc7703041bbceb571de6631d2615",
                4294967295l
        );
    }

    private void internalBlake2fTestVector(String data, String expectedResult, long expectedGasCost) throws VMException {
        PrecompiledContract contract = blake2();
        byte[] result = contract.execute(Hex.decode(data));

        assertEquals(expectedResult, Hex.toHexString(result));
        assertEquals(expectedGasCost, contract.getGasForData(Hex.decode(data)));
    }

    private void internalFailBlake2fTestVector(String data, String expectedError) {
        try {
            blake2().execute(Hex.decode(data));
        } catch (IllegalArgumentException | VMException e) {
            assertEquals(expectedError, e.getMessage());
        }
    }

    private PrecompiledContract blake2() throws VMException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP153)).thenReturn(true);

        DataWord addr = DataWord.valueFromHex("0000000000000000000000000000000000000000000000000000000000000009");
        PrecompiledContract contract = precompiledContracts.getContractForAddress(activations, addr);
        assertNotNull(contract);

        return contract;
    }

    @Test
    public void blake2fTestOnNotActivatedHardFork() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP153)).thenReturn(false);

        DataWord addr = DataWord.valueFromHex("0000000000000000000000000000000000000000000000000000000000000009");
        PrecompiledContract contract = precompiledContracts.getContractForAddress(activations, addr);
        assertNull(contract);
    }

}
