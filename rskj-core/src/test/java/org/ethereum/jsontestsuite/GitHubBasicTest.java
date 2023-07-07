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
import co.rsk.core.DifficultyCalculator;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockHeader;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.MethodOrderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Mikhail Kalinin
 * @since 02.09.2015
 */
@TestMethodOrder(MethodOrderer.MethodName.class)
@Disabled
class GitHubBasicTest {

    private static TestSystemProperties config = new TestSystemProperties();
    private static final Logger logger = LoggerFactory.getLogger("TCK-Test");
    private static final DifficultyCalculator DIFFICULTY_CALCULATOR = new DifficultyCalculator(config.getActivationConfig(), config.getNetworkConstants());

    public String shacommit = "99afe8f5aad7bca5d0f1b1685390a4dea32d73c3";

    @Test
    void runDifficultyTest() throws IOException {
        BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());

        String json = JSONReader.loadJSONFromCommit("BasicTests/difficulty.json", shacommit);

        DifficultyTestSuite testSuite = new DifficultyTestSuite(json);

        for (DifficultyTestingCase testCase : testSuite.getTestCases()) {

            logger.info("Running {}\n", testCase.getName());

            BlockHeader current = testCase.getCurrent(blockFactory);
            BlockHeader parent = testCase.getParent(blockFactory);

            assertEquals(testCase.getExpectedDifficulty(), DIFFICULTY_CALCULATOR.calcDifficulty(current, parent));
        }
    }

    @Test
    void runDifficultyFrontierTest() throws IOException {

        BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());

        String json = JSONReader.loadJSONFromCommit("BasicTests/difficultyFrontier.json", shacommit);

        DifficultyTestSuite testSuite = new DifficultyTestSuite(json);

        for (DifficultyTestingCase testCase : testSuite.getTestCases()) {

            logger.info("Running {}\n", testCase.getName());

            BlockHeader current = testCase.getCurrent(blockFactory);
            BlockHeader parent = testCase.getParent(blockFactory);

            assertEquals(testCase.getExpectedDifficulty(), DIFFICULTY_CALCULATOR.calcDifficulty(current, parent));
        }
    }

    @Test
    void runDifficultyHomesteadTest() throws IOException {

        BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());

        String json = JSONReader.loadJSONFromCommit("BasicTests/difficultyHomestead.json", shacommit);

        DifficultyTestSuite testSuite = new DifficultyTestSuite(json);

        for (DifficultyTestingCase testCase : testSuite.getTestCases()) {

            logger.info("Running {}\n", testCase.getName());

            BlockHeader current = testCase.getCurrent(blockFactory);
            BlockHeader parent = testCase.getParent(blockFactory);

            assertEquals(testCase.getExpectedDifficulty(), DIFFICULTY_CALCULATOR.calcDifficulty(current, parent));
        }
    }
}
