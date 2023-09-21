/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

import co.rsk.util.HexUtils;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class HexDataParamTest {
    @Test
    public void testValidHexDataParam() {
        String validHexRawData = "0xf892038609184e72a0008276c094e7b8e91401bf4d1669f54dc5f98109d7efbc4eea849184e72aa9d46e8dd67c5d32be8d46e8dd67c5d32be8058bb8eb970870f072445675058bb8eb970870f07244567565a0ea179f22ab9149013f3330a0921cef8fc8fddc84f3e51746ef8d5a35e04bfd8ba020e5640606a3257f32b9ef2f706c7294666d4bf759093769d40e7fdd2ac2f49e";

        HexDataParam hexDataParam = new HexDataParam(validHexRawData);

        assertEquals(validHexRawData, HexUtils.toUnformattedJsonHex(hexDataParam.getRawDataBytes()));
    }

    @Test
    public void testInvalidHexDataParam() {
        String invalidHexRawData = "0xsv92038609184e72a0008276c094e7b8e91401bf4d1669f54dc5f98109d7efbc4eea849184e72aa9d46e8dd67c5d32be8d46e8dd67c5d32be8058bb8eb970870f072445675058bb8eb970870f07244567565a0ea179f22ab9149013f3330a0921cef8fc8fddc84f3e51746ef8d5a35e04bfd8ba020e5640606a3257f32b9ef2f706c7294666d4bf759093769d40e7fdd2ac2f49e";

        assertThrows(RskJsonRpcRequestException.class, () -> new HexDataParam(invalidHexRawData));
    }
}
