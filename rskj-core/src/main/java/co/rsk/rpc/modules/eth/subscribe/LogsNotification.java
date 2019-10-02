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

import com.fasterxml.jackson.annotation.JsonInclude;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.vm.LogInfo;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.ethereum.rpc.TypeConverter.toJsonHex;
import static org.ethereum.rpc.TypeConverter.toQuantityJsonHex;

/**
 * The logs DTO for JSON serialization purposes.
 */
public class LogsNotification implements EthSubscriptionNotificationDTO {
    private final String logIndex;
    private final String blockNumber;
    private final String blockHash;
    private final String transactionHash;
    private final String transactionIndex;
    private final String address;
    private final String data;
    private final List<String> topics;
    private final boolean removed;

    public LogsNotification(LogInfo logInfo, Block b, int txIndex, Transaction tx, int logIdx, boolean r) {
        logIndex = toQuantityJsonHex(logIdx);
        blockNumber = toQuantityJsonHex(b.getNumber());
        blockHash = b.getHashJsonString();
        removed = r;
        transactionIndex = toQuantityJsonHex(txIndex);
        transactionHash = tx.getHash().toJsonString();
        address = toJsonHex(logInfo.getAddress());
        data = toJsonHex(logInfo.getData());
        topics = logInfo.getTopics().stream()
                .map(t -> toJsonHex(t.getData()))
                .collect(Collectors.toList());
    }

    public String getLogIndex() {
        return logIndex;
    }

    public String getBlockNumber() {
        return blockNumber;
    }

    public String getBlockHash() {
        return blockHash;
    }

    public String getTransactionHash() {
        return transactionHash;
    }

    public String getTransactionIndex() {
        return transactionIndex;
    }

    public String getAddress() {
        return address;
    }

    public String getData() {
        return data;
    }

    public List<String> getTopics() {
        return Collections.unmodifiableList(topics);
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public boolean getRemoved() {
        return removed;
    }
}
