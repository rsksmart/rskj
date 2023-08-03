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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.FileNotFoundException;

import org.apache.commons.lang3.NotImplementedException;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.vm.PrecompiledContracts.PrecompiledContract;
import org.ethereum.vm.exception.VMException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import co.rsk.config.TestSystemProperties;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;

// todo(fedejinich) this should/will be moved to PrecompiledContractTest
class BFVPrecompiledContractTest {
    private final TestSystemProperties config = new TestSystemProperties();
    private final PrecompiledContracts precompiledContracts = new PrecompiledContracts(config, null,
            new BlockTxSignatureCache(new ReceivedTxSignatureCache()));

    @Test
    public void bfvAddTest() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIPBFV)).thenReturn(true);
        byte[] data = Hex.decode(
                "18c547e4f7b0f325ad1e56f57e26c745b09a3e503d86e00e5255ff7f715d3d1c000000000000000000000000000000000000000000000000000000000000001c73b1693892219d736caba55bdb67216e485557ea6b6af75f37096c9aa6a5a75feeb940b1d03b21e36b0e47e79769f095fe2ab855bd91e3a38756b7d75a9c4549");
        DataWord addr = DataWord.valueFromHex("0000000000000000000000000000000001000011");
        PrecompiledContract contract = precompiledContracts.getContractForAddress(activations, addr);

        try {
            contract.execute(data);
        } catch (NotImplementedException e) {
            assertEquals("bfv add should be implemented", e.getMessage());
            try {
                contract.getGasForData(data);
            } catch (NotImplementedException n) {
                assertEquals("bfv add gas should be implemented", n.getMessage());
                return;
            }
        } catch (VMException e) {
            fail("execute() unexpected error");
        }

        fail("bfv add unexpected behavior");
    }

    @Test
    public void bfvSubTest() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIPBFV)).thenReturn(true);
        byte[] data = Hex.decode(
                "18c547e4f7b0f325ad1e56f57e26c745b09a3e503d86e00e5255ff7f715d3d1c000000000000000000000000000000000000000000000000000000000000001c73b1693892219d736caba55bdb67216e485557ea6b6af75f37096c9aa6a5a75feeb940b1d03b21e36b0e47e79769f095fe2ab855bd91e3a38756b7d75a9c4549");
        DataWord addr = DataWord.valueFromHex("0000000000000000000000000000000001000012");
        PrecompiledContract contract = precompiledContracts.getContractForAddress(activations, addr);

        try {
            contract.execute(data);
        } catch (NotImplementedException e) {
            assertEquals("bfv sub should be implemented", e.getMessage());
            try {
                contract.getGasForData(data);
            } catch (NotImplementedException n) {
                assertEquals("bfv sub gas should be implemented", n.getMessage());
                return;
            }
        } catch (VMException e) {
            fail("execute() unexpected error");
        }

        fail("bfv sub unexpected behavior");
    }

    @Test
    public void bfvMulTest() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIPBFV)).thenReturn(true);
        byte[] data = Hex.decode(
                "18c547e4f7b0f325ad1e56f57e26c745b09a3e503d86e00e5255ff7f715d3d1c000000000000000000000000000000000000000000000000000000000000001c73b1693892219d736caba55bdb67216e485557ea6b6af75f37096c9aa6a5a75feeb940b1d03b21e36b0e47e79769f095fe2ab855bd91e3a38756b7d75a9c4549");
        DataWord addr = DataWord.valueFromHex("0000000000000000000000000000000001000013");
        PrecompiledContract contract = precompiledContracts.getContractForAddress(activations, addr);

        try {
            contract.execute(data);
        } catch (NotImplementedException e) {
            assertEquals("bfv mul should be implemented", e.getMessage());
            try {
                contract.getGasForData(data);
            } catch (NotImplementedException n) {
                assertEquals("bfv mul gas should be implemented", n.getMessage());
                return;
            }
        } catch (VMException e) {
            fail("execute() unexpected error");
        }

        fail("bfv mul unexpected behavior");
    }

    @Test
    public void bfvTranTest() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIPBFV)).thenReturn(true);
        byte[] data = Hex.decode(
                "18c547e4f7b0f325ad1e56f57e26c745b09a3e503d86e00e5255ff7f715d3d1c000000000000000000000000000000000000000000000000000000000000001c73b1693892219d736caba55bdb67216e485557ea6b6af75f37096c9aa6a5a75feeb940b1d03b21e36b0e47e79769f095fe2ab855bd91e3a38756b7d75a9c4549");
        DataWord addr = DataWord.valueFromHex("0000000000000000000000000000000001000014");
        PrecompiledContract contract = precompiledContracts.getContractForAddress(activations, addr);

        try {
            contract.execute(data);
        } catch (NotImplementedException e) {
            assertEquals("bfv tran should be implemented", e.getMessage());
            try {
                contract.getGasForData(data);
            } catch (NotImplementedException n) {
                assertEquals("bfv tran gas should be implemented", n.getMessage());
                return;
            }
        } catch (VMException e) {
            fail("execute() unexpected error");
        }

        fail("bfv tran unexpected behavior");
    }

    @Test
    public void testAddDsl() throws FileNotFoundException, DslProcessorException {
        // TestSystemProperties config = new TestSystemProperties(rawConfig ->
        // rawConfig.withValue("blockchain.config.hardforkActivationHeights.iris300",
        // ConfigValueFactory.fromAnyRef(5))
        // );
        TestSystemProperties config = new TestSystemProperties();
        World world = new World(config);
        WorldDslProcessor processor = new WorldDslProcessor(world);

        DslParser parser = DslParser.fromResource("dsl/bfv/bfv_add_test.txt");

        processor.processCommands(parser);

        Assertions.assertTrue(true);
    }

}
