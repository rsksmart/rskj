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

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author Roman Mandeleil
 * @since 10.07.2014
 */
public class TestSuite {

    List<TestingCase> testList = new ArrayList<>();

    public TestSuite(JsonNode testCaseJSONObj) throws IOException {
        for (Iterator<String> it = testCaseJSONObj.fieldNames(); it.hasNext(); ) {
            String key = it.next();

            JsonNode testCaseJSON = testCaseJSONObj.get(key);
            TestingCase testCase = new TestingCase(key, testCaseJSON);
            testList.add(testCase);
        }
    }

    public List<TestingCase> getAllTests(){
        return testList;
    }

    public Iterator<TestingCase> iterator() {
        return testList.iterator();
    }
}
