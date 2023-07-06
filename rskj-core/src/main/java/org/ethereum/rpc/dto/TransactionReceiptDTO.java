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
package org.ethereum.rpc.dto;

import org.ethereum.core.BasicBlock;
import org.ethereum.core.Block;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.db.TransactionInfo;
import org.ethereum.rpc.LogFilterElement;
import org.ethereum.vm.LogInfo;

import co.rsk.core.RskAddress;
import co.rsk.util.HexUtils;

/**
 * Created by Ruben on 5/1/2016.
 */
public class TransactionReceiptDTO {
    private static final String TRANSACTION_TYPE = "0x0";

    private String transactionHash;      // hash of the transaction.
    private String transactionIndex;     // integer of the transactions index position in the block.
    private String blockHash;            // hash of the block where this transaction was in.
    private String blockNumber;          // block number where this transaction was in.
    private String cumulativeGasUsed;    // The total amount of gas used when this transaction was executed in the block.
    private String gasUsed;              // The amount of gas used by this specific transaction alone.
    private String contractAddress;      // The contract address created, if the transaction was a contract creation, otherwise  null .
    private LogFilterElement[] logs;     // Array of log objects, which this transaction generated.
    private String from;                 // address of the sender.
    private String to;                   // address of the receiver. null when it's a contract creation transaction.
    private String status;               // either 1 (success) or 0 (failure)
    private String logsBloom;            // Bloom filter for light clients to quickly retrieve related logs.
    private String type = TRANSACTION_TYPE;     // is a positive unsigned 8-bit number that represents the type of the transaction.

    public TransactionReceiptDTO(Block block, TransactionInfo txInfo, SignatureCache signatureCache) {
        TransactionReceipt receipt = txInfo.getReceipt();

        status = HexUtils.toQuantityJsonHex(txInfo.getReceipt().getStatus());
        blockHash = HexUtils.toUnformattedJsonHex(txInfo.getBlockHash());
        blockNumber = HexUtils.toQuantityJsonHex(block.getNumber());

        RskAddress contractAddress = receipt.getTransaction().getContractAddress();
        if (contractAddress != null) {
            this.contractAddress = contractAddress.toJsonString();
        }

        cumulativeGasUsed = HexUtils.toQuantityJsonHex(receipt.getCumulativeGas());
        from = receipt.getTransaction().getSender(signatureCache).toJsonString();
        gasUsed = HexUtils.toQuantityJsonHex(receipt.getGasUsed());

        logs = new LogFilterElement[receipt.getLogInfoList().size()];
        for (int i = 0; i < logs.length; i++) {
            LogInfo logInfo = receipt.getLogInfoList().get(i);
            BasicBlock basicBlock = BasicBlock.createFromBlock(block);
            logs[i] = new LogFilterElement(logInfo, basicBlock, txInfo.getIndex(),
                    txInfo.getReceipt().getTransaction(), i);
        }

        to = receipt.getTransaction().getReceiveAddress().toJsonString();
        transactionHash = receipt.getTransaction().getHash().toJsonString();
        transactionIndex = HexUtils.toQuantityJsonHex(txInfo.getIndex());
        logsBloom = HexUtils.toUnformattedJsonHex(txInfo.getReceipt().getBloomFilter().getData());
    }

    public String getTransactionHash() {
        return transactionHash;
    }

    public String getTransactionIndex() {
        return transactionIndex;
    }

    public String getBlockHash() {
        return blockHash;
    }

    public String getBlockNumber() {
        return blockNumber;
    }

    public String getCumulativeGasUsed() {
        return cumulativeGasUsed;
    }

    public String getGasUsed() {
        return gasUsed;
    }

    public String getContractAddress() {
        return contractAddress;
    }

    public LogFilterElement[] getLogs() {
        return logs.clone();
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    public String getStatus() {
        return status;
    }

    public String getLogsBloom() {
        return logsBloom;
    }

    public String getType() {
        return type;
    }
}