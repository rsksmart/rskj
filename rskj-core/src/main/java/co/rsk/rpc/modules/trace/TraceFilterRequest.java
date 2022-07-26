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

import co.rsk.core.RskAddress;
import co.rsk.util.HexUtils;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class TraceFilterRequest {
    private static final String EARLIEST_BLOCK = "earliest";
    private static final String LATEST_BLOCK = "latest";
    private static final String PENDING_BLOCK = "pending";
    private String fromBlock = EARLIEST_BLOCK;
    private String toBlock = LATEST_BLOCK;
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
        String hexBlockNum = this.getBlockHexNumberByTag(this.fromBlock);

        if (hexBlockNum == null) {
            return null;
        }

        return HexUtils.stringHexToBigInteger(hexBlockNum);
    }

    public String getFromBlock() {
        return fromBlock;
    }

    public BigInteger getToBlockNumber() {
        String hexBlockNum = this.getBlockHexNumberByTag(this.toBlock);

        if (hexBlockNum == null) {
            return null;
        }

        return HexUtils.stringHexToBigInteger(hexBlockNum);
    }

    public String getToBlock() {
        return toBlock;
    }

    public List<RskAddress> getFromAddressAsRskAddresses() {
        if (this.fromAddress == null) {
            return Collections.emptyList();
        }

        return this.fromAddress.stream().map(RskAddress::new).collect(Collectors.toList());
    }

    public List<String> getFromAddress() {
        return this.fromAddress == null ? Collections.emptyList() : this.fromAddress;
    }

    public List<String> getToAddress() {
        return this.toAddress == null ? Collections.emptyList() : this.toAddress;
    }

    public List<RskAddress> getToAddressAsRskAddresses() {
        if (this.toAddress == null) {
            return Collections.emptyList();
        }

        return this.toAddress.stream().map(RskAddress::new).collect(Collectors.toList());
    }

    public Integer getAfter() {
        return after;
    }

    public Integer getCount() {
        return count;
    }

    private String getBlockHexNumberByTag(String block) {
        if (EARLIEST_BLOCK.equalsIgnoreCase(block)) {
            return "0x0";
        }

        if (LATEST_BLOCK.equalsIgnoreCase(block) || PENDING_BLOCK.equalsIgnoreCase(block)) {
            return null;
        }

        return block;
    }
}
