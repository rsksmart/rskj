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

import java.util.List;

public class PrecompiledContractArgsBuilder {
    private Transaction transaction;
    private Block executionBlock;
    private Repository repository;
    private BlockStore blockStore;
    private ReceiptStore receiptStore;
    private List<LogInfo> logs;
    private ProgramInvoke programInvoke;

    private PrecompiledContractArgsBuilder() {}

    public static PrecompiledContractArgsBuilder builder() {
        return new PrecompiledContractArgsBuilder();
    }

    public PrecompiledContractArgsBuilder transaction(Transaction transaction) {
        this.transaction = transaction;
        return this;
    }

    public PrecompiledContractArgsBuilder executionBlock(Block executionBlock) {
        this.executionBlock = executionBlock;
        return this;
    }

    public PrecompiledContractArgsBuilder repository(Repository repository) {
        this.repository = repository;
        return this;
    }

    public PrecompiledContractArgsBuilder blockStore(BlockStore blockStore) {
        this.blockStore = blockStore;
        return this;
    }

    public PrecompiledContractArgsBuilder receiptStore(ReceiptStore receiptStore) {
        this.receiptStore = receiptStore;
        return this;
    }

    public PrecompiledContractArgsBuilder logs(List<LogInfo> logs) {
        this.logs = logs;
        return this;
    }

    public PrecompiledContractArgsBuilder programInvoke(ProgramInvoke programInvoke) {
        this.programInvoke = programInvoke;
        return this;
    }

    public PrecompiledContractArgs build() {
        PrecompiledContractArgs args = new PrecompiledContractArgs();
        args.setTransaction(this.transaction);
        args.setExecutionBlock(this.executionBlock);
        args.setRepository(this.repository);
        args.setBlockStore(this.blockStore);
        args.setReceiptStore(this.receiptStore);
        args.setLogs(this.logs);
        args.setProgramInvoke(this.programInvoke);

        return args;
    }
}
