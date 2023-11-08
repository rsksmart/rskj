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
        return new PrecompiledContractArgs(this.transaction, this.executionBlock, this.repository, this.blockStore, this.receiptStore, this.logs, this.programInvoke);
    }
}
