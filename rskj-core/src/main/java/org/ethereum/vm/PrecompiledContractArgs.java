package org.ethereum.vm;

import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.vm.program.invoke.ProgramInvoke;

import java.util.List;

public class PrecompiledContractArgs {
    private Transaction transaction;
    private Block executionBlock;
    private Repository repository;
    private BlockStore blockStore;
    private ReceiptStore receiptStore;
    private List<LogInfo> logs;
    private ProgramInvoke programInvoke;

    public Transaction getTransaction() {
        return transaction;
    }

    public void setTransaction(Transaction transaction) {
        this.transaction = transaction;
    }

    public Block getExecutionBlock() {
        return executionBlock;
    }

    public void setExecutionBlock(Block executionBlock) {
        this.executionBlock = executionBlock;
    }

    public Repository getRepository() {
        return repository;
    }

    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    public BlockStore getBlockStore() {
        return blockStore;
    }

    public void setBlockStore(BlockStore blockStore) {
        this.blockStore = blockStore;
    }

    public ReceiptStore getReceiptStore() {
        return receiptStore;
    }

    public void setReceiptStore(ReceiptStore receiptStore) {
        this.receiptStore = receiptStore;
    }

    public List<LogInfo> getLogs() {
        return logs;
    }

    public void setLogs(List<LogInfo> logs) {
        this.logs = logs;
    }

    public ProgramInvoke getProgramInvoke() {
        return programInvoke;
    }

    public void setProgramInvoke(ProgramInvoke programInvoke) {
        this.programInvoke = programInvoke;
    }
}
