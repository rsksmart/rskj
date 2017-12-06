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

import co.rsk.config.RskSystemProperties;
import co.rsk.core.DifficultyCalculator;
import org.ethereum.config.net.MainNetConfig;
import org.ethereum.core.BlockHeader;
import org.ethereum.jsontestsuite.DifficultyTestCase;
import org.ethereum.jsontestsuite.DifficultyTestSuite;
import org.ethereum.jsontestsuite.JSONReader;
import org.json.simple.parser.ParseException;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigInteger;

import static co.rsk.config.RskSystemProperties.CONFIG;
import static org.junit.Assert.assertEquals;

/**
 * @author Angel J Lopez
 * @since 02.23.2016
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class LocalBasicTest {

    private static final Logger logger = LoggerFactory.getLogger("TCK-Test");

    @Before
    public void setup() {

        // if not set explicitly
        // this test fails being run by Gradle
        CONFIG.setGenesisInfo("frontier.json");
    }


    @Test
    public void runDifficultyTest() throws IOException, ParseException {

        RskSystemProperties.CONFIG.setBlockchainConfig(MainNetConfig.INSTANCE);

        String json = getJSON("difficulty");

        DifficultyTestSuite testSuite = new DifficultyTestSuite(json);

        for (DifficultyTestCase testCase : testSuite.getTestCases()) {

            logger.info("Running {}\n", testCase.getName());

            BlockHeader current = testCase.getCurrent();
            BlockHeader parent = testCase.getParent();
            BigInteger calc = new DifficultyCalculator(RskSystemProperties.CONFIG).calcDifficulty(current, parent);
            int c = calc.compareTo(parent.getDifficultyBI());
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
