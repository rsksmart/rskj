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

package org.ethereum.core.genesis;

import com.google.common.io.ByteStreams;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JavaType;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

/**
 * Created by mario on 03/01/17.
 */
public class GenesisJsonTest {

    private static final String GENESIS_JSON = "{ " +
            "\"alloc\": { " +
            "\"0000000000000000000000000000000000000006\": {" +
            "\"balance\": \"21000000000000000000000000\"" +
            "}}," +
            "\"nonce\": \"0xffffffffffffffff\"," +
            "\"mixhash\": \"0x00\"," +
            "\"bitcoinMergedMiningHeader\": \"0x00\"," +
            "\"bitcoinMergedMiningMerkleProof\": \"0x00\"," +
            "\"bitcoinMergedMiningCoinbaseTransaction\": \"0x00\"," +
            "\"timestamp\": \"0x00\"," +
            "\"parentHash\": \"0x0000000000000000000000000000000000000000000000000000000000000000\"," +
            "\"extraData\": \"0x686f727365\"," +
            "\"gasLimit\": \"0x2dc6c0\"," +
            "\"difficulty\": \"0x0000000001\"," +
            "\"coinbase\": \"0x3333333333333333333333333333333333333333\"," +
            "\"minimumGasPrice\": \"0x00\"" +
            "}";

    @Test
    public void generateGenesisJson() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JavaType type = mapper.getTypeFactory().constructType(GenesisJson.class);

        GenesisJson genesisJson = new ObjectMapper().readValue(GENESIS_JSON, type);

        Assert.assertTrue(StringUtils.isNotBlank(genesisJson.getMinimumGasPrice()));
        Assert.assertTrue(StringUtils.isNotBlank(genesisJson.getParentHash()));
        Assert.assertTrue(genesisJson.getAlloc().size() > 0);
        Assert.assertTrue(StringUtils.isNotBlank(genesisJson.getBitcoinMergedMiningCoinbaseTransaction()));
        Assert.assertTrue(StringUtils.isNotBlank(genesisJson.getBitcoinMergedMiningHeader()));
        Assert.assertTrue(StringUtils.isNotBlank(genesisJson.getBitcoinMergedMiningMerkleProof()));
        Assert.assertTrue(StringUtils.isNotBlank(genesisJson.getCoinbase()));
        Assert.assertTrue(StringUtils.isNotBlank(genesisJson.getDifficulty()));
        Assert.assertTrue(StringUtils.isNotBlank(genesisJson.getExtraData()));
        Assert.assertTrue(StringUtils.isNotBlank(genesisJson.getMixhash()));
        Assert.assertTrue(StringUtils.isNotBlank(genesisJson.getTimestamp()));

    }

}
