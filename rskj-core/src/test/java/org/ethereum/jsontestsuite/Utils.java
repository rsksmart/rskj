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

import org.ethereum.util.ByteUtil;
import org.bouncycastle.util.encoders.Hex;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.math.BigInteger;


import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.ethereum.util.Utils.unifiedNumericToBigInteger;

/**
 * @author Roman Mandeleil
 * @since 15.12.2014
 */
public class Utils {

    public static byte[] parseVarData(String data){
        if (data == null || data.equals("")) return EMPTY_BYTE_ARRAY;
        if (data.startsWith("0x")) {
            data = data.substring(2);
            if (data.equals("")) return EMPTY_BYTE_ARRAY;

            if (data.length() % 2 == 1) data = "0" + data;

            return Hex.decode(data);
        }

        return parseNumericData(data);
    }


    public static byte[] parseData(String data) {
        if (data == null) return EMPTY_BYTE_ARRAY;
        if (data.startsWith("0x")) data = data.substring(2);
        return Hex.decode(data);
    }

    public static byte[] parseNumericData(String data){

        if (data == null || data.equals("")) return EMPTY_BYTE_ARRAY;
        byte[] dataB = unifiedNumericToBigInteger(data).toByteArray();
        return ByteUtil.stripLeadingZeroes(dataB);
    }

    public static long parseLong(String data) {
        boolean hex = data.startsWith("0x");
        if (hex) data = data.substring(2);
        if (data.equals("")) return 0;
        return new BigInteger(data, hex ? 16 : 10).longValue();
    }

    public static byte parseByte(String data) {
        if (data.startsWith("0x")) {
            data = data.substring(2);
            return data.equals("") ? 0 : Byte.parseByte(data, 16);
        } else
            return data.equals("") ? 0 : Byte.parseByte(data);
    }


    public static String parseUnidentifiedBase(String number) {
        if (number.startsWith("0x"))
          number = new BigInteger(number.substring(2), 16).toString(10);
        return number;
    }

    @SuppressWarnings("unchecked")
    public static String translateGeneralStateTestToStateTest(String generalStateJsonSuite) throws ParseException {
        JSONParser parser = new JSONParser();
        JSONObject generalStateSuite = (JSONObject) parser.parse(generalStateJsonSuite);
        generalStateSuite.keySet().forEach(keyStr ->
        {
            JSONObject keyvalue = (JSONObject) generalStateSuite.get(keyStr);
            JSONObject stateTest = translateTestCase(keyvalue);
            generalStateSuite.replace(keyStr,stateTest);
        });

        return generalStateSuite.toString();
    }

    @SuppressWarnings("unchecked")
    private static JSONObject translateTestCase(JSONObject testKey){
        JSONObject stateJson = new JSONObject();
        stateJson.put("env", testKey.get("env"));
        stateJson.put("pre", testKey.get("pre"));
        stateJson.put("post", null);
        stateJson.put("out", null);

        JSONObject post = (JSONObject)((JSONArray)((JSONObject)testKey.get("post")).get("Constantinople")).get(0);
        stateJson.put("postStateRoot", post.get("hash"));

        JSONObject transaction = (JSONObject)testKey.get("transaction");
        int dataPos = ((Long)((JSONObject)post.get("indexes")).get("data")).intValue();
        int valuePos = ((Long)((JSONObject)post.get("indexes")).get("gas")).intValue();
        int gasPos = ((Long)((JSONObject)post.get("indexes")).get("value")).intValue();


        transaction.replace("data", ((JSONArray)transaction.get("data")).get(dataPos));
        transaction.replace("gasLimit", ((JSONArray)transaction.get("gasLimit")).get(gasPos));
        transaction.replace("value", ((JSONArray)transaction.get("value")).get(valuePos));
        stateJson.put("logs", new JSONArray());

        stateJson.put("transaction",transaction);

        return stateJson;
    }
}
