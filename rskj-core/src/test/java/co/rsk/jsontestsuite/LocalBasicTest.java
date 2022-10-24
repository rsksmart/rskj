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

import co.rsk.core.BlockDifficulty;
import co.rsk.core.DifficultyCalculator;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockHeader;
import org.ethereum.jsontestsuite.DifficultyTestCase;
import org.ethereum.jsontestsuite.DifficultyTestSuite;
import org.ethereum.jsontestsuite.JSONReader;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Angel J Lopez
 * @since 02.23.2016
 */
class LocalBasicTest {

    private static final Logger logger = LoggerFactory.getLogger("TCK-Test");
    private final Constants networkConstants = Constants.mainnet();
    private ActivationConfig activationConfig = ActivationConfigsForTest.allBut();

    @Test
    void runDifficultyTestBeforeRSKIP156() throws IOException {
        activationConfig = ActivationConfigsForTest.allBut(ConsensusRule.RSKIP156);
        String jsonName = "difficultyBeforeRSKIP156";
        runJsonTest(jsonName);
    }

    @Test
    void runDifficultyTestBeforeRSKIP290() throws IOException {
        activationConfig = ActivationConfigsForTest.allBut(ConsensusRule.RSKIP290);
        Constants networkConstants = Constants.testnet(activationConfig);
        String jsonName = "difficulty";
        runJsonTest(jsonName, activationConfig, networkConstants);
    }

    @Test
    void runDifficultyTestAfterRSKIP290() throws IOException {
        activationConfig = ActivationConfigsForTest.all();
        Constants networkConstants = Constants.testnet(activationConfig);
        String jsonName = "difficulty1";
        runJsonTest(jsonName, activationConfig, networkConstants);
    }

    @Test
    void runDifficultyTest() throws IOException {
        String jsonName = "difficulty";
        runJsonTest(jsonName);
    }

    private void runJsonTest(String jsonName) throws IOException {
        runJsonTest(jsonName, activationConfig, this.networkConstants);
    }

    private void runJsonTest(
            String jsonName,
            ActivationConfig activationConfig,
            Constants networkConstants
            ) throws IOException {
        BlockFactory blockFactory = new BlockFactory(activationConfig);

        String json = getJSON(jsonName);

        DifficultyTestSuite testSuite = new DifficultyTestSuite(json);

        for (DifficultyTestCase testCase : testSuite.getTestCases()) {

            logger.info("Running {}\n", testCase.getName());

            BlockHeader current = testCase.getCurrent(blockFactory);
            BlockHeader parent = testCase.getParent(blockFactory);
            BlockDifficulty calc = new DifficultyCalculator(activationConfig, networkConstants).calcDifficulty(current, parent);
            int c = calc.compareTo(parent.getDifficulty());
            if (c > 0)
                logger.info(" Difficulty increase test\n");
            else if (c < 0)
                logger.info(" Difficulty decrease test\n");
            else
                logger.info(" Difficulty without change test\n");


            assertEquals(testCase.getExpectedDifficulty(), calc);
        }
    }


    private static String getJSON(String name) {
        String json = JSONReader.loadJSONFromResource("json/BasicTests/" + name + ".json", LocalVMTest.class.getClassLoader());
        return json;
    }
}
