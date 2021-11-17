package co.rsk.core.bc;

import co.rsk.core.Coin;
import co.rsk.crypto.Keccak256;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.program.ProgramResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TransactionExecutionResult {
    private Transaction executedTransaction;
    private Set<DataWord> deletedAccounts;
    private long totalGasUsed;
    private Coin totalPaidFees;
    private TransactionReceipt receipt;
    private Keccak256 txHash;
    private ProgramResult result;

    public TransactionExecutionResult(Transaction executedTransaction,
                                           Set<DataWord> deletedAccounts,
                                           long totalGasUsed,
                                           Coin totalPaidFees,
                                           TransactionReceipt receipt,
                                           ProgramResult result) {
        this.executedTransaction = executedTransaction;
        this.deletedAccounts = deletedAccounts;
        this.totalGasUsed = totalGasUsed;
        this.totalPaidFees = totalPaidFees;
        this.receipt = receipt;
        this.txHash = executedTransaction.getHash();
        this.result = result;
    }

    public Transaction getExecutedTransaction(){
        return this.executedTransaction;
    }

    public Set<DataWord> getDeletedAccounts(){
        return this.deletedAccounts;
    }

    public Coin getTotalPaidFees() {
        return this.totalPaidFees;
    }

    public long getTotalGasUsed(){
        return this.totalGasUsed;
    }

    public ProgramResult getResult() {
        return result;
    }

    public Keccak256 getTxHash() {
        return txHash;
    }

    public TransactionReceipt getReceipt() {
        return receipt;
    }
}
