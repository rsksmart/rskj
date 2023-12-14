/*
 * This file is part of RskJ
 * Copyright (C) 2023 RSK Labs Ltd.
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
package org.ethereum.vm;

import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.vm.program.invoke.ProgramInvoke;

import javax.annotation.Nullable;
import java.util.List;

public class PrecompiledContractArgs {
    private Transaction transaction;
    private Block executionBlock;
    private Repository repository;
    private BlockStore blockStore;
    @Nullable
    private ReceiptStore receiptStore;
    private List<LogInfo> logs;
    /**
     * programInvoke may be set to null in some cases, like in the first loop
     * of recursive contract calls. In those cases the contract using this variable should take
     * that use case into account, for example the Environment contract's GetCallStackDepth method
     * returns a value of 1 if the programInvoke is null.
     */
    @Nullable
    private ProgramInvoke programInvoke;

    public PrecompiledContractArgs() {
    }

    void setTransaction(Transaction transaction) {
        this.transaction = transaction;
    }

    void setExecutionBlock(Block executionBlock) {
        this.executionBlock = executionBlock;
    }

    void setRepository(Repository repository) {
        this.repository = repository;
    }

    void setBlockStore(BlockStore blockStore) {
        this.blockStore = blockStore;
    }

    void setReceiptStore(ReceiptStore receiptStore) {
        this.receiptStore = receiptStore;
    }

    void setLogs(List<LogInfo> logs) {
        this.logs = logs;
    }

    void setProgramInvoke(ProgramInvoke programInvoke) {
        this.programInvoke = programInvoke;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public Block getExecutionBlock() {
        return executionBlock;
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

    public ProgramInvoke getProgramInvoke() {
        return programInvoke;
    }
}
