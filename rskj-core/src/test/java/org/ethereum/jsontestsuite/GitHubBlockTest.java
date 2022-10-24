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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.MethodOrderer;

import java.io.IOException;
import java.util.Collections;

@TestMethodOrder(MethodOrderer.MethodName.class)
@Disabled
class GitHubBlockTest {

    //SHACOMMIT of tested commit, ethereum/tests.git
    public String shacommit = "0895e096ca9de6ba745bad238cb579964bd90cea";

    @Disabled("test for conveniently running a single test")
    @Test
    void runSingleTest() throws IOException {
        TestSystemProperties config = new TestSystemProperties();
        config.setGenesisInfo("frontier.json");

        String json = JSONReader.loadJSONFromCommit("BlockchainTests/Homestead/bcTotalDifficultyTest.json", shacommit);
        GitHubJSONTestSuite.runGitHubJsonSingleBlockTest(json, "sideChainWithNewMaxDifficultyStartingFromBlock3AfterBlock4");
    }

    private void runFrontier(String name) throws IOException {
        String json = JSONReader.loadJSONFromCommit("BlockchainTests/" + name + ".json", shacommit);
        GitHubJSONTestSuite.runGitHubJsonBlockTest(json, Collections.EMPTY_SET);
    }

    private void runHomestead(String name) throws IOException {
        String json = JSONReader.loadJSONFromCommit("BlockchainTests/Homestead/" + name + ".json", shacommit);
        TestSystemProperties config = new TestSystemProperties();
        GitHubJSONTestSuite.runGitHubJsonBlockTest(json, Collections.EMPTY_SET);
    }

    private void run(String name, boolean frontier, boolean homestead) throws IOException {
        if (frontier) runFrontier(name);
        if (homestead) runHomestead(name);
    }

    @Test
    void runBCInvalidHeaderTest() throws IOException {
        run("bcInvalidHeaderTest", true, true);
    }


    @Test
    void runBCInvalidRLPTest() throws IOException {
        run("bcInvalidRLPTest", true, false);
    }

    @Test
    void runBCRPCAPITest() throws IOException {
        run("bcRPC_API_Test", true, true);
    }


    @Test
    void runBCUncleHeaderValidityTest() throws IOException {
        run("bcUncleHeaderValiditiy", true, true);
    }

    @Test
     void runBCUncleTest() throws IOException {
        run("bcUncleTest", true, true);
    }

    @Disabled("test for conveniently running a single test")
    @Test
    void runBCValidBlockTest() throws IOException {
        TestSystemProperties config = new TestSystemProperties();
        config.setGenesisInfo("frontier.json");
        run("bcValidBlockTest", true, true);
    }

    @Test
    void runBCBlockGasLimitTest() throws IOException {
        run("bcBlockGasLimitTest", true, true);
    }

    @Test
    void runBCForkBlockTest() throws IOException {
        run("bcForkBlockTest", true, false);
    }

    @Test
    void runBCForkUncleTest() throws IOException {
        run("bcForkUncle", true, false);
    }

    @Test
    void runBCForkStressTest() throws IOException {
        run("bcForkStressTest", true, true);
    }

    @Disabled("test for conveniently running a single test")
    @Test
    void runBCStateTest() throws IOException {
        run("bcStateTest", true, true);
    }

    @Test
    void runBCGasPricerTest() throws IOException {
        run("bcGasPricerTest", true, true);
    }

    @Test
    void runBCTotalDifficultyTest() throws IOException {
        run("bcTotalDifficultyTest", false, true);
    }

    @Test
    void runBCWalletTest() throws Exception {
        run("bcWalletTest", true, true);
    }

    @Test
    void runBCMultiChainTest() throws IOException {
        run("bcMultiChainTest", true, true);
    }
}
