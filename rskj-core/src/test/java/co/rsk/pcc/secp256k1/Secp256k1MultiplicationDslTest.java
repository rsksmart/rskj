/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
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

package co.rsk.pcc.secp256k1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileNotFoundException;

import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.core.util.TransactionReceiptUtil;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.typesafe.config.ConfigValueFactory;

import co.rsk.config.TestSystemProperties;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;

/**
 * Simple DSL-based test for Secp256k1Multiplication precompiled contract.
 * This test deploys a simple contract that calls the Secp256k1Multiplication
 * precompiled contract and verifies that the function call succeeds
 * and the expected event is emitted.
 */
class Secp256k1MultiplicationDslTest {

    private static final String DSL_SECP256K1_MULTIPLICATION_TXT = "dsl/ec_precompiled_contracts/secp256k1_multiplication.txt";
    public static final double EC_DEFAULT_MULTIPLICATION_GAS_COST = 3000;

    private World world;
    private TestSystemProperties config;

    @BeforeEach
    void setUp() {
        // Configure test system with RSKIP516 enabled
        config = new TestSystemProperties(rawConfig -> rawConfig
                .withValue("consensusRules.rskip516", ConfigValueFactory.fromAnyRef(0)));
    }

    @Test
    void testSecp256k1MultiplicationWithRSKIP516Enabled() throws FileNotFoundException, DslProcessorException {
        // given
        DslParser parser = DslParser.fromResource(DSL_SECP256K1_MULTIPLICATION_TXT);
        world = new World(config);
        WorldDslProcessor processor = new WorldDslProcessor(world);

        // when
        processor.processCommands(parser);

        // then
        assertTransactionWasExecutedWithSuccess("tx01");

        TransactionReceipt txReceipt = assertTransactionWasExecutedWithSuccess("tx02");

        assertEquals(1, TransactionReceiptUtil.getEventCount(txReceipt, "OK", new String[] { "string" }));

        verifyGasConsumption("tx02");
    }

    @Test
    void testSecp256k1MultiplicationWithRSKIP516Disabled() throws FileNotFoundException, DslProcessorException {
        // given
        TestSystemProperties configDisabled = new TestSystemProperties(rawConfig -> rawConfig
                .withValue("blockchain.config.consensusRules.rskip516", ConfigValueFactory.fromAnyRef(-1)));
        DslParser parser = DslParser.fromResource(DSL_SECP256K1_MULTIPLICATION_TXT);
        world = new World(configDisabled);
        WorldDslProcessor processor = new WorldDslProcessor(world);

        // when
        processor.processCommands(parser);

        // then
        assertTransactionWasExecutedWithSuccess("tx01");

        TransactionReceipt txReceipt = assertTransactionWasExecutedWithSuccess("tx02");
        assertEquals(1, TransactionReceiptUtil.getEventCount(txReceipt, "ERROR", new String[] { "string" }));
    }

    @Test
    void testPrecompiledContractAddress() {
        // Verify that the precompiled contract address is correctly defined
        assertEquals("0000000000000000000000000000000001000017",
                PrecompiledContracts.SECP256K1_MULTIPLICATION_ADDR_STR);
    }

    private TransactionReceipt assertTransactionWasExecutedWithSuccess(String transactionName) {
        Transaction transaction = world.getTransactionByName(transactionName);
        assertNotNull(transaction, "Transaction " + transactionName + " should exist");

        TransactionReceipt receipt = world.getTransactionReceiptByName(transactionName);
        assertNotNull(receipt, "Transaction receipt for " + transactionName + " should exist");

        assertTrue(receipt.isSuccessful(), "Transaction " + transactionName + " should be successful");

        return receipt;
    }

    private void verifyGasConsumption(String transactionName) {
        TransactionReceipt receipt = world.getTransactionReceiptByName(transactionName);
        assertNotNull(receipt, "Transaction receipt for " + transactionName + " should exist");

        // The actual gas used should be at least the expected gas cost for the
        // precompiled contract
        // plus some overhead for the transaction itself
        byte[] gasUsedBytes = receipt.getGasUsed();
        long actualGasUsed = new java.math.BigInteger(1, gasUsedBytes).longValue();
        assertTrue(actualGasUsed >= EC_DEFAULT_MULTIPLICATION_GAS_COST,
                "Transaction " + transactionName + " should use at least " + EC_DEFAULT_MULTIPLICATION_GAS_COST
                        + " gas, but used "
                        + actualGasUsed);
    }
}