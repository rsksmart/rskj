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
import org.bouncycastle.util.BigIntegers;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.util.ByteUtil;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Roman Mandeleil
 * @since 28.06.2014
 */
public class TestCase {

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

    public TestCase(String name, JSONObject testCaseJSONObj) throws ParseException {

        this(testCaseJSONObj);
        this.name = name;
    }

    public TestCase(JSONObject testCaseJSONObj) throws ParseException {

        try {

            JSONObject envJSON = (JSONObject) testCaseJSONObj.get("env");
            JSONObject execJSON = (JSONObject) testCaseJSONObj.get("exec");
            JSONObject preJSON = (JSONObject) testCaseJSONObj.get("pre");
            JSONObject postJSON = new JSONObject();
            if (testCaseJSONObj.containsKey("post")) // in cases where there is no post dictionary (when testing for
                // exceptions for example)
                postJSON = (JSONObject) testCaseJSONObj.get("post");
            JSONArray callCreates = new JSONArray();
            if (testCaseJSONObj.containsKey("callcreates"))
                callCreates = (JSONArray) testCaseJSONObj.get("callcreates");

            JSONArray logsJSON = new JSONArray();
            if (testCaseJSONObj.containsKey("logs"))
                logsJSON = (JSONArray) testCaseJSONObj.get("logs");
            logs = new Logs(logsJSON);

            String gasString = "0";
            if (testCaseJSONObj.containsKey("gas"))
                gasString = testCaseJSONObj.get("gas").toString();
            this.gas = BigIntegers.asUnsignedByteArray(toBigInt(gasString));

            String outString = null;
            if (testCaseJSONObj.containsKey("out"))
                outString = testCaseJSONObj.get("out").toString();
            if (outString != null && outString.length() > 2)
                this.out = Hex.decode(outString.substring(2));
            else
                this.out = ByteUtil.EMPTY_BYTE_ARRAY;

            for (Object key : preJSON.keySet()) {

                RskAddress addr = new RskAddress(key.toString());
                AccountState accountState =
                        new AccountState(addr, (JSONObject) preJSON.get(key));

                pre.put(addr, accountState);
            }

            for (Object key : postJSON.keySet()) {

                RskAddress addr = new RskAddress(key.toString());
                AccountState accountState =
                        new AccountState(addr, (JSONObject) postJSON.get(key));

                post.put(addr, accountState);
            }

            for (Object callCreate : callCreates) {

                CallCreate cc = new CallCreate((JSONObject) callCreate);
                this.callCreateList.add(cc);
            }

            if (testCaseJSONObj.containsKey("env"))
              this.env = new Env(envJSON);

            if (testCaseJSONObj.containsKey("exec"))
              this.exec = new Exec(execJSON);

        } catch (Throwable e) {
            e.printStackTrace();
            throw new ParseException(0, e);
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
