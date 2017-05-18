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

import org.ethereum.core.BlockHeader;
import org.ethereum.jsontestsuite.model.BlockHeaderTck;

import static org.ethereum.json.Utils.parseData;
import static org.ethereum.json.Utils.parseNumericData;

public class BlockHeaderBuilder {


    public static long getPositiveLong(String a) {
        if (a.startsWith("0x"))
            a =a.substring(2);

        //new BigInteger(parseData("00"+a)).longValue();  // ugly
        return Long.parseLong(a,16);
    }

    public static BlockHeader  build(BlockHeaderTck headerTck){

        BlockHeader header = new BlockHeader(
                parseData(headerTck.getParentHash()),
                parseData(headerTck.getUncleHash()),
                parseData(headerTck.getCoinbase()),
                parseData(headerTck.getBloom()),
                parseNumericData(headerTck.getDifficulty()),
                getPositiveLong(headerTck.getNumber()),
                parseData(headerTck.getGasLimit()),
                getPositiveLong(headerTck.getGasUsed()),
                getPositiveLong(headerTck.getTimestamp()),
                parseData(headerTck.getExtraData()),
                null,
                0
        );

        header.setReceiptsRoot(parseData(headerTck.getReceiptTrie()));
        header.setTransactionsRoot(parseData(headerTck.getTransactionsTrie()));
        header.setStateRoot(parseData(headerTck.getStateRoot()));

        return header;
    }

}
