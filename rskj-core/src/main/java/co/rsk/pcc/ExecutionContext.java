package co.rsk.pcc;

import org.ethereum.config.BlockchainConfig;
import org.ethereum.config.BlockchainNetConfig;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.vm.LogInfo;

import java.util.List;

public class ExecutionContext {
    private Transaction transaction;
    private Block block;
    private Repository repository;
    private List<LogInfo> logs;
    private BlockchainNetConfig config;
    private BlockchainConfig blockConfig;

    public ExecutionContext(BlockchainNetConfig config, Transaction transaction, Block block, Repository repository, List<LogInfo> logs) {
        this.config = config;
        this.blockConfig = config.getConfigForBlock(block.getNumber());
        this.transaction = transaction;
        this.block = block;
        this.repository = repository;
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

    public List<LogInfo> getLogs() {
        return logs;
    }

    public boolean isLocalCall() {
        return transaction.isLocalCallTransaction();
    }
}
