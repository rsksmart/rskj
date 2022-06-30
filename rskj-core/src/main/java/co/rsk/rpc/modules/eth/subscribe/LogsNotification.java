/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.rpc.modules.eth.subscribe;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.vm.LogInfo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import co.rsk.util.HexUtils;

/**
 * The logs DTO for JSON serialization purposes.
 */
public class LogsNotification implements EthSubscriptionNotificationDTO {

    private final LogInfo logInfo;
    private final Block block;
    private final Transaction transaction;
    private final int logInfoIndex;
    private final int transactionIndex;
    private final boolean removed;

    private String lazyLogIndex;
    private String lazyBlockNumber;
    private String lazyBlockHash;
    private String lazyTransactionHash;
    private String lazyTransactionIndex;
    private String lazyAddress;
    private String lazyData;
    private List<String> lazyTopics;

    public LogsNotification(LogInfo logInfo, Block b, int txIndex, Transaction tx, int logIdx, boolean r) {
        this.logInfo = logInfo;
        this.block = b;
        this.transaction = tx;
        this.logInfoIndex = logIdx;
        this.removed = r;
        this.transactionIndex = txIndex;
    }

    public String getLogIndex() {
        if (lazyLogIndex == null) {
            lazyLogIndex = HexUtils.toQuantityJsonHex(logInfoIndex);
        }
        return lazyLogIndex;
    }

    public String getBlockNumber() {
        if (lazyBlockNumber == null) {
            lazyBlockNumber = HexUtils.toQuantityJsonHex(block.getNumber());
        }
        return lazyBlockNumber;
    }

    public String getBlockHash() {
        if (lazyBlockHash == null) {
            lazyBlockHash = block.getHashJsonString();
        }
        return lazyBlockHash;
    }

    public String getTransactionHash() {
        if (lazyTransactionHash == null) {
            lazyTransactionHash = transaction.getHash().toJsonString();
        }
        return lazyTransactionHash;
    }

    public String getTransactionIndex() {
        if (lazyTransactionIndex == null) {
            lazyTransactionIndex = HexUtils.toQuantityJsonHex(transactionIndex);
        }
        return lazyTransactionIndex;
    }

    public String getAddress() {
        if (lazyAddress == null) {
            lazyAddress = HexUtils.toJsonHex(logInfo.getAddress());
        }
        return lazyAddress;
    }

    public String getData() {
        if (lazyData == null) {
            lazyData = HexUtils.toJsonHex(logInfo.getData());
        }
        return lazyData;
    }

    public List<String> getTopics() {
        if (lazyTopics == null) {
            lazyTopics = logInfo.getTopics().stream()
                    .map(t -> HexUtils.toJsonHex(t.getData()))
                    .collect(Collectors.toList());
        }
        return Collections.unmodifiableList(lazyTopics);
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public boolean getRemoved() {
        return removed;
    }

    @JsonIgnore
    public LogInfo getLogInfo() {
        return logInfo;
    }
}
