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
package org.ethereum.jsontestsuite.builder;

import com.fasterxml.jackson.databind.JsonNode;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.jsontestsuite.Env;
import org.ethereum.jsontestsuite.model.EnvTck;

import static org.bouncycastle.util.BigIntegers.asUnsignedByteArray;
import static org.ethereum.json.Utils.*;
import static org.ethereum.jsontestsuite.TestingCase.toBigInt;

public class EnvBuilder {

    public static final String defaultMinimumGasPrice = "0x0050";

    public static Env build(EnvTck envTck) {
        byte[] coinbase = parseData(envTck.getCurrentCoinbase());
        byte[] difficulty = parseVarData(envTck.getCurrentDifficulty());
        byte[] gasLimit = parseVarData(envTck.getCurrentGasLimit());
        byte[] minimumGasPrice = parseVarData(defaultMinimumGasPrice);
        byte[] number = parseNumericData(envTck.getCurrentNumber());
        byte[] timestamp = parseNumericData(envTck.getCurrentTimestamp());
        byte[] hash = parseData(envTck.getPreviousHash());

        return new Env(coinbase, difficulty, gasLimit, minimumGasPrice, number, timestamp, hash);
    }

    /*
        e.g:
            "currentCoinbase" : "2adc25665018aa1fe0e6bc666dac8fc2697ff9ba",
            "currentDifficulty" : "256",
            "currentGasLimit" : "1000000",
            "currentMinimumGasPrice" : "777",
            "currentNumber" : "0",
            "currentTimestamp" : 1,
            "previousHash" : "5e20a0453cecd065ea59c37ac63e079ee08998b6045136a8ce6635c7912ec0b6"
    */
    public static Env build(JsonNode jsonEnv) {
        byte[] coinbase = Hex.decode(jsonEnv.get("currentCoinbase").asText());
        byte[] difficulty = asUnsignedByteArray(toBigInt(jsonEnv.get("currentDifficulty").asText()));
        byte[] gasLimit = asUnsignedByteArray(toBigInt(parseUnidentifiedBase(jsonEnv.get("currentGasLimit").asText())));
        byte[] minimumGasPrice = asUnsignedByteArray(toBigInt(parseUnidentifiedBase(defaultMinimumGasPrice)));
        byte[] number = toBigInt(jsonEnv.get("currentNumber").asText()).toByteArray();
        byte[] timestamp = toBigInt(jsonEnv.get("currentTimestamp").asText()).toByteArray();

        JsonNode previousHash = jsonEnv.get("previousHash");
        String prevHash = previousHash == null ? "" : previousHash.asText();

        byte[] hash = Hex.decode(prevHash);

        return new Env(coinbase, difficulty, gasLimit, minimumGasPrice, number, timestamp, hash);
    }

}
