package org.ethereum.rpc.parameters;

import co.rsk.util.HexUtils;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class HexRawDataParamTest {
    @Test
    public void testValidHexRawDataParam() {
        String validHexRawData = "0xf892038609184e72a0008276c094e7b8e91401bf4d1669f54dc5f98109d7efbc4eea849184e72aa9d46e8dd67c5d32be8d46e8dd67c5d32be8058bb8eb970870f072445675058bb8eb970870f07244567565a0ea179f22ab9149013f3330a0921cef8fc8fddc84f3e51746ef8d5a35e04bfd8ba020e5640606a3257f32b9ef2f706c7294666d4bf759093769d40e7fdd2ac2f49e";

        HexRawDataParam hexRawDataParam = new HexRawDataParam(validHexRawData);

        assertEquals(validHexRawData, HexUtils.toUnformattedJsonHex(hexRawDataParam.getRawDataBytes()));
    }

    @Test
    public void testInvalidHexRawDataParam() {
        String invalidHexRawData = "0xsv92038609184e72a0008276c094e7b8e91401bf4d1669f54dc5f98109d7efbc4eea849184e72aa9d46e8dd67c5d32be8d46e8dd67c5d32be8058bb8eb970870f072445675058bb8eb970870f07244567565a0ea179f22ab9149013f3330a0921cef8fc8fddc84f3e51746ef8d5a35e04bfd8ba020e5640606a3257f32b9ef2f706c7294666d4bf759093769d40e7fdd2ac2f49e";
        String shorterHexRawData = "0xf892038609184e72a00082";

        assertThrows(RskJsonRpcRequestException.class, () -> new HexRawDataParam(invalidHexRawData));
        assertThrows(RskJsonRpcRequestException.class, () -> new HexRawDataParam(shorterHexRawData));
    }
}
