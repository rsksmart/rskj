/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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
package org.ethereum.core;

import co.rsk.config.VmConfig;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.metrics.profilers.Metric;
import co.rsk.metrics.profilers.MetricKind;
import co.rsk.metrics.profilers.Profiler;
import co.rsk.metrics.profilers.ProfilerFactory;
import co.rsk.panic.PanicProcessor;
import co.rsk.rpc.modules.trace.ProgramSubtrace;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.vm.*;
import org.ethereum.vm.exception.VMException;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.ProgramResult;
import org.ethereum.vm.program.invoke.ProgramInvoke;
import org.ethereum.vm.program.invoke.ProgramInvokeFactory;
import org.ethereum.vm.program.invoke.TransferInvoke;
import org.ethereum.vm.trace.ProgramTrace;
import org.ethereum.vm.trace.ProgramTraceProcessor;
import org.ethereum.vm.trace.SummarizedProgramTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.math.BigInteger;
import java.util.*;

import static co.rsk.util.ListArrayUtil.getLength;
import static co.rsk.util.ListArrayUtil.isEmpty;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP144;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP174;
import static org.ethereum.util.BIUtil.*;
import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

/**
 * @author Roman Mandeleil
 * @since 19.12.2014
 */
public class TransactionExecutor {

    private static final Logger logger = LoggerFactory.getLogger("execute");
    private static final Profiler profiler = ProfilerFactory.getInstance();
    private static final PanicProcessor panicProcessor = new PanicProcessor();

    private final Constants constants;
    private final ActivationConfig.ForBlock activations;
    private final Transaction tx;
    private final int txindex;
    private final Repository track;
    private final Repository cacheTrack;
    private final BlockStore blockStore;
    private final ReceiptStore receiptStore;
    private final BlockFactory blockFactory;
    private final VmConfig vmConfig;
    private final PrecompiledContracts precompiledContracts;
    private final boolean enableRemasc;
    private String executionError = "";
    private final long gasConsumed;
    private Coin paidFees;

    private final ProgramInvokeFactory programInvokeFactory;
    private final RskAddress coinbase;

    private TransactionReceipt receipt;
    private ProgramResult result = new ProgramResult();
    private final Block executionBlock;

    private VM vm;
    private Program program;
    private List<ProgramSubtrace> subtraces;

    private PrecompiledContracts.PrecompiledContract precompiledContract;

    private long gasLeftover = 0;
    private long basicTxCost = 0;
    private List<LogInfo> logs = null;
    private final Set<DataWord> deletedAccounts;
    private final SignatureCache signatureCache;
    private final long sublistGasLimit;

    private boolean localCall = false;

    private final Set<RskAddress> precompiledContractsCalled = new HashSet<>();

    private final boolean postponeFeePayment;

    public TransactionExecutor(
            Constants constants, ActivationConfig activationConfig, Transaction tx, int txindex, RskAddress coinbase,
            Repository track, BlockStore blockStore, ReceiptStore receiptStore, BlockFactory blockFactory,
            ProgramInvokeFactory programInvokeFactory, Block executionBlock, long gasConsumed, VmConfig vmConfig,
            boolean remascEnabled, PrecompiledContracts precompiledContracts, Set<DataWord> deletedAccounts,
            SignatureCache signatureCache, boolean postponeFeePayment, long sublistGasLimit) {
        this.constants = constants;
        this.signatureCache = signatureCache;
        this.activations = activationConfig.forBlock(executionBlock.getNumber());
        this.tx = tx;
        this.txindex = txindex;
        this.coinbase = coinbase;
        this.track = track;
        this.cacheTrack = track.startTracking();
        this.blockStore = blockStore;
        this.receiptStore = receiptStore;
        this.blockFactory = blockFactory;
        this.programInvokeFactory = programInvokeFactory;
        this.executionBlock = executionBlock;
        this.gasConsumed = gasConsumed;
        this.vmConfig = vmConfig;
        this.precompiledContracts = precompiledContracts;
        this.enableRemasc = remascEnabled;
        this.deletedAccounts = new HashSet<>(deletedAccounts);
        this.postponeFeePayment = postponeFeePayment;
        this.sublistGasLimit = sublistGasLimit;
    }

    /**
     * Validates and executes the transaction
     *
     * @return true if the transaction is valid and executed, false if the transaction is invalid
     */
    public boolean executeTransaction() {
        if (!this.init()) {
            return false;
        }

        this.execute();
        this.go();
        this.finalization();

        return true;
    }

    /**
     * Do all the basic validation, if the executor
     * will be ready to run the transaction at the end
     * set readyToExecute = true
     */
    private boolean init() {
        basicTxCost = tx.transactionCost(constants, activations, signatureCache);

        if (localCall) {
            return true;
        }

        if (tx.isInitCodeSizeInvalidForTx(activations)) {

            String errorMessage = String.format("Initcode size for contract is invalid, it exceed the max limit size: initcode size = %d | maxAllowed = %d |  tx = %s", getLength(tx.getData()), Constants.getMaxInitCodeSize(), tx.getHash());

            logger.warn(errorMessage);

            execError(errorMessage);

            return false;
        }

        long txGasLimit = GasCost.toGas(tx.getGasLimit());
        long gasLimit = activations.isActive(RSKIP144) ? sublistGasLimit : GasCost.toGas(executionBlock.getGasLimit());

        if (!gasIsValid(txGasLimit, gasLimit)) {
            return false;
        }

        if (!nonceIsValid()) {
            return false;
        }

        Coin totalCost = tx.getValue();

        if (basicTxCost > 0 ) {
            // add gas cost only for priced transactions
            Coin txGasCost = tx.getGasPrice().multiply(BigInteger.valueOf(txGasLimit));
            totalCost = totalCost.add(txGasCost);
        }

        Coin senderBalance = track.getBalance(tx.getSender(signatureCache));

        if (!isCovers(senderBalance, totalCost)) {

            logger.warn("Not enough cash: Require: {}, Sender cash: {}, tx {}", totalCost, senderBalance, tx.getHash());
            logger.warn("Transaction Data: {}", tx);
            logger.warn("Tx Included in the following block: {}", this.executionBlock);

            execError(String.format("Not enough cash: Require: %s, Sender cash: %s", totalCost, senderBalance));

            return false;
        }

        if (!transactionAddressesAreValid()) {
            return false;
        }

        return true;
    }


    private boolean transactionAddressesAreValid() {
        // Prevent transactions with excessive address size
        byte[] receiveAddress = tx.getReceiveAddress().getBytes();
        if (receiveAddress != null && !Arrays.equals(receiveAddress, EMPTY_BYTE_ARRAY) && receiveAddress.length > Constants.getMaxAddressByteLength()) {
            logger.warn("Receiver address to long: size: {}, tx {}", receiveAddress.length, tx.getHash());
            logger.warn("Transaction Data: {}", tx);
            logger.warn("Tx Included in the following block: {}", this.executionBlock);

            return false;
        }

        if (!tx.acceptTransactionSignature(constants.getChainId())) {
            logger.warn("Transaction {} signature not accepted: {}", tx.getHash(), tx.getSignature());
            logger.warn("Transaction Data: {}", tx);
            logger.warn("Tx Included in the following block: {}", this.executionBlock);

            panicProcessor.panic("invalidsignature",
                    String.format("Transaction %s signature not accepted: %s",
                            tx.getHash(), tx.getSignature()));
            execError(String.format("Transaction signature not accepted: %s", tx.getSignature()));

            return false;
        }

        return true;
    }

    private boolean nonceIsValid() {
        BigInteger reqNonce = track.getNonce(tx.getSender(signatureCache));
        BigInteger txNonce = toBI(tx.getNonce());

        if (isNotEqual(reqNonce, txNonce)) {
            if (logger.isWarnEnabled()) {
                logger.warn("Invalid nonce: sender {}, required: {} , tx.nonce: {}, tx {}", tx.getSender(signatureCache), reqNonce, txNonce, tx.getHash());
                logger.warn("Transaction Data: {}", tx);
                logger.warn("Tx Included in the following block: {}", this.executionBlock.getShortDescr());
            }

            execError(String.format("Invalid nonce: required: %s , tx.nonce: %s", reqNonce, txNonce));
            return false;
        }

        return true;
    }

    private boolean gasIsValid(long txGasLimit, long curContainerGasLimit) {
        // if we've passed the curContainerGasLimit limit we must stop exec
        // cumulativeGas being equal to GasCost.MAX_GAS is a border condition
        // which is used on some stress tests, but its far from being practical
        // as the current gas limit on blocks is 6.8M... several orders of magnitude
        // less than the theoretical max gas on blocks.
        long cumulativeGas = GasCost.add(txGasLimit, gasConsumed);

        boolean cumulativeGasReached = cumulativeGas > curContainerGasLimit || cumulativeGas == GasCost.MAX_GAS;
        if (cumulativeGasReached) {
            execError(String.format("Too much gas used in this block or sublist(RSKIP144): available in sublist: %s tx sent: %s",
                    curContainerGasLimit - txGasLimit,
                    txGasLimit));
            return false;
        }

        if (txGasLimit < basicTxCost) {
            execError(String.format("Not enough gas for transaction execution: tx needs: %s tx sent: %s", basicTxCost, txGasLimit));
            return false;
        }

        return true;
    }

    private void execute() {
        logger.trace("Execute transaction {} {}", toBI(tx.getNonce()), tx.getHash());

        if (!localCall) {

            track.increaseNonce(tx.getSender(signatureCache));

            long txGasLimit = GasCost.toGas(tx.getGasLimit());
            Coin txGasCost = tx.getGasPrice().multiply(BigInteger.valueOf(txGasLimit));
            track.addBalance(tx.getSender(signatureCache), txGasCost.negate());

            logger.trace("Paying: txGasCost: [{}], gasPrice: [{}], gasLimit: [{}]", txGasCost, tx.getGasPrice(), txGasLimit);
        }

        if (tx.isContractCreation()) {
            create();
        } else {
            call();
        }
    }

    private boolean enoughGas(long txGasLimit, long requiredGas, long gasUsed) {
        if (!activations.isActive(ConsensusRule.RSKIP136)) {
            return txGasLimit >= requiredGas;
        }
        return txGasLimit >= gasUsed;
    }

    private void call() {
        logger.trace("Call transaction {} {}", toBI(tx.getNonce()), tx.getHash());

        RskAddress targetAddress = tx.getReceiveAddress();

        // DataWord(targetAddress)) can fail with exception:
        // java.lang.RuntimeException: Data word can't exceed 32 bytes:
        // if targetAddress size is greater than 32 bytes.
        // But init() will detect this earlier
        precompiledContract = precompiledContracts.getContractForAddress(activations, DataWord.valueOf(targetAddress.getBytes()));

        this.subtraces = new ArrayList<>();

        if (precompiledContract != null) {
            this.precompiledContractsCalled.add(targetAddress);
            Metric metric = profiler.start(MetricKind.PRECOMPILED_CONTRACT_INIT);
            PrecompiledContractArgs args = PrecompiledContractArgsBuilder.builder()
                    .transaction(tx)
                    .executionBlock(executionBlock)
                    .repository(cacheTrack)
                    .blockStore(blockStore)
                    .receiptStore(receiptStore)
                    .logs(result.getLogInfoList())
                    .build();

            precompiledContract.init(args);

            profiler.stop(metric);
            metric = profiler.start(MetricKind.PRECOMPILED_CONTRACT_EXECUTE);

            long requiredGas = precompiledContract.getGasForData(tx.getData());
            long txGasLimit = GasCost.toGas(tx.getGasLimit());
            long gasUsed = GasCost.add(requiredGas, basicTxCost);
            if (!localCall && !enoughGas(txGasLimit, requiredGas, gasUsed)) {
                // no refund no endowment
                execError(String.format( "Out of Gas calling precompiled contract at block %d " +
                                "for address 0x%s. required: %s, used: %s, left: %s ",
                        executionBlock.getNumber(), targetAddress.toString(), requiredGas, gasUsed, gasLeftover));
                gasLeftover = 0;
                profiler.stop(metric);
                return;
            }

            gasLeftover = activations.isActive(ConsensusRule.RSKIP136) ?
                    GasCost.subtract(txGasLimit, gasUsed) :
                    txGasLimit - gasUsed;

            // FIXME: save return for vm trace
            try {
                byte[] out = precompiledContract.execute(tx.getData());
                this.subtraces = precompiledContract.getSubtraces();
                result.setHReturn(out);
                if (!track.isExist(targetAddress)) {
                    track.createAccount(targetAddress);
                    track.setupContract(targetAddress);
                } else if (!track.isContract(targetAddress)) {
                    track.setupContract(targetAddress);
                }
            } catch (VMException | RuntimeException e) {
                result.setException(e);
            }
            result.spendGas(gasUsed);
            profiler.stop(metric);
        } else {
            byte[] code = track.getCode(targetAddress);
            // Code can be null
            if (isEmpty(code)) {
                gasLeftover = GasCost.subtract(GasCost.toGas(tx.getGasLimit()), basicTxCost);
                result.spendGas(basicTxCost);
            } else {
                ProgramInvoke programInvoke =  programInvokeFactory
                        .createProgramInvoke(tx, txindex, executionBlock, cacheTrack, blockStore, signatureCache);

                this.vm = new VM(vmConfig, precompiledContracts);
                this.program = new Program(vmConfig, precompiledContracts, blockFactory, activations, code, programInvoke, tx, deletedAccounts, signatureCache);
            }
        }

        if (result.getException() == null) {
            Coin endowment = tx.getValue();
            cacheTrack.transfer(tx.getSender(signatureCache), targetAddress, endowment);
        }
    }

    private void create() {
        RskAddress newContractAddress = tx.getContractAddress();
        cacheTrack.createAccount(newContractAddress, activations.isActive(RSKIP174) && cacheTrack.isExist(newContractAddress));

        if (isEmpty(tx.getData())) {
            gasLeftover = GasCost.subtract(GasCost.toGas(tx.getGasLimit()), basicTxCost);
            // If there is no data, then the account is created, but without code nor
            // storage. It doesn't even call setupContract() to setup a storage root
        } else {
            cacheTrack.setupContract(newContractAddress);
            ProgramInvoke programInvoke = programInvokeFactory.createProgramInvoke(tx, txindex, executionBlock, cacheTrack, blockStore, signatureCache);

            this.vm = new VM(vmConfig, precompiledContracts);
            this.program = new Program(vmConfig, precompiledContracts, blockFactory, activations, tx.getData(), programInvoke, tx, deletedAccounts, signatureCache);

            // reset storage if the contract with the same address already exists
            // TCK test case only - normally this is near-impossible situation in the real network
            /* Storage keys not available anymore in a fast way
            ContractDetails contractDetails = program.getStorage().getContractDetails(newContractAddress);
            for (DataWord key : contractDetails.getStorageKeys()) {
                program.storageSave(key, DataWord.ZERO);
            }
            */
        }

        Coin endowment = tx.getValue();
        cacheTrack.transfer(tx.getSender(signatureCache), newContractAddress, endowment);
    }

    private void execError(Throwable err) {
        logger.error("execError: ", err);
        executionError = err.getMessage();
    }

    private void execError(String err) {
        logger.trace(err);
        executionError = err;
    }

    private void go() {
        // TODO: transaction call for pre-compiled  contracts
        if (vm == null) {
            cacheTrack.commit();
            return;
        }

        logger.trace("Go transaction {} {}", toBI(tx.getNonce()), tx.getHash());

        //Set the deleted accounts in the block in the remote case there is a CREATE2 creating a deleted account

        Metric metric = profiler.start(MetricKind.VM_EXECUTE);
        try {

            // Charge basic cost of the transaction
            program.spendGas(tx.transactionCost(constants, activations, signatureCache), "TRANSACTION COST");

            vm.play(program);

            // This line checks whether the invoked smart contract calls a Precompiled contract.
            // This flag is then taken by the Parallel transaction handler, if the tx calls a precompiled contract,
            // it should be executed sequentially.
            this.precompiledContractsCalled.addAll(program.precompiledContractsCalled());

            result = program.getResult();
            gasLeftover = GasCost.subtract(GasCost.toGas(tx.getGasLimit()), program.getResult().getGasUsed());

            if (tx.isContractCreation() && !result.isRevert()) {
                createContract();
            }

            if (result.getException() != null || result.isRevert()) {
                result.clearFieldsOnException();
                cacheTrack.rollback();

                if (result.getException() != null) {
                    throw result.getException();
                } else {
                    execError("REVERT opcode executed");
                }
            }
        } catch (Exception e) {
            cacheTrack.rollback();
            gasLeftover = 0;
            execError(e);
            result.setException(e);
            profiler.stop(metric);
            return;
        }
        cacheTrack.commit();
        profiler.stop(metric);
    }

    private void createContract() {
        int createdContractSize = getLength(program.getResult().getHReturn());
        long returnDataGasValue = GasCost.multiply(GasCost.CREATE_DATA, createdContractSize);
        if (gasLeftover < returnDataGasValue) {
            configureRuntimeExceptionOnProgram(
                    Program.ExceptionHelper.notEnoughSpendingGas(
                            program,
                            "No gas to return just created contract",
                            returnDataGasValue));
        } else if (createdContractSize > Constants.getMaxContractSize()) {
            configureRuntimeExceptionOnProgram(
                    Program.ExceptionHelper.tooLargeContractSize(
                            program,
                            Constants.getMaxContractSize(),
                            createdContractSize));
        } else {
            gasLeftover = GasCost.subtract(gasLeftover,  returnDataGasValue);
            program.spendGas(returnDataGasValue, "CONTRACT DATA COST");
            cacheTrack.saveCode(tx.getContractAddress(), result.getHReturn());
        }
    }

    private void configureRuntimeExceptionOnProgram(RuntimeException e) {
        program.setRuntimeFailure(e);
        result = program.getResult();
        result.setHReturn(EMPTY_BYTE_ARRAY);
    }

    public TransactionReceipt getReceipt() {
        if (receipt == null) {
            receipt = new TransactionReceipt();
            long totalGasUsed = GasCost.add(gasConsumed, getGasConsumed());
            receipt.setCumulativeGas(totalGasUsed);
            receipt.setTransaction(tx);
            receipt.setLogInfoList(getVMLogs());
            receipt.setGasUsed(getGasConsumed());
            receipt.setStatus(executionError.isEmpty() ? TransactionReceipt.SUCCESS_STATUS : TransactionReceipt.FAILED_STATUS);
        }
        return receipt;
    }

    private void finalization() {
        // RSK if local call gas balances must not be changed
        if (localCall) {
            // there's no need to save any change
            localCallFinalization();
            return;
        }

        logger.trace("Finalize transaction {} {}", toBI(tx.getNonce()), tx.getHash());

        cacheTrack.commit();

        //Transaction sender is stored in cache
        signatureCache.storeSender(tx);

        // Should include only LogInfo's that was added during not rejected transactions
        List<LogInfo> logsFromNonRejectedTransactions = result.logsFromNonRejectedTransactions();

        TransactionExecutionSummary.Builder summaryBuilder = TransactionExecutionSummary.builderFor(tx)
                .gasLeftover(BigInteger.valueOf(gasLeftover))
                .logs(logsFromNonRejectedTransactions)
                .result(result.getHReturn());

        long gasRefund = refundGas();

        TransactionExecutionSummary summary = buildTransactionExecutionSummary(summaryBuilder, gasRefund);

        // Refund for gas leftover
        RskAddress txSender = tx.getSender(signatureCache);
        track.addBalance(txSender, summary.getLeftover().add(summary.getRefund()));
        logger.trace("Pay total refund to sender: [{}], refund val: [{}]", txSender, summary.getRefund());

        // Transfer fees to miner
        Coin summaryFee = summary.getFee();

        //TODO: REMOVE THIS WHEN THE LocalBLockTests starts working with REMASC
        if (!postponeFeePayment) {
            if (enableRemasc) {
                logger.trace("Adding fee to remasc contract account");
                track.addBalance(PrecompiledContracts.REMASC_ADDR, summaryFee);
            } else {
                track.addBalance(coinbase, summaryFee);
            }
        }

        this.paidFees = summaryFee;

        logger.trace("Processing result");
        logs = logsFromNonRejectedTransactions;

        result.getCodeChanges().forEach((key, value) -> track.saveCode(new RskAddress(key), value));
        // Traverse list of suicides
        result.getDeleteAccounts().forEach(address -> track.delete(new RskAddress(address)));

        track.clearTransientStorage();

        logger.trace("tx listener done");

        logger.trace("tx finalization done");
    }

    private void localCallFinalization() {
        if(result == null) {
            logger.warn("this is unexpected, a transaction executor should always have a non null result");
            return;
        }

        if(result.getException() != null) {
            logger.warn("Local call produced an execution error: {}",
                    executionError != null ? executionError : "unexpected");
            return;
        }

        logger.trace("Finalize transaction gas estimation, txHash: {}, nonce:{},", tx.getHash(), toBI(tx.getNonce()));

        // Should include only LogInfo's that was added during not rejected transactions
        List<LogInfo> logsFromNonRejectedTransactions = result.logsFromNonRejectedTransactions();

        TransactionExecutionSummary.Builder summaryBuilder = TransactionExecutionSummary.builderFor(tx)
                .gasLeftover(BigInteger.valueOf(gasLeftover))
                .logs(logsFromNonRejectedTransactions)
                .result(result.getHReturn());

        long gasRefund = refundGas();

        result.setGasUsed(getGasConsumed());

        TransactionExecutionSummary summary = buildTransactionExecutionSummary(summaryBuilder, gasRefund);

        if (logger.isTraceEnabled()) {
            logger.trace("Pay total refund to sender: [{}], refund val: [{}]", tx.getSender(signatureCache), summary.getRefund());
        }

        // Transfer fees to miner
        this.paidFees = summary.getFee();

        logger.trace("Processing result for gas estimation");

        logs = logsFromNonRejectedTransactions;

        logger.trace("tx listener for gas estimation done");

        logger.trace("tx finalization for gas estimation done");
    }

    private TransactionExecutionSummary buildTransactionExecutionSummary(TransactionExecutionSummary.Builder summaryBuilder, long gasRefund) {
        summaryBuilder
                .gasUsed(toBI(result.getGasUsed()))
                .gasRefund(toBI(gasRefund))
                .deletedAccounts(result.getDeleteAccounts())
                .internalTransactions(result.getInternalTransactions());

        if (result.getException() != null) {
            summaryBuilder.markAsFailed();
        }

        logger.trace("Building transaction execution summary");

        return summaryBuilder.build();
    }

    private long refundGas() {
        // Accumulate refunds for suicides
        result.addFutureRefund(GasCost.multiply(result.getDeleteAccounts().size(), GasCost.SUICIDE_REFUND));

        // The actual gas subtracted is equal to half of the future refund
        long gasRefund = Math.min(result.getFutureRefund(), result.getGasUsed() / 2);
        result.addDeductedRefund(gasRefund);
        result.setGasUsedBeforeRefunds(result.getGasUsed());

        gasLeftover = activations.isActive(ConsensusRule.RSKIP136) ?
                GasCost.add(gasLeftover, gasRefund) :
                gasLeftover + gasRefund;

        return gasRefund;
    }


    /**
     * This extracts the trace to an object in memory.
     * Refer to {@link org.ethereum.vm.VMUtils#saveProgramTraceFile} for a way to saving the trace to a file.
     */
    public void extractTrace(ProgramTraceProcessor programTraceProcessor) {
        if (program != null) {
            // TODO improve this settings; the trace should already have the values
            ProgramTrace trace = program.getTrace().result(result.getHReturn()).error(result.getException()).revert(result.isRevert());
            programTraceProcessor.processProgramTrace(trace, tx.getHash());
        }
        else {
            TransferInvoke invoke = new TransferInvoke(DataWord.valueOf(tx.getSender(signatureCache).getBytes()), DataWord.valueOf(tx.getReceiveAddress().getBytes()), 0L, DataWord.valueOf(tx.getValue().getBytes()), tx.getData());

            SummarizedProgramTrace trace = new SummarizedProgramTrace(invoke);

            if (this.subtraces != null) {
                for (ProgramSubtrace subtrace : this.subtraces) {
                    trace.addSubTrace(subtrace);
                }
            }

            programTraceProcessor.processProgramTrace(trace, tx.getHash());
        }
    }

    public TransactionExecutor setLocalCall(boolean localCall) {
        this.localCall = localCall;
        this.tx.setLocalCallTransaction(localCall);
        return this;
    }

    public List<LogInfo> getVMLogs() {
        return logs;
    }

    public ProgramResult getResult() {
        return result;
    }

    public long getGasConsumed() {
        if (activations.isActive(ConsensusRule.RSKIP136)) {
            return GasCost.subtract(GasCost.toGas(tx.getGasLimit()), gasLeftover);
        }
        return toBI(tx.getGasLimit()).subtract(toBI(gasLeftover)).longValue();
    }

    public Coin getPaidFees() { return paidFees; }

    @Nonnull
    public Set<RskAddress> precompiledContractsCalled() {
        return this.precompiledContractsCalled.isEmpty() ? Collections.emptySet() : new HashSet<>(this.precompiledContractsCalled);
    }
}
