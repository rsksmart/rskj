package org.ethereum.core;

import co.rsk.config.VmConfig;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvokeFactory;

import java.util.Set;
import java.util.concurrent.ExecutorService;

public class TransactionConcurrentExecutor extends TransactionExecutor {
    public TransactionConcurrentExecutor(
            Constants constants, ActivationConfig activationConfig, Transaction tx, int txindex,
            Repository track, BlockStore blockStore, ReceiptStore receiptStore, BlockFactory blockFactory,
            ProgramInvokeFactory programInvokeFactory, Block executionBlock, long gasUsedInTheBlock, VmConfig vmConfig,
            boolean playVm, boolean remascEnabled, PrecompiledContracts precompiledContracts, Set<DataWord> deletedAccounts, ExecutorService vmExecution) {
        super(constants, activationConfig, tx, txindex, null, track, blockStore, receiptStore, blockFactory, programInvokeFactory,
                executionBlock, gasUsedInTheBlock, vmConfig, playVm, remascEnabled, precompiledContracts, deletedAccounts, vmExecution);
    }

    @Override
    protected void finalization() {
        // RSKIP144
        // Transfer fees to coinbase or Remasc contract creates a conflicts between all transactions,
        // that prevents us to execute them in parallel.
        // Then don't transfer fees here but it will be done by BlockExecutor after all transactions are processed.
        finalization(false);
    }
}
