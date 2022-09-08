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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JavaType;
import org.json.simple.parser.ParseException;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.MethodOrderer;

import java.io.IOException;
import java.util.HashMap;


@TestMethodOrder(MethodOrderer.MethodName.class)
@Disabled
public class GitHubCryptoTest {


    @Test
    public void testAllInCryptoSute() throws ParseException, IOException {

        String json = JSONReader.loadJSON("BasicTests/crypto.json");

        ObjectMapper mapper = new ObjectMapper();
        JavaType type = mapper.getTypeFactory().
                constructMapType(HashMap.class, String.class, CryptoTestCase.class);


        HashMap<String , CryptoTestCase> testSuite =
                mapper.readValue(json, type);

        for (String key : testSuite.keySet()){

            System.out.println("executing: " + key);
            testSuite.get(key).execute();

        }
    }


}
