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

package org.ethereum.jsontestsuite;

import co.rsk.config.TestSystemProperties;
import org.json.simple.parser.ParseException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.ethereum.jsontestsuite.JSONReader.getFileNamesForTreeSha;

@TestMethodOrder(MethodOrderer.MethodName.class)
@Disabled
public class GitHubStateTest {

    //SHACOMMIT of tested commit, ethereum/tests.git
    public String shacommit = "f28ac81493281feec0b17290565cf74042893677";


    private long oldForkValue;
    private static TestSystemProperties config;

    @BeforeAll
    public static void setup() {
        // TODO remove this after Homestead launch and shacommit update with actual block number
        // for this JSON test commit the Homestead block was defined as 900000
        config = new TestSystemProperties();
    }

    @Disabled
    @Test // this method is mostly for hands-on convenient testing
    public void stSingleTest() throws ParseException, IOException {
        String json = JSONReader.loadJSONFromCommit("StateTests/stSystemOperationsTest.json", shacommit);
        GitHubJSONTestSuite.runStateTest(json, "suicideSendEtherPostDeath");
    }

    @Test
    public void stExample() throws ParseException, IOException {

        Set<String> excluded = new HashSet<>();
        String json = JSONReader.loadJSONFromCommit("StateTests/stExample.json", shacommit);
        GitHubJSONTestSuite.runStateTest(json, excluded);
    }

    @Test
    public void stCallCodes() throws ParseException, IOException {

        Set<String> excluded = new HashSet<>();
        String json = JSONReader.loadJSONFromCommit("StateTests/stCallCodes.json", shacommit);
        GitHubJSONTestSuite.runStateTest(json, excluded);

    }

    @Test
    public void stCallDelegateCodes() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();

        String json = JSONReader.loadJSONFromCommit("StateTests/stCallDelegateCodes.json", shacommit);
        GitHubJSONTestSuite.runStateTest(json, excluded);
    }

    @Test
    public void stCallDelegateCodesCallCode() throws ParseException, IOException {

        Set<String> excluded = new HashSet<>();
        String json = JSONReader.loadJSONFromCommit("StateTests/stCallDelegateCodesCallCode.json", shacommit);
        GitHubJSONTestSuite.runStateTest(json, excluded);
    }

    @Test
    public void stHomeSteadSpecific() throws ParseException, IOException {

        Set<String> excluded = new HashSet<>();
        String json = JSONReader.loadJSONFromCommit("StateTests/stHomeSteadSpecific.json", shacommit);
        GitHubJSONTestSuite.runStateTest(json, excluded);
    }

    @Test
    public void stCallCreateCallCodeTest() throws ParseException, IOException {

        Set<String> excluded = new HashSet<>();
        String json = JSONReader.loadJSONFromCommit("StateTests/stCallCreateCallCodeTest.json", shacommit);
        GitHubJSONTestSuite.runStateTest(json, excluded);

        json = JSONReader.loadJSONFromCommit("StateTests/stCallCreateCallCodeTest.json", shacommit);
        GitHubJSONTestSuite.runStateTest(json, excluded);
    }

    @Test
    public void stDelegatecallTest() throws ParseException, IOException {

        Set<String> excluded = new HashSet<>();
        String json = JSONReader.loadJSONFromCommit("StateTests/stDelegatecallTest.json", shacommit);
        GitHubJSONTestSuite.runStateTest(json, excluded);
    }

    @Test
    public void stInitCodeTest() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();
        String json = JSONReader.loadJSONFromCommit("StateTests/stInitCodeTest.json", shacommit);
        GitHubJSONTestSuite.runStateTest(json, excluded);

    }

    @Test
    public void stLogTests() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();
        String json = JSONReader.loadJSONFromCommit("StateTests/stLogTests.json", shacommit);
        GitHubJSONTestSuite.runStateTest(json, excluded);

    }

    @Test
    public void stPreCompiledContracts() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();

        String json = JSONReader.loadJSONFromCommit("StateTests/stPreCompiledContracts.json", shacommit);
        GitHubJSONTestSuite.runStateTest(json, excluded);

    }

    @Test
    public void stMemoryStressTest() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();
        excluded.add("mload32bitBound_return2");// The test extends memory to 4Gb which can't be handled with Java arrays
        excluded.add("mload32bitBound_return"); // The test extends memory to 4Gb which can't be handled with Java arrays
        excluded.add("mload32bitBound_Msize"); // The test extends memory to 4Gb which can't be handled with Java arrays
        String json = JSONReader.loadJSONFromCommit("StateTests/stMemoryStressTest.json", shacommit);
        GitHubJSONTestSuite.runStateTest(json, excluded);

    }

    @Test
    public void stMemoryTest() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();

        String json = JSONReader.loadJSONFromCommit("StateTests/stMemoryTest.json", shacommit);
        GitHubJSONTestSuite.runStateTest(json, excluded);

    }

    @Test
    public void stQuadraticComplexityTest() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();

        String json = JSONReader.loadJSONFromCommit("StateTests/stQuadraticComplexityTest.json", shacommit);
        GitHubJSONTestSuite.runStateTest(json, excluded);
    }

    @Test
    public void stSolidityTest() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();
        String json = JSONReader.loadJSONFromCommit("StateTests/stSolidityTest.json", shacommit);
        GitHubJSONTestSuite.runStateTest(json, excluded);
    }

    @Test
    public void stRecursiveCreate() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();
        String json = JSONReader.loadJSONFromCommit("StateTests/stRecursiveCreate.json", shacommit);
        GitHubJSONTestSuite.runStateTest(json, excluded);

    }

    @Test
    public void stRefundTest() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();

        String json = JSONReader.loadJSONFromCommit("StateTests/stRefundTest.json", shacommit);
        GitHubJSONTestSuite.runStateTest(json, excluded);

    }

    @Test
    public void stSpecialTest() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();

        String json = JSONReader.loadJSONFromCommit("StateTests/stSpecialTest.json", shacommit);
        GitHubJSONTestSuite.runStateTest(json, excluded);

    }

    @Test
    public void stBlockHashTest() throws ParseException, IOException {
        String json = JSONReader.loadJSONFromCommit("StateTests/stBlockHashTest.json", shacommit);
        GitHubJSONTestSuite.runStateTest(json);
    }

    @Test
    public void stSystemOperationsTest() throws IOException {
        Set<String> excluded = new HashSet<>();

        String json = JSONReader.loadJSONFromCommit("StateTests/stSystemOperationsTest.json", shacommit);
        GitHubJSONTestSuite.runStateTest(json, excluded);

        json = JSONReader.loadJSONFromCommit("StateTests/stSystemOperationsTest.json", shacommit);
        GitHubJSONTestSuite.runStateTest(json, excluded);
    }

    @Test
    public void stTransactionTest() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();

        String json = JSONReader.loadJSONFromCommit("StateTests/stTransactionTest.json", shacommit);
        GitHubJSONTestSuite.runStateTest(json, excluded);

    }

    @Test
    public void stTransitionTest() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();

        String json = JSONReader.loadJSONFromCommit("StateTests/stTransitionTest.json", shacommit);
        GitHubJSONTestSuite.runStateTest(json, excluded);
    }

    @Test
    public void stWalletTest() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();

        String json = JSONReader.loadJSONFromCommit("StateTests/stWalletTest.json", shacommit);
        GitHubJSONTestSuite.runStateTest(json, excluded);

    }

    @Test // testing full suite
    public void testRandomStateGitHub() throws ParseException, IOException {

        String sha = "99db6f4f5fea3aa5cfbe8436feba8e213d06d1e8";
        List<String> fileNames = getFileNamesForTreeSha(sha);
        List<String> includedFiles =
                Arrays.asList(
                        "st201504081841JAVA.json",
                        "st201504081842JAVA.json",
                        "st201504081843JAVA.json"
                );

        for (String fileName : fileNames) {
            if (includedFiles.contains(fileName)) {
              System.out.println("Running: " + fileName);
              String json = JSONReader.loadJSON("StateTests//RandomTests/" + fileName);
              GitHubJSONTestSuite.runStateTest(json);
            }
        }

    }
}

