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
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.IOException;
import java.util.Collections;

/**
 * @author Angel J Lopez
 * @since 02.23.2016
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class LocalBlockTest {
    private ClassLoader loader = LocalBlockTest.class.getClassLoader();
    private static TestSystemProperties config = new TestSystemProperties();

    @BeforeClass
    public static void init() {
        config.disableRemasc();
    }

    private void run(String name) throws IOException, ParseException {
        String json = JSONReader.loadJSONFromResource("json/BlockchainTests/" + name + ".json", LocalBlockTest.class.getClassLoader());
        GitHubJSONTestSuite.runGitHubJsonBlockTest(json, Collections.EMPTY_SET);
    }

    @Ignore // to fix after adding prefix to tx raw encode
    @Test
    public void runSingleTest() throws ParseException, IOException {
        config.setGenesisInfo("frontier.json");

        String json = JSONReader.loadJSONFromResource("json/BlockchainTests/bcValidBlockTest.json", loader);
        GitHubJSONTestSuite.runGitHubJsonSingleBlockTest(json, "RecallSuicidedContractInOneBlock");
    }

    @Test
    @Ignore
    public void runBCInvalidHeaderTest() throws ParseException, IOException {
        run("bcInvalidHeaderTest");
    }

    @Test
    @Ignore
    public void runBCInvalidRLPTest() throws ParseException, IOException {
        run("bcInvalidRLPTest");
    }

    @Test
    @Ignore
    public void runBCRPCAPITest() throws ParseException, IOException {
        run("bcRPC_API_Test");
    }

    @Ignore // to fix after adding prefix to tx raw encode
    @Test
    public void runBCUncleHeaderValidityTest() throws ParseException, IOException {
        run("bcUncleHeaderValiditiy");
    }

    @Ignore // to fix after adding prefix to tx raw encode
    @Test
    public void runBCUncleTest() throws ParseException, IOException {
        run("bcUncleTest");
    }

    @Test
    @Ignore
    public void runBCValidBlockTest() throws ParseException, IOException {
        config.setGenesisInfo("frontier.json");
        run("bcValidBlockTest");
    }

    @Ignore // after adding tx prefix to sign
    @Test
    public void runBCBlockGasLimitTest() throws ParseException, IOException {
        run("bcBlockGasLimitTest");
    }

    @Test
    @Ignore
    public void runBCForkBlockTest() throws ParseException, IOException {
        run("bcForkBlockTest");
    }

    @Ignore // after adding tx prefix to sign
    @Test
    public void runBCForkUncleTest() throws ParseException, IOException {
        run("bcForkUncle");
    }

    @Ignore // to fix after adding prefix to tx raw encode
    @Test
    public void runBCForkStressTest() throws ParseException, IOException {
        run("bcForkStressTest");
    }

    @Test
    @Ignore
    public void runBCStateTest() throws ParseException, IOException {
        run("bcStateTest");
    }

    @Ignore // to fix after adding prefix to tx raw encode
    @Test
    public void runBCGasPricerTest() throws ParseException, IOException {
        run("bcGasPricerTest");
    }

    @Ignore // to fix after adding prefix to tx raw encode
    @Test
    public void runBCTotalDifficultyTest() throws ParseException, IOException {
        run("bcTotalDifficultyTest");
    }

    @Ignore
    @Test
    public void runBCWalletTest() throws Exception, IOException {
        run("bcWalletTest");
    }

    @Ignore
    @Test
    public void runBCMultiChainTest() throws ParseException, IOException {
        run("bcMultiChainTest");
    }
}
