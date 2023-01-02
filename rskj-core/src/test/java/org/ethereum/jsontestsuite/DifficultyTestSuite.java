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

import java.io.IOException;
import java.util.*;

/**
 * @author Mikhail Kalinin
 * @since 02.09.2015
 */
public class DifficultyTestSuite {

    List<DifficultyTestingCase> testCases = new ArrayList<>();

    public DifficultyTestSuite(String json) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JavaType type = mapper.getTypeFactory().
                constructMapType(HashMap.class, String.class, DifficultyTestingCase.class);

        Map<String, DifficultyTestingCase> caseMap = new ObjectMapper().readValue(json, type);

        for (Map.Entry<String, DifficultyTestingCase> e : caseMap.entrySet()) {
            e.getValue().setName(e.getKey());
            testCases.add(e.getValue());
        }

        Collections.sort(testCases, new Comparator<DifficultyTestingCase>() {
            @Override
            public int compare(DifficultyTestingCase t1, DifficultyTestingCase t2) {
                return t1.getName().compareTo(t2.getName());
            }
        });
    }

    public List<DifficultyTestingCase> getTestCases() {
        return testCases;
    }

    @Override
    public String toString() {
        return "DifficultyTestSuite{" +
                "testCases=" + testCases +
                '}';
    }
}
