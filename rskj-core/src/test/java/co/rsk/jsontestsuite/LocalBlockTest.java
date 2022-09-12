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

import co.rsk.config.TestSystemProperties;
import org.ethereum.jsontestsuite.GitHubJSONTestSuite;
import org.ethereum.jsontestsuite.JSONReader;
import org.json.simple.parser.ParseException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Disabled;

import java.io.IOException;
import java.util.Collections;

/**
 * @author Angel J Lopez
 * @since 02.23.2016
 */
@TestMethodOrder(MethodOrderer.MethodName.class)
@SuppressWarnings("squid:S1607") // many @Disabled annotations for diverse reasons
class LocalBlockTest {
    private ClassLoader loader = LocalBlockTest.class.getClassLoader();
    private static TestSystemProperties config = new TestSystemProperties();

    @BeforeAll
     static void init() {
        config.setRemascEnabled(false);
    }

    private void run(String name) throws IOException, ParseException {
        String json = JSONReader.loadJSONFromResource("json/BlockchainTests/" + name + ".json", LocalBlockTest.class.getClassLoader());
        GitHubJSONTestSuite.runGitHubJsonBlockTest(json, Collections.EMPTY_SET);
    }

    @Disabled // to fix after adding prefix to tx raw encode
    @Test
    void runSingleTest() throws ParseException, IOException {
        config.setGenesisInfo("frontier.json");

        String json = JSONReader.loadJSONFromResource("json/BlockchainTests/bcValidBlockTest.json", loader);
        GitHubJSONTestSuite.runGitHubJsonSingleBlockTest(json, "RecallSuicidedContractInOneBlock");
    }

    @Test
    @Disabled
    void runBCInvalidHeaderTest() throws ParseException, IOException {
        run("bcInvalidHeaderTest");
    }

    @Test
    @Disabled
    void runBCInvalidRLPTest() throws ParseException, IOException {
        run("bcInvalidRLPTest");
    }

    @Test
    @Disabled
    void runBCRPCAPITest() throws ParseException, IOException {
        run("bcRPC_API_Test");
    }

    @Disabled // to fix after adding prefix to tx raw encode
    @Test
    void runBCUncleHeaderValidityTest() throws ParseException, IOException {
        run("bcUncleHeaderValiditiy");
    }

    @Disabled // to fix after adding prefix to tx raw encode
    @Test
    void runBCUncleTest() throws ParseException, IOException {
        run("bcUncleTest");
    }

    @Test
    @Disabled
    void runBCValidBlockTest() throws ParseException, IOException {
        config.setGenesisInfo("frontier.json");
        run("bcValidBlockTest");
    }

    @Disabled // after adding tx prefix to sign
    @Test
    void runBCBlockGasLimitTest() throws ParseException, IOException {
        run("bcBlockGasLimitTest");
    }

    @Test
    @Disabled
    void runBCForkBlockTest() throws ParseException, IOException {
        run("bcForkBlockTest");
    }

    @Disabled // after adding tx prefix to sign
    @Test
    void runBCForkUncleTest() throws ParseException, IOException {
        run("bcForkUncle");
    }

    @Disabled // to fix after adding prefix to tx raw encode
    @Test
    void runBCForkStressTest() throws ParseException, IOException {
        run("bcForkStressTest");
    }

    @Test
    @Disabled
    void runBCStateTest() throws ParseException, IOException {
        run("bcStateTest");
    }

    @Disabled // to fix after adding prefix to tx raw encode
    @Test
    void runBCGasPricerTest() throws ParseException, IOException {
        run("bcGasPricerTest");
    }

    @Disabled // to fix after adding prefix to tx raw encode
    @Test
    void runBCTotalDifficultyTest() throws ParseException, IOException {
        run("bcTotalDifficultyTest");
    }

    @Disabled
    @Test
    void runBCWalletTest() throws Exception, IOException {
        run("bcWalletTest");
    }

    @Disabled
    @Test
    void runBCMultiChainTest() throws ParseException, IOException {
        run("bcMultiChainTest");
    }
}
