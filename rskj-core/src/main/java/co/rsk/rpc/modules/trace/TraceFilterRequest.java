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

package co.rsk.rpc.modules.trace;

import co.rsk.util.HexUtils;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TraceFilterRequest {
    public static final String FROM_BLOCK_KEY = "fromBlock";
    public static final String TO_BLOCK_KEY = "toBlock";
    public static final String FROM_ADDRESS_KEY = "fromAddress";
    public static final String TO_ADDRESS_KEY = "toAddress";
    public static final String AFTER_KEY = "after";
    public static final String COUNT_KEY = "count";
    public static final Integer COUNT_LIMIT = 1000;

    private String fromBlock;
    private String toBlock;
    private List<String> fromAddress;
    private List<String> toAddress;
    private Integer after;
    private Integer count;

    public TraceFilterRequest(String fromBlock, String toBlock, List<String> fromAddress, List<String> toAddress, Integer after, Integer count) {
        this.fromBlock = fromBlock;
        this.toBlock = toBlock;
        this.fromAddress = fromAddress;
        this.toAddress = toAddress;
        this.after = after;
        this.count = count;
    }

    public static TraceFilterRequest buildFrom(Map<String, Object> map) {
        String fromBlock = null;
        String toBlock = null;
        List<String> fromAddress = null;
        List<String> toAddress = null;
        Integer after = null;
        Integer count = null;

        if (map.containsKey(FROM_BLOCK_KEY)) {
            fromBlock = map.get(FROM_BLOCK_KEY).toString();
        }

        if (map.containsKey(TO_BLOCK_KEY)) {
            toBlock = map.get(TO_BLOCK_KEY).toString();
        }

        if (map.containsKey(FROM_ADDRESS_KEY)) {
            fromAddress = (List<String>) map.get(FROM_ADDRESS_KEY);
        }

        if (map.containsKey(TO_ADDRESS_KEY)) {
            toAddress = (List<String>) map.get(TO_ADDRESS_KEY);
        }

        if (map.containsKey(AFTER_KEY)) {
            after = (Integer) map.get(AFTER_KEY);
        }

        if (map.containsKey(COUNT_KEY)) {
            count = (Integer) map.get(COUNT_KEY);
        }

        return new TraceFilterRequest(fromBlock, toBlock, fromAddress, toAddress, after, count);
    }


    public BigInteger getFromBlockNumber() {
        return HexUtils.stringHexToBigInteger(this.fromBlock);
    }

    public String getFromBlock() {
        return fromBlock;
    }

    public BigInteger getToBlockNumber() {
        return HexUtils.stringHexToBigInteger(this.toBlock);
    }

    public String getToBlock() {
        return toBlock;
    }

    public List<BigInteger> getFromAddressAsBigIntegers() {
        if (this.fromAddress == null) {
            return new ArrayList<>();
        }

        return this.fromAddress.stream().map(HexUtils::stringHexToBigInteger).collect(Collectors.toList());
    }

    public List<String> getFromAddress() {
        return this.fromAddress;
    }

    public List<String> getToAddress() {
        return toAddress;
    }

    public List<BigInteger> getToAddressAsBigIntegers() {
        if (this.toAddress == null) {
            return new ArrayList<>();
        }

        return this.toAddress.stream().map(HexUtils::stringHexToBigInteger).collect(Collectors.toList());
    }

    public Integer getAfter() {
        return after;
    }

    public Integer getCount() {
        return count;
    }

    public Boolean isValid() {
        return fromBlock != null && toBlock != null && count != null && count <= COUNT_LIMIT;
    }
}
