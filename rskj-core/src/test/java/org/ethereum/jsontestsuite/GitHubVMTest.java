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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.MethodOrderer;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.ethereum.jsontestsuite.JSONReader.getFileNamesForTreeSha;

@TestMethodOrder(MethodOrderer.MethodName.class)
@Disabled
class GitHubVMTest {

    //SHACOMMIT of tested commit, ethereum/tests.git
    public String shacommit = "f28ac81493281feec0b17290565cf74042893677";

    @Test
    void runSingle() throws IOException {
        String json = JSONReader.loadJSONFromCommit("VMTests/vmEnvironmentalInfoTest.json", shacommit);
        GitHubJSONTestSuite.runGitHubJsonVMTest(json, "balance0");
    }

    @Test
    void testArithmeticFromGitHub() throws IOException {
        Set<String> excluded = new HashSet<>();
        // TODO: these are excluded due to bad wrapping behavior in ADDMOD/DataWord.add
        String json = JSONReader.loadJSONFromCommit("VMTests/vmArithmeticTest.json", shacommit);
        //String json = JSONReader.getTestBlobForTreeSha(shacommit, "vmArithmeticTest.json");
        GitHubJSONTestSuite.runGitHubJsonVMTest(json, excluded);
    }

    @Test // testing full suite
    void testBitwiseLogicOperationFromGitHub() throws IOException {
        Set<String> excluded = new HashSet<>();
        String json = JSONReader.loadJSONFromCommit("VMTests/vmBitwiseLogicOperationTest.json", shacommit);
        GitHubJSONTestSuite.runGitHubJsonVMTest(json, excluded);
    }

    @Test // testing full suite
    void testBlockInfoFromGitHub() throws IOException {
        Set<String> excluded = new HashSet<>();
        String json = JSONReader.loadJSONFromCommit("VMTests/vmBlockInfoTest.json", shacommit);
        GitHubJSONTestSuite.runGitHubJsonVMTest(json, excluded);
    }

    @Test // testing full suite
    void testEnvironmentalInfoFromGitHub() throws IOException {
        Set<String> excluded = new HashSet<>();
        String json = JSONReader.loadJSONFromCommit("VMTests/vmEnvironmentalInfoTest.json", shacommit);
        GitHubJSONTestSuite.runGitHubJsonVMTest(json, excluded);
    }

    @Test // testing full suite
    void testIOandFlowOperationsFromGitHub() throws IOException {
        Set<String> excluded = new HashSet<>();
        String json = JSONReader.loadJSONFromCommit("VMTests/vmIOandFlowOperationsTest.json", shacommit);
        GitHubJSONTestSuite.runGitHubJsonVMTest(json, excluded);
    }

    @Disabled("//FIXME - 60M - need new fast downloader")
    @Test
    void testvmInputLimitsTest1FromGitHub() throws IOException {
        Set<String> excluded = new HashSet<>();
        String json = JSONReader.loadJSONFromCommit("VMTests/vmInputLimits1.json", shacommit);
        GitHubJSONTestSuite.runGitHubJsonVMTest(json, excluded);
    }

    @Disabled("//FIXME - 50M - need to handle large filesizes")
    @Test
    void testvmInputLimitsTest2FromGitHub() throws IOException {
        Set<String> excluded = new HashSet<>();
        String json = JSONReader.loadJSONFromCommit("VMTests/vmInputLimits2.json", shacommit);
        GitHubJSONTestSuite.runGitHubJsonVMTest(json, excluded);
    }

    @Disabled("//FIXME - 20M - possibly provide percentage indicator")
    @Test
    void testvmInputLimitsLightTestFromGitHub() throws IOException {
        Set<String> excluded = new HashSet<>();
        String json = JSONReader.loadJSONFromCommit("VMTests/vmInputLimitsLight.json", shacommit);
        GitHubJSONTestSuite.runGitHubJsonVMTest(json, excluded);
    }

    @Test // testing full suite
    void testVMLogGitHub() throws IOException {
        Set<String> excluded = new HashSet<>();
        String json = JSONReader.loadJSONFromCommit("VMTests/vmLogTest.json", shacommit);
        GitHubJSONTestSuite.runGitHubJsonVMTest(json, excluded);
    }

    @Test // testing full suite
    void testPerformanceFromGitHub() throws IOException {
        Set<String> excluded = new HashSet<>();
        String json = JSONReader.loadJSONFromCommit("VMTests/vmPerformanceTest.json", shacommit);
        GitHubJSONTestSuite.runGitHubJsonVMTest(json, excluded);
    }

    @Test // testing full suite
    void testPushDupSwapFromGitHub() throws IOException {
        Set<String> excluded = new HashSet<>();
        String json = JSONReader.loadJSONFromCommit("VMTests/vmPushDupSwapTest.json", shacommit);
        GitHubJSONTestSuite.runGitHubJsonVMTest(json, excluded);
    }

    @Test // testing full suite
    void testShaFromGitHub() throws IOException {
        Set<String> excluded = new HashSet<>();
        String json = JSONReader.loadJSONFromCommit("VMTests/vmSha3Test.json", shacommit);
        GitHubJSONTestSuite.runGitHubJsonVMTest(json, excluded);
    }

    @Disabled("FIXME: as soon as possible")
    @Test // testing full suite
    void testvmSystemOperationsTestGitHub() throws IOException {
        Set<String> excluded = new HashSet<>();

        String json = JSONReader.loadJSONFromCommit("VMTests/vmSystemOperationsTest.json", shacommit);
        GitHubJSONTestSuite.runGitHubJsonVMTest(json, excluded);
    }

    @Test // testing full suite
    void testVMGitHub() throws IOException {
        Set<String> excluded = new HashSet<>();
        String json = JSONReader.loadJSONFromCommit("VMTests/vmtests.json", shacommit);
        GitHubJSONTestSuite.runGitHubJsonVMTest(json, excluded);
    }

    @Test // testing full suite
    void testRandomVMGitHub() throws IOException {

        String shacommit = "c5eafb85390eee59b838a93ae31bc16a5fd4f7b1";
        List<String> fileNames = getFileNamesForTreeSha(shacommit);
        List<String> excludedFiles =
                Collections.singletonList(
                        ""
                );

        for (String fileName : fileNames) {

            if (excludedFiles.contains(fileName)) continue;
            System.out.println("Running: " + fileName);
            String json = JSONReader.loadJSON("VMTests//RandomTests/" + fileName);
            GitHubJSONTestSuite.runGitHubJsonVMTest(json);
        }

    }
}
