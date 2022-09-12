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
import org.ethereum.jsontestsuite.CryptoTestCase;
import org.ethereum.jsontestsuite.JSONReader;
import org.json.simple.parser.ParseException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.MethodOrderer;

import java.io.IOException;
import java.util.HashMap;

/**
 * @author Angel J Lopez
 * @since 02.24.2016
 */

@TestMethodOrder(MethodOrderer.MethodName.class)
class LocalCryptoTest {


    @Test
    void testAllInCryptoSute() throws ParseException, IOException {

        String json = getJSON("crypto");

        ObjectMapper mapper = new ObjectMapper();
        JavaType type = mapper.getTypeFactory().
                constructMapType(HashMap.class, String.class, CryptoTestCase.class);


        HashMap<String , CryptoTestCase> testSuite =
                mapper.readValue(json, type);

        for (String key : testSuite.keySet()){

            System.out.println("executing: " + key);
            Assertions.assertDoesNotThrow(() -> testSuite.get(key).execute());

        }
    }

    private static String getJSON(String name) {
        String json = JSONReader.loadJSONFromResource("json/BasicTests/" + name + ".json", LocalVMTest.class.getClassLoader());
        return json;
    }
}
