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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JavaType;
import org.ethereum.jsontestsuite.JSONReader;
import org.ethereum.jsontestsuite.RLPTestCase;
import org.json.simple.parser.ParseException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Angel J Lopez
 * @since 02.24.2016
 */
@TestMethodOrder(MethodOrderer.MethodName.class)
public class LocalRLPTest {

    private static Logger logger = LoggerFactory.getLogger("rlp");
    private static HashMap<String , RLPTestCase> TEST_SUITE;

    @BeforeAll
    public static void init() throws ParseException, IOException {
        logger.info("    Initializing RLP tests...");
        String json = getJSON("rlptest");

        Assumptions.assumeFalse(json.equals(""), "Local test is not available");

        ObjectMapper mapper = new ObjectMapper();
        JavaType type = mapper.getTypeFactory().
                constructMapType(HashMap.class, String.class, RLPTestCase.class);

        TEST_SUITE = mapper.readValue(json, type);
    }

    @Test
    public void rlpEncodeTest() throws Exception {
        logger.info("    Testing RLP encoding...");

        for (String key : TEST_SUITE.keySet()) {
            logger.info("    " + key);
            RLPTestCase testCase = TEST_SUITE.get(key);
            testCase.doEncode();
            Assertions.assertEquals(testCase.getExpected(), testCase.getComputed());
        }
    }

    @Test
    public void rlpDecodeTest() throws Exception {
        logger.info("    Testing RLP decoding...");

        Set<String> excluded = new HashSet<>();

        for (String key : TEST_SUITE.keySet()) {
            if ( excluded.contains(key)) {
                logger.info("[X] " + key);
                continue;
            }
            else {
                logger.info("    " + key);
            }

            RLPTestCase testCase = TEST_SUITE.get(key);
            testCase.doDecode();
            Assertions.assertEquals(testCase.getExpected(), testCase.getComputed());
        }
    }

    private static String getJSON(String name) {
        String json = JSONReader.loadJSONFromResource("json/RLPTests/" + name + ".json", LocalVMTest.class.getClassLoader());
        return json;
    }
}
