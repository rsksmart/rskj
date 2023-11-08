package org.ethereum.vm;

import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.vm.program.invoke.ProgramInvoke;

import java.util.List;

public class PrecompiledContractArgs {
    private final Transaction transaction;
    private final Block executionBlock;
    private final Repository repository;
    private final BlockStore blockStore;
    private final ReceiptStore receiptStore;
    private final List<LogInfo> logs;
    private final ProgramInvoke programInvoke;

    public PrecompiledContractArgs(Transaction transaction, Block executionBlock, Repository repository, BlockStore blockStore, ReceiptStore receiptStore, List<LogInfo> logs, ProgramInvoke programInvoke) {
        this.transaction = transaction;
        this.executionBlock = executionBlock;
        this.repository = repository;
        this.blockStore = blockStore;
        this.receiptStore = receiptStore;
        this.logs = logs;
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
