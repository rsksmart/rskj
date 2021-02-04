package co.rsk.pcc;

/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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


import co.rsk.config.TestSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.db.RepositorySnapshot;
import co.rsk.pcc.bls12dot381.AbstractBLS12PrecompiledContract;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import com.google.common.base.Strings;
import com.google.common.io.CharStreams;
import com.google.common.primitives.Bytes;
import com.typesafe.config.ConfigValueFactory;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.exception.VMException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.ethereum.rpc.exception.RskJsonRpcRequestException.invalidParamError;
import static org.ethereum.util.ByteUtil.stripLeadingZeroes;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;



public class BLS12PrecompiledContractTest {


    private TestSystemProperties config;
    private PrecompiledContracts precompiledContracts;

    @Before
    public void setup() {
        this.config = new TestSystemProperties(rawConfig ->
                rawConfig.withValue("blockchain.config.hardforkActivationHeights.iris300", ConfigValueFactory.fromAnyRef(0))
        );
        this.precompiledContracts = new PrecompiledContracts(config, null);
    }

    //simple test, equivalent to first example of g1_add.csv
    @Test
    public void bls12G1AddPrecompiledContractTest() throws VMException {

        DataWord addr = DataWord.valueFromHex("000000000000000000000000000000000000000000000000000000000000000A");
        PrecompiledContracts.PrecompiledContract contract = precompiledContracts.getContractForAddress(config.getActivationConfig().forBlock(0), addr);
        byte[] input = Hex.decode(
                "0000000000000000000000000000000012196c5a43d69224d8713389285f26b98f86ee910ab3dd668e413738282003cc5b7357af9a7af54bb713d62255e80f560000000000000000000000000000000006ba8102bfbeea4416b710c73e8cce3032c31c6269c44906f8ac4f7874ce99fb17559992486528963884ce429a992fee000000000000000000000000000000000001101098f5c39893765766af4512a0c74e1bb89bc7e6fdf14e3e7337d257cc0f94658179d83320b99f31ff94cd2bac0000000000000000000000000000000003e1a9f9f44ca2cdab4f43a1a3ee3470fdf90b2fc228eb3b709fcd72f014838ac82a6d797aeefed9a0804b22ed1ce8f7"
        );
        String expected = "000000000000000000000000000000001466e1373ae4a7e7ba885c5f0c3ccfa48cdb50661646ac6b779952f466ac9fc92730dcaed9be831cd1f8c4fefffd5209000000000000000000000000000000000c1fb750d2285d4ca0378e1e8cdbf6044151867c34a711b73ae818aee6dbe9e886f53d7928cc6ed9c851e0422f609b11";

        byte[] result = contract.execute(input);

        assertEquals(expected, ByteUtil.toHexString(result));
    }

    /*

    //dsl test equivalent to the same example
    @Test
    public void fullDslBbls12G1AddPrecompiledContractTest() throws FileNotFoundException, DslProcessorException {
        World world = new World(config);
        WorldDslProcessor processor = new WorldDslProcessor(world);
        DslParser parser = DslParser.fromResource("dsl/bls12/bls12_example.txt");

        processor.processCommands(parser);

        TransactionReceipt tx02 = world.getTransactionReceiptByName("tx02");

        List<LogInfo> logInfoList = tx02.getLogInfoList();
        assertEquals(4, logInfoList.size());

        byte[] data = logInfoList.get(0).getData();
        byte[] data1 = logInfoList.get(1).getData();
        byte[] data2 = logInfoList.get(2).getData();
        byte[] data3 = logInfoList.get(3).getData();

        byte[] concat = Bytes.concat(data, data1, data2, data3);

        String expected = "000000000000000000000000000000001466e1373ae4a7e7ba885c5f0c3ccfa48cdb50661646ac6b779952f466ac9fc92730dcaed9be831cd1f8c4fefffd5209000000000000000000000000000000000c1fb750d2285d4ca0378e1e8cdbf6044151867c34a711b73ae818aee6dbe9e886f53d7928cc6ed9c851e0422f609b11";
        assertEquals(expected, ByteUtil.toHexString(concat));
    }

    @Test
    public void dslErrorBbls12G1AddPrecompiledContractTest() throws FileNotFoundException, DslProcessorException {
        World world = new World(config);
        WorldDslProcessor processor = new WorldDslProcessor(world);
        DslParser parser = DslParser.fromResource("dsl/bls12/bls12_error_example.txt");

        processor.processCommands(parser);

        TransactionReceipt tx02 = world.getTransactionReceiptByName("tx02");

        List<LogInfo> logInfoList = tx02.getLogInfoList();
        assertEquals(1, logInfoList.size());

        byte[] errorCode = logInfoList.get(0).getData();

        assertEquals("0000000000000000000000000000000000000000000000000000000000000aaa", ByteUtil.toHexString(errorCode));
    }


    @Test
    public void testG1Add() throws IOException {
        testPrecompileAtAddressWithCsv("000000000000000000000000000000000000000000000000000000000000000A", "g1_add.csv");
    }

    @Test
    public void testG1Mul() throws IOException {
        testPrecompileAtAddressWithCsv("000000000000000000000000000000000000000000000000000000000000000B", "g1_mul.csv");
    }

    @Test
    public void testG1MultiExp() throws IOException {
        testPrecompileAtAddressWithCsv("000000000000000000000000000000000000000000000000000000000000000C", "g1_multiexp.csv");
    }

    @Test
    public void testG2Add() throws IOException {
        testPrecompileAtAddressWithCsv("000000000000000000000000000000000000000000000000000000000000000D", "g2_add.csv");
    }

    @Test
    public void testG2Mul() throws IOException {
        testPrecompileAtAddressWithCsv("000000000000000000000000000000000000000000000000000000000000000E", "g2_mul.csv");
    }

    @Test
    public void testG2MultiExp() throws IOException {
        testPrecompileAtAddressWithCsv("000000000000000000000000000000000000000000000000000000000000000F", "g2_multiexp.csv");
    }
    @Test
    public void testPairing() throws IOException {
        testPrecompileAtAddressWithCsv("0000000000000000000000000000000000000000000000000000000000000010", "pairing.csv");
        testPrecompileAtAddressWithCsv("0000000000000000000000000000000000000000000000000000000000000010", "invalid_subgroup_for_pairing.csv");
    }
    @Test
    public void testFP1ToG1() throws IOException {
        testPrecompileAtAddressWithCsv("0000000000000000000000000000000000000000000000000000000000000011", "fp_to_g1.csv");
    }
    @Test
    public void testFP2toG2() throws IOException {
        testPrecompileAtAddressWithCsv("0000000000000000000000000000000000000000000000000000000000000012", "fp2_to_g2.csv");
    }

    public void testPrecompileAtAddressWithCsv(String address, String csv) throws IOException {
        Iterable<String[]> strings = readCsvTestCases(csv);

        strings.forEach(s -> {
            String input = s[0];
            String expectedResult = s[1];
            String expectedGasUsed = s[2];
            String notes = s[3];

            if ("input".equals(input)) {
                // skip the header row
                return;
            }
            testSinglePrecompileCase(address, removeLeading(input), removeLeading(expectedResult), expectedGasUsed, notes);

        });
    }

    private void testSinglePrecompileCase(String address, String input, String expectedResult, String expectedGasUsed, String notes) {
        byte[] inputAsByteArray = Hex.decode(input);
        DataWord addr = DataWord.valueFromHex(address);
        PrecompiledContracts.PrecompiledContract contract = precompiledContracts.getContractForAddress(config.getActivationConfig().forBlock(0), addr);

        if(!Strings.isNullOrEmpty(expectedResult)) {
            byte[] result = contract.execute(inputAsByteArray);
            assertNotNull("Result should be not null for input: " + input, result);
            String resultAsHexString = ByteUtil.toHexString(result);
            assertEquals(expectedResult, resultAsHexString);
            assertEquals(Long.parseLong(expectedGasUsed), contract.getGasForData(inputAsByteArray));
        }
        else {
            try{
                contract.execute(inputAsByteArray);
                Assert.fail("Exception expected");
            }
            catch(AbstractBLS12PrecompiledContract.BLS12FailureException e) {
                assertEquals(notes, ((AbstractBLS12PrecompiledContract) contract ).getFailureReason());
            }
        }
    }

    private String removeLeading(String x) {
        if(!Strings.isNullOrEmpty(x) && x.startsWith("0x")) {
            return x.substring(2);
        }
        else {
            return x;
        }
    }

    public static Iterable<String[]> readCsvTestCases(String fileName) throws IOException {
        InputStream resourceAsStream = BLS12PrecompiledContractTest.class.getResourceAsStream(fileName);
        return CharStreams.readLines(
                new InputStreamReader(resourceAsStream, UTF_8))
                .stream()
                .map(line -> line.split(",", 4))
                .collect(Collectors.toList());
    }

*/
}