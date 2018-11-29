package co.rsk.metrics.block.builder;

import co.rsk.core.Coin;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Transaction;

import java.math.BigInteger;
import java.util.List;

public class BlockInfo {

    private List<Transaction> transactions;
    private Coin paidFees;
    private BigInteger blockDifficulty;
    private long blockNumber;
    private BigInteger blockGasLimit;
    private List<BlockHeader> uncles;
    private String coinbase;

    public BlockInfo(List<Transaction> trxs, Coin paidFees, BigInteger blockDifficulty, long blockNumber, BigInteger blockGasLimit, List<BlockHeader> uncles, String coinbase){
        this.transactions = trxs;
        this.paidFees = paidFees;
        this.blockDifficulty = blockDifficulty;
        this.blockNumber = blockNumber;
        this.blockGasLimit = blockGasLimit;
        this.uncles = uncles;
        this.coinbase = coinbase;
    }

    private BlockInfo(){

    }

    public static final BlockInfo fromBlock(Block block){
        BlockInfo blockMock = new BlockInfo();
        blockMock.transactions = block.getTransactionsList();
        blockMock.paidFees = block.getFeesPaidToMiner();
        blockMock.blockDifficulty = block.getDifficulty().asBigInteger();
        blockMock.blockNumber = block.getNumber();
        blockMock.blockGasLimit = block.getGasLimitAsInteger();
        blockMock.uncles = block.getUncleList();
        blockMock.coinbase = block.getCoinbase().toString();

        return blockMock;
    }

    public List<Transaction> getTransactions() {
        return transactions;
    }

    public Coin getPaidFees() {
        return paidFees;
    }

    public BigInteger getBlockDifficulty() {
        return blockDifficulty;
    }

    public long getBlockNumber() {
        return blockNumber;
    }

    public BigInteger getBlockGasLimit() {
        return blockGasLimit;
    }

    public List<BlockHeader> getUncles() {
        return uncles;
    }

    public String getCoinbase() { return this.coinbase;}
}
