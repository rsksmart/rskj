/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.jsontestsuite;

import org.ethereum.jsontestsuite.GitHubJSONTestSuite;
import org.ethereum.jsontestsuite.JSONReader;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.MethodOrderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * @author Angel J Lopez
 * @since 02.23.2016
 */
@TestMethodOrder(MethodOrderer.MethodName.class)
@SuppressWarnings("squid:S1607") // many @Disabled annotations for diverse reasons
class LocalVMTest {
    private Logger logger = LoggerFactory.getLogger("VM-Test");
    @Test
    void runSingle() throws IOException {
        String json = getJSON("vmEnvironmentalInfoTest");
        GitHubJSONTestSuite.runGitHubJsonVMTest(json, "balance0");
    }

    @Test
    void testArithmetic() throws IOException {
        Set<String> excluded = new HashSet<>();
        Set<String> included = null;
        //included  =new HashSet<>();
        //included.add("expXY");
        // TODO: these are excluded due to bad wrapping behavior in ADDMOD/DataWord.add
        String json = getJSON("vmArithmeticTest");
        GitHubJSONTestSuite.runGitHubJsonVMTest(json, excluded,included);
    }

    @Test // testing full suite
    void testBitwiseLogicOperation() throws IOException {
        Set<String> excluded = new HashSet<>();
        String json = getJSON("vmBitwiseLogicOperationTest");
        GitHubJSONTestSuite.runGitHubJsonVMTest(json, excluded);
    }

    @Test // testing full suite
    void testBlockInfo() throws IOException {
        Set<String> excluded = new HashSet<>();
        String json = getJSON("vmBlockInfoTest");
        GitHubJSONTestSuite.runGitHubJsonVMTest(json, excluded);
    }

    @Test // testing full suite
    void testEnvironmentalInfo() throws IOException {
        Set<String> excluded = new HashSet<>();
        String json = getJSON("vmEnvironmentalInfoTest");
        GitHubJSONTestSuite.runGitHubJsonVMTest(json, excluded);
    }

    @Test // testing full suite
    void testIOandFlowOperations() throws IOException {
        Set<String> excluded = new HashSet<>();
        String json = getJSON("vmIOandFlowOperationsTest");
        GitHubJSONTestSuite.runGitHubJsonVMTest(json, excluded);
    }

    @Test
    void testvmInputLimits() throws IOException {
        Set<String> excluded = new HashSet<>();
        String json = getJSON("vmInputLimits");
        GitHubJSONTestSuite.runGitHubJsonVMTest(json, excluded);
    }

    @Disabled //FIXME - 20M - possibly provide percentage indicator
    @Test
    void testvmInputLimitsLight() throws IOException {
        Set<String> excluded = new HashSet<>();
        String json = getJSON("vmInputLimitsLight");
        GitHubJSONTestSuite.runGitHubJsonVMTest(json, excluded);
    }

    @Test // testing full suite
    void testVMLog() throws IOException {
        Set<String> excluded = new HashSet<>();
        String json = getJSON("vmLogTest");
        GitHubJSONTestSuite.runGitHubJsonVMTest(json, excluded);
    }
    @Disabled
    @Test // testing full suite
    void testPerformance() throws IOException {
        Set<String> excluded = new HashSet<>();
        String json = getJSON("vmPerformanceTest");
        GitHubJSONTestSuite.runGitHubJsonVMTest(json, excluded);
    }

    @Test // testing full suite
    void testPushDupSwap() throws IOException {
        Set<String> excluded = new HashSet<>();
        String json = getJSON("vmPushDupSwapTest");
        GitHubJSONTestSuite.runGitHubJsonVMTest(json, excluded);
    }

    @Test // testing full suite
    void testSha() throws IOException {
        Set<String> excluded = new HashSet<>();
        String json = getJSON("vmSha3Test");
        GitHubJSONTestSuite.runGitHubJsonVMTest(json, excluded);
    }

    // This ignore and fixme is at Ethereum code
    @Disabled
    @Test // testing full suite
    void testvmSystemOperationsTest() throws IOException {
        Set<String> excluded = new HashSet<>();

        String json = getJSON("vmSystemOperationsTest");
        GitHubJSONTestSuite.runGitHubJsonVMTest(json, excluded);
    }

    @Test // testing full suite
    void testVM() throws IOException {
        Set<String> excluded = new HashSet<>();
        String json = getJSON("vmtests");
        GitHubJSONTestSuite.runGitHubJsonVMTest(json, excluded);
    }

    // TODO get name of resources
    @Test // testing full suite
    void testRandomVM() throws IOException {
        List<String> resources =
                Arrays.asList(
                        //--TODO: These cases have been commented out after adding the remaining gas condition
                        //--in doCall method in VM.java. This will be addressed in next release.
                        //"201503102037PYTHON",
                        //"201503102148PYTHON",
                        //"201503102300PYTHON",
                        //"201503102320PYTHON",
                        //"201503110050PYTHON",
                        "201503110206PYTHON",
                        "201503110219PYTHON",
                        "201503110226PYTHON_DUP6",
                        "201503110346PYTHON_PUSH24",
                        "201503110526PYTHON",
                        "201503111844PYTHON",
                        "201503112218PYTHON",
                        "201503120317PYTHON",
                        "201503120525PYTHON",
                        "201503120547PYTHON",
                        "201503120909PYTHON",
                        "randomTest"
                );

        for (String resource : resources) {
            String json = getJSON("RandomTests/" + resource);
            Set<String> excluded = new HashSet<>();
            GitHubJSONTestSuite.runGitHubJsonVMTest(json, excluded);
        }
    }

    private String getJSON(String name) {
        String fullName = "json/VMTests/" + name + ".json";
        logger.info("Reading resource "+fullName);
        String json = JSONReader.loadJSONFromResource(fullName, LocalVMTest.class.getClassLoader());
        return json;
    }
}
