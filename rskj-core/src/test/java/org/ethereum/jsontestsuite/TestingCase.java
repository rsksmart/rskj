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

import co.rsk.core.RskAddress;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bouncycastle.util.BigIntegers;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.util.ByteUtil;

import java.io.IOException;
import java.math.BigInteger;
import java.util.*;

/**
 * @author Roman Mandeleil
 * @since 28.06.2014
 */
public class TestingCase {

    private String name = "";

    //            "env": { ... },
    private Env env;

    //
    private Logs logs;

    //            "exec": { ... },
    private Exec exec;

    //            "gas": { ... },
    private byte[] gas;

    //            "out": { ... },
    private byte[] out;

    //            "pre": { ... },
    private Map<RskAddress, AccountState> pre = new HashMap<>();

    //            "post": { ... },
    private Map<RskAddress, AccountState> post = new HashMap<>();

    //            "callcreates": { ... }
    private List<CallCreate> callCreateList = new ArrayList<>();

    public TestingCase(String name, JsonNode testCaseJSONObj) throws IOException {
        this(testCaseJSONObj);
        this.name = name;
    }

    public TestingCase(JsonNode testCaseJSONObj) throws IOException {

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode envJSON = testCaseJSONObj.get("env");
            JsonNode execJSON = testCaseJSONObj.get("exec");
            JsonNode preJSON = testCaseJSONObj.get("pre");
            ObjectNode postJSON = objectMapper.createObjectNode();
            if (testCaseJSONObj.has("post")) // in cases where there is no post dictionary (when testing for
                // exceptions for example)
                postJSON = (ObjectNode) testCaseJSONObj.get("post");
            ArrayNode callCreates = objectMapper.createArrayNode();
            if (testCaseJSONObj.has("callcreates"))
                callCreates = (ArrayNode) testCaseJSONObj.get("callcreates");

            ArrayNode logsJSON = objectMapper.createArrayNode();
            if (testCaseJSONObj.has("logs"))
                logsJSON = (ArrayNode) testCaseJSONObj.get("logs");
            logs = new Logs(logsJSON);

            String gasString = "0";
            if (testCaseJSONObj.has("gas"))
                gasString = testCaseJSONObj.get("gas").asText();
            this.gas = BigIntegers.asUnsignedByteArray(toBigInt(gasString));

            String outString = null;
            if (testCaseJSONObj.has("out"))
                outString = testCaseJSONObj.get("out").asText();
            if (outString != null && outString.length() > 2)
                this.out = Hex.decode(outString.substring(2));
            else
                this.out = ByteUtil.EMPTY_BYTE_ARRAY;

            for (Iterator<String> it = preJSON.fieldNames(); it.hasNext(); ) {
                String key = it.next();

                RskAddress addr = new RskAddress(key);
                AccountState accountState =
                        new AccountState(addr, preJSON.get(key));

                pre.put(addr, accountState);
            }

            for (Iterator<String> it = postJSON.fieldNames(); it.hasNext(); ) {
                String key = it.next();

                RskAddress addr = new RskAddress(key);
                AccountState accountState =
                        new AccountState(addr, postJSON.get(key));

                post.put(addr, accountState);
            }

            for (JsonNode callCreate : callCreates) {
                CallCreate cc = new CallCreate(callCreate);
                this.callCreateList.add(cc);
            }

            if (testCaseJSONObj.has("env"))
              this.env = new Env(envJSON);

            if (testCaseJSONObj.has("exec"))
              this.exec = new Exec(execJSON);

        } catch (Throwable e) {
            e.printStackTrace();
            throw new IOException(e.getMessage());
        }
    }

    static BigInteger toBigInt(String s) {
        if (s.startsWith("0x")) {
            if (s.equals("0x")) return new BigInteger("0");
            return new BigInteger(s.substring(2), 16);
        } else {
            return new BigInteger(s);
        }
    }

    public Env getEnv() {
        return env;
    }

    public Exec getExec() {
        return exec;
    }

    public Logs getLogs() {
        return logs;
    }

    public byte[] getGas() {
        return gas;
    }

    public byte[] getOut() {
        return out;
    }

    public Map<RskAddress, AccountState> getPre() {
        return pre;
    }

    public Map<RskAddress, AccountState> getPost() {
        return post;
    }

    public List<CallCreate> getCallCreateList() {
        return callCreateList;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "TestCase{" +
                "" + env +
                ", " + exec +
                ", gas=" + ByteUtil.toHexString(gas) +
                ", out=" + ByteUtil.toHexString(out) +
                ", pre=" + pre +
                ", post=" + post +
                ", callcreates=" + callCreateList +
                '}';
    }
}
