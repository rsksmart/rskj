/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
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
package org.ethereum.rpc.parameters;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.vm.DataWord;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for HexDataArrayParam parameter class.
 */
class HexDataArrayParamTest {

    @Test
    void testEmptyArray() {
        HexDataArrayParam param = new HexDataArrayParam(Collections.emptyList());

        assertTrue(param.isEmpty());
        assertEquals(0, param.size());
        assertTrue(param.getDataList().isEmpty());
        assertTrue(param.getAsDataWords().isEmpty());
    }

    @Test
    void testSingleElement() {
        List<String> hexStrings = List.of("0x0000000000000000000000000000000000000000000000000000000000000000");
        HexDataArrayParam param = new HexDataArrayParam(hexStrings);

        assertFalse(param.isEmpty());
        assertEquals(1, param.size());
        assertEquals(1, param.getDataList().size());
        assertEquals(1, param.getAsDataWords().size());
    }

    @Test
    void testMultipleElements() {
        List<String> hexStrings = List.of(
                "0x0000000000000000000000000000000000000000000000000000000000000000",
                "0x0000000000000000000000000000000000000000000000000000000000000001",
                "0x0000000000000000000000000000000000000000000000000000000000000002"
        );
        HexDataArrayParam param = new HexDataArrayParam(hexStrings);

        assertEquals(3, param.size());
        assertEquals(3, param.getDataList().size());
        assertEquals(3, param.getAsDataWords().size());
    }

    @Test
    void testGetAsDataWords() {
        List<String> hexStrings = List.of(
                "0x0000000000000000000000000000000000000000000000000000000000000001",
                "0x0000000000000000000000000000000000000000000000000000000000000002"
        );
        HexDataArrayParam param = new HexDataArrayParam(hexStrings);

        List<DataWord> dataWords = param.getAsDataWords();

        assertEquals(2, dataWords.size());
        assertEquals(DataWord.valueOf(1), dataWords.get(0));
        assertEquals(DataWord.valueOf(2), dataWords.get(1));
    }

    @Test
    void testShortHexValue() {
        // Short hex values should be padded when converted to DataWord
        List<String> hexStrings = List.of("0x42");
        HexDataArrayParam param = new HexDataArrayParam(hexStrings);

        List<DataWord> dataWords = param.getAsDataWords();
        assertEquals(DataWord.valueOf(0x42), dataWords.get(0));
    }

    @Test
    void testNullList() {
        HexDataArrayParam param = new HexDataArrayParam(null);

        assertTrue(param.isEmpty());
        assertEquals(0, param.size());
    }

    @Test
    void testInvalidHexString() {
        List<String> hexStrings = List.of("invalid");

        assertThrows(RskJsonRpcRequestException.class, () -> new HexDataArrayParam(hexStrings));
    }

    @Test
    void testInvalidHexStringAtIndex() {
        List<String> hexStrings = List.of(
                "0x0000000000000000000000000000000000000000000000000000000000000001",
                "not_hex"
        );

        RskJsonRpcRequestException ex = assertThrows(
                RskJsonRpcRequestException.class, 
                () -> new HexDataArrayParam(hexStrings)
        );
        assertTrue(ex.getMessage().contains("index 1"));
    }

    @Test
    void testEquality() {
        List<String> hexStrings = List.of("0x01", "0x02");
        HexDataArrayParam param1 = new HexDataArrayParam(hexStrings);
        HexDataArrayParam param2 = new HexDataArrayParam(hexStrings);

        assertEquals(param1, param2);
        assertEquals(param1.hashCode(), param2.hashCode());
    }

    @Test
    void testInequalityDifferentSize() {
        HexDataArrayParam param1 = new HexDataArrayParam(List.of("0x01"));
        HexDataArrayParam param2 = new HexDataArrayParam(List.of("0x01", "0x02"));

        assertNotEquals(param1, param2);
    }

    @Test
    void testInequalityDifferentValues() {
        HexDataArrayParam param1 = new HexDataArrayParam(List.of("0x01"));
        HexDataArrayParam param2 = new HexDataArrayParam(List.of("0x02"));

        assertNotEquals(param1, param2);
    }

    @Test
    void testJsonDeserialization() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String json = "[\"0x01\", \"0x02\", \"0x03\"]";

        HexDataArrayParam param = mapper.readValue(json, HexDataArrayParam.class);

        assertEquals(3, param.size());
    }

    @Test
    void testJsonDeserializationEmptyArray() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String json = "[]";

        HexDataArrayParam param = mapper.readValue(json, HexDataArrayParam.class);

        assertTrue(param.isEmpty());
    }
}
