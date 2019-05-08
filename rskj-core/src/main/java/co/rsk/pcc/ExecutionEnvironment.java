/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package co.rsk.pcc;

import org.ethereum.config.BlockchainConfig;
import org.ethereum.config.BlockchainNetConfig;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.vm.LogInfo;

import java.util.List;

/**
 * Represents the execution environment of a particular native contract's method execution.
 * Things as block-dependent configuration, execution block and repository are kept here.
 *
 * @author Ariel Mendelzon
 */
public class ExecutionEnvironment {
    private Transaction transaction;
    private Block block;
    private Repository repository;
    private BlockStore blockStore;
    private ReceiptStore receiptStore;
    private List<LogInfo> logs;
    private BlockchainNetConfig config;
    private BlockchainConfig blockConfig;

    public ExecutionEnvironment(
            BlockchainNetConfig config,
            Transaction transaction,
            Block block,
            Repository repository,
            BlockStore blockStore,
            ReceiptStore receiptStore,
            List<LogInfo> logs) {
        this.config = config;
        this.blockConfig = config.getConfigForBlock(block.getNumber());
        this.transaction = transaction;
        this.block = block;
        this.repository = repository;
        this.blockStore = blockStore;
        this.receiptStore = receiptStore;
        this.logs = logs;
    }

    public BlockchainNetConfig getConfig() {
        return config;
    }

    public BlockchainConfig getBlockConfig() {
        return blockConfig;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public Block getBlock() {
        return block;
    }

    public Repository getRepository() {
        return repository;
    }

    public BlockStore getBlockStore() {
        return blockStore;
    }

    public ReceiptStore getReceiptStore() {
        return receiptStore;
    }

    public List<LogInfo> getLogs() {
        return logs;
    }

    public boolean isLocalCall() {
        return transaction.isLocalCallTransaction();
    }
}
