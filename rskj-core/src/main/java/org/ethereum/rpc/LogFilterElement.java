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

package org.ethereum.rpc;

import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.vm.LogInfo;

import java.util.Arrays;

import static org.ethereum.rpc.TypeConverter.toJsonHex;

/**
 * Created by ajlopez on 5/4/2016.
 */
public class LogFilterElement {
    public String logIndex;
    public String blockNumber;
    public String blockHash;
    public String transactionHash;
    public String transactionIndex;
    public String address;
    public String data;
    public String[] topics;

    public LogFilterElement(LogInfo logInfo, Block b, int txIndex, Transaction tx, int logIdx) {
        logIndex = toJsonHex(logIdx);
        blockNumber = b == null ? null : toJsonHex(b.getNumber());
        blockHash = b == null ? null : toJsonHex(b.getHash().getBytes());
        transactionIndex = b == null ? null : toJsonHex(txIndex);
        transactionHash = tx.getHash().toJsonString();
        address = toJsonHex(logInfo.getAddress());
        data = toJsonHex(logInfo.getData());
        topics = new String[logInfo.getTopics().size()];
        for (int i = 0; i < topics.length; i++) {
            topics[i] = toJsonHex(logInfo.getTopics().get(i).getData());
        }
    }

    @Override
    public String toString() {
        return "LogFilterElement{" +
                "logIndex='" + logIndex + '\'' +
                ", blockNumber='" + blockNumber + '\'' +
                ", blockHash='" + blockHash + '\'' +
                ", transactionHash='" + transactionHash + '\'' +
                ", transactionIndex='" + transactionIndex + '\'' +
                ", address='" + address + '\'' +
                ", data='" + data + '\'' +
                ", topics=" + Arrays.toString(topics) +
                '}';
    }
}
