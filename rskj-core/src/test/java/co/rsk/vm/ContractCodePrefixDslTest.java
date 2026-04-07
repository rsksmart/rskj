/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
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

package co.rsk.vm;

import co.rsk.config.TestSystemProperties;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import com.typesafe.config.ConfigValueFactory;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.util.ByteUtil;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DSL Tests for RSKIP544/EIP-3541: Reject new contract code starting with the 0xEF byte
 */
class ContractCodePrefixDslTest {

    @Test
    void testCreateFailsWithEFByteAfterRSKIP544Activation() 
            throws FileNotFoundException, DslProcessorException {
        TestSystemProperties properties = new TestSystemProperties(rawConfig ->
            rawConfig.withValue(
                "blockchain.config.consensusRules.rskip544",
                ConfigValueFactory.fromAnyRef(0)
            )
        );
        
        World world = createWorldAndProcess(
            "dsl/contract_code_prefix_rskip544/create_fails_with_ef_byte_activated.txt", 
            properties
        );
        
        TransactionReceipt receipt = world.getTransactionReceiptByName("txCreateWithEF");
        assertNotNull(receipt);
        assertFalse(receipt.isSuccessful());
        
        long gasUsed = ByteUtil.byteArrayToLong(receipt.getGasUsed());
        assertEquals(300000, gasUsed);
    }

    @Test
    void testCreateSucceedsWithEFByteBeforeRSKIP544Activation() 
            throws FileNotFoundException, DslProcessorException {
        TestSystemProperties properties = new TestSystemProperties(rawConfig ->
            rawConfig.withValue(
                "blockchain.config.consensusRules.rskip544", 
                ConfigValueFactory.fromAnyRef(-1)
            )
        );
        
        World world = createWorldAndProcess(
            "dsl/contract_code_prefix_rskip544/create_fails_with_ef_byte_activated.txt", 
            properties
        );
        
        TransactionReceipt receipt = world.getTransactionReceiptByName("txCreateWithEF");
        assertNotNull(receipt);
        assertTrue(receipt.isSuccessful());
        
        long gasUsed = ByteUtil.byteArrayToLong(receipt.getGasUsed());
        assertTrue(gasUsed > 21000);
        assertTrue(gasUsed < 300000);
    }

    @Test
    void testCreateSucceedsWithFEByte() 
            throws FileNotFoundException, DslProcessorException {
        TestSystemProperties properties = new TestSystemProperties(rawConfig ->
            rawConfig.withValue(
                "blockchain.config.consensusRules.rskip544",
                ConfigValueFactory.fromAnyRef(0)
            )
        );
        
        World world = createWorldAndProcess(
            "dsl/contract_code_prefix_rskip544/create_succeeds_with_fe_byte.txt", 
            properties
        );
        
        TransactionReceipt receipt = world.getTransactionReceiptByName("txCreateWithFE");
        assertNotNull(receipt);
        assertTrue(receipt.isSuccessful());
        
        long gasUsed = ByteUtil.byteArrayToLong(receipt.getGasUsed());
        assertTrue(gasUsed > 21000 && gasUsed < 300000);
    }

    @Test
    void testCreateSucceedsWithEmptyCode() 
            throws FileNotFoundException, DslProcessorException {
        TestSystemProperties properties = new TestSystemProperties(rawConfig ->
            rawConfig.withValue(
                "blockchain.config.consensusRules.rskip544",
                ConfigValueFactory.fromAnyRef(0)
            )
        );
        
        World world = createWorldAndProcess(
            "dsl/contract_code_prefix_rskip544/create_succeeds_with_empty_code.txt", 
            properties
        );
        
        TransactionReceipt receipt = world.getTransactionReceiptByName("txCreateWithEmpty");
        assertNotNull(receipt);
        assertTrue(receipt.isSuccessful());
        
        long gasUsed = ByteUtil.byteArrayToLong(receipt.getGasUsed());
        assertTrue(gasUsed > 21000);
        assertTrue(gasUsed < 300000);
    }

    @Test
    void testGasConsumptionComparison() 
            throws FileNotFoundException, DslProcessorException {
        TestSystemProperties properties = new TestSystemProperties(rawConfig ->
            rawConfig.withValue(
                "blockchain.config.consensusRules.rskip544",
                ConfigValueFactory.fromAnyRef(0)
            )
        );
        
        World worldFailed = createWorldAndProcess(
            "dsl/contract_code_prefix_rskip544/create_fails_with_ef_byte_activated.txt", 
            properties
        );
        TransactionReceipt receiptFailed = worldFailed.getTransactionReceiptByName("txCreateWithEF");
        long gasUsedFailed = ByteUtil.byteArrayToLong(receiptFailed.getGasUsed());
        
        World worldSuccess = createWorldAndProcess(
            "dsl/contract_code_prefix_rskip544/create_succeeds_with_fe_byte.txt", 
            properties
        );
        TransactionReceipt receiptSuccess = worldSuccess.getTransactionReceiptByName("txCreateWithFE");
        long gasUsedSuccess = ByteUtil.byteArrayToLong(receiptSuccess.getGasUsed());
        
        assertTrue(gasUsedFailed > 21000);
        assertTrue(gasUsedSuccess > 21000 && gasUsedSuccess < 300000);
    }

    private World createWorldAndProcess(String resourcePath, TestSystemProperties properties) 
            throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource(resourcePath);
        World world = new World(properties);
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);
        return world;
    }
}
