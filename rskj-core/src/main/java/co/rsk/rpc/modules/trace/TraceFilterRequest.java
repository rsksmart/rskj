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
import java.util.stream.Collectors;

public class TraceFilterRequest {
    private String fromBlock = "earliest";
    private String toBlock = "latest";
    private List<String> fromAddress;
    private List<String> toAddress;
    private Integer after;
    private Integer count;

    public void setFromBlock(String fromBlock) {
        this.fromBlock = fromBlock;
    }

    public void setToBlock(String toBlock) {
        this.toBlock = toBlock;
    }

    public void setFromAddress(List<String> fromAddress) {
        this.fromAddress = fromAddress;
    }

    public void setToAddress(List<String> toAddress) {
        this.toAddress = toAddress;
    }

    public void setAfter(Integer after) {
        this.after = after;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public BigInteger getFromBlockNumber() {
        if ("earliest".equalsIgnoreCase(this.fromBlock)) {
            return new BigInteger("0");
        }

        return HexUtils.stringHexToBigInteger(this.fromBlock);
    }

    public String getFromBlock() {
        return fromBlock;
    }

    public BigInteger getToBlockNumber() {
        if ("latest".equalsIgnoreCase(this.toBlock)) {
            return null;
        }

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
}
