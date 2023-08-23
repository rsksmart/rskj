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

package org.ethereum.vm.bfv;

import co.rsk.config.TestSystemProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.NotImplementedException;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.PrecompiledContracts.PrecompiledContract;
import org.ethereum.vm.exception.VMException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.rsksmart.BFV;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// todo(fedejinich) this should/will be moved to PrecompiledContractTest
class BFVPrecompiledContractTest {
    private final TestSystemProperties config = new TestSystemProperties();
    private final PrecompiledContracts precompiledContracts = new PrecompiledContracts(config, null,
            new BlockTxSignatureCache(new ReceivedTxSignatureCache()));

    @Test
    public void bfvAddTest() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIPBFV)).thenReturn(true);

        BFVTestCase testCase = getTestCase("test_add");

        ByteBuffer buffer = ByteBuffer.allocate(testCase.getEl1().length +
                testCase.getEl2().length + Integer.BYTES * 2);

        buffer.putInt(testCase.getEl1().length);
        buffer.putInt(testCase.getEl2().length);
        buffer.put(testCase.getEl1());
        buffer.put(testCase.getEl2());

        byte[] data = buffer.array();

        DataWord addr = DataWord.valueFromHex("0000000000000000000000000000000001000011");
        PrecompiledContract contract = precompiledContracts.getContractForAddress(activations, addr);

        byte[] result = null;
        try {
            result = contract.execute(data);
        } catch (NotImplementedException e) {
            fail("bfv add not ok");
        } catch (VMException e) {
            fail("execute() unexpected error");
        }

        Assertions.assertNotNull(result);
        Assertions.assertArrayEquals(testCase.getExpectedResult(), result);
    }

    @Test
    public void bfvSubTest() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIPBFV)).thenReturn(true);

        BFVTestCase testCase = getTestCase("test_sub");

        ByteBuffer buffer = ByteBuffer.allocate(testCase.getEl1().length +
                testCase.getEl2().length + Integer.BYTES * 2);

        buffer.putInt(testCase.getEl1().length);
        buffer.putInt(testCase.getEl2().length);
        buffer.put(testCase.getEl1());
        buffer.put(testCase.getEl2());

        byte[] data = buffer.array();

        DataWord addr = DataWord.valueFromHex("0000000000000000000000000000000001000012");
        PrecompiledContract contract = precompiledContracts.getContractForAddress(activations, addr);

        byte[] result = null;
        try {
            result = contract.execute(data);
        } catch (NotImplementedException e) {
            fail("bfv sub not ok");
        } catch (VMException e) {
            fail("execute() unexpected error");
        }

        Assertions.assertNotNull(result);
        Assertions.assertArrayEquals(testCase.getExpectedResult(), result);
    }

    @Test
    public void bfvMulTest() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIPBFV)).thenReturn(true);

        BFVTestCase testCase = getTestCase("test_mul");

        // space for each element (op1, op2 & relinKey) + 3 sizes
        ByteBuffer buffer = ByteBuffer.allocate(
                testCase.getEl1().length +
                testCase.getEl2().length +
                testCase.getRelinearizationKey().length +
                Integer.BYTES * 3);

        buffer.putInt(testCase.getEl1().length);
        buffer.putInt(testCase.getEl2().length);
        buffer.putInt(testCase.getRelinearizationKey().length);
        buffer.put(testCase.getEl1());
        buffer.put(testCase.getEl2());
        buffer.put(testCase.getRelinearizationKey());

        byte[] data = buffer.array();

        DataWord addr = DataWord.valueFromHex("0000000000000000000000000000000001000013");
        PrecompiledContract contract = precompiledContracts.getContractForAddress(activations, addr);

        byte[] result = null;
        try {
            result = contract.execute(data);
        } catch (NotImplementedException e) {
            fail("bfv mul not ok");
        } catch (VMException e) {
            fail("execute() unexpected error");
        }

        Assertions.assertNotNull(result);
        Assertions.assertArrayEquals(testCase.getExpectedResult(), result);
    }

    @Test
    public void bfvTranscipher() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIPBFV)).thenReturn(true);

        TranscipherCase testCase = new ObjectMapper().readValue(new File(
                        "src/test/java/org/ethereum/vm/bfv/" + "test_transcipher" + ".json"),
                TranscipherCase.class);

        byte[] encryptedMessage = testCase.getEncryptedMessage();

        // space for each element (encryptedMessage, pastaSK & relinKey) + 3 sizes
        ByteBuffer buffer = ByteBuffer.allocate(
        encryptedMessage.length +
                testCase.getPastaSK().length +
                testCase.getRelinearizationKey().length +
                testCase.getBfvSK().length +
                Integer.BYTES * 4);

        buffer.putInt(encryptedMessage.length);
        buffer.putInt(testCase.getPastaSK().length);
        buffer.putInt(testCase.getRelinearizationKey().length);
        buffer.putInt(testCase.getBfvSK().length);
        buffer.put(encryptedMessage);
        buffer.put(testCase.getPastaSK());
        buffer.put(testCase.getRelinearizationKey());
        buffer.put(testCase.getBfvSK());

        byte[] data = buffer.array();

        DataWord addr = DataWord.valueFromHex("0000000000000000000000000000000001000014");
        PrecompiledContract contract = precompiledContracts.getContractForAddress(activations, addr);

        byte[] result = null;
        try {
            result = contract.execute(data); // this is a bfv ciphertext
        } catch (NotImplementedException e) {
            fail("bfv transcipher not ok");
        } catch (VMException e) {
            System.out.println(e.getMessage());
            fail("execute() unexpected error");
        }

        BFV bfv = new BFV();

        Assertions.assertNotNull(result);

        byte[] msg = testCase.getMessage();
        long[] msgLong = toLongArray(msg, msg.length / Long.BYTES, ByteOrder.LITTLE_ENDIAN);

        // decrypt
        byte[] decrypted = bfv.decrypt(result, result.length,
                testCase.getBfvSK(), testCase.getBfvSK().length);
        long[] decryptedLong = toLongArray(decrypted, msgLong.length, ByteOrder.LITTLE_ENDIAN);

        Assertions.assertArrayEquals(msgLong, decryptedLong);
    }

    private long[] toLongArray(byte[] message, int size, ByteOrder order) {
        ByteBuffer buff = ByteBuffer.wrap(message).order(order);
        long[] result = new long[size];
        for (int i = 0; i < result.length; i++) {
            result[i] = buff.getLong();
        }

        return result;
    }

    private BFVTestCase getTestCase(String expected) throws IOException {
        BFVTestCase bfvTestCase = new ObjectMapper().readValue(new File(
                        "src/test/java/org/ethereum/vm/bfv/" + expected + ".json"),
                BFVTestCase.class);

        Assertions.assertEquals(expected, bfvTestCase.getTestName());

        return bfvTestCase;
    }


}
