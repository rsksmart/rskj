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
import co.rsk.core.BlockDifficulty;
import co.rsk.core.DifficultyCalculator;
import org.ethereum.config.net.MainNetConfig;
import org.ethereum.core.BlockHeader;
import org.ethereum.jsontestsuite.DifficultyTestCase;
import org.ethereum.jsontestsuite.DifficultyTestSuite;
import org.ethereum.jsontestsuite.JSONReader;
import org.json.simple.parser.ParseException;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * @author Angel J Lopez
 * @since 02.23.2016
 */
public class LocalBasicTest {

    private static final Logger logger = LoggerFactory.getLogger("TCK-Test");
    private TestSystemProperties config = new TestSystemProperties();

    @Test
    public void runDifficultyTest() throws IOException, ParseException {
        config.setGenesisInfo("frontier.json");
        config.setBlockchainConfig(new MainNetConfig());

        String json = getJSON("difficulty");

        DifficultyTestSuite testSuite = new DifficultyTestSuite(json);

        for (DifficultyTestCase testCase : testSuite.getTestCases()) {

            logger.info("Running {}\n", testCase.getName());

            BlockHeader current = testCase.getCurrent();
            BlockHeader parent = testCase.getParent();
            BlockDifficulty calc = new DifficultyCalculator(config).calcDifficulty(current, parent);
            int c = calc.compareTo(parent.getDifficulty());
            if (c>0)
                logger.info(" Difficulty increase test\n");
            else
            if (c<0)
                logger.info(" Difficulty decrease test\n");
            else
                logger.info(" Difficulty without change test\n");


            assertEquals(testCase.getExpectedDifficulty(),calc);
        }
    }



    private static String getJSON(String name) {
        String json = JSONReader.loadJSONFromResource("json/BasicTests/" + name + ".json", LocalVMTest.class.getClassLoader());
        return json;
    }
}
