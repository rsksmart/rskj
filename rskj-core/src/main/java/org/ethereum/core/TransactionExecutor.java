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

import co.rsk.config.RskSystemProperties;
import co.rsk.core.commons.RskAddress;
import co.rsk.panic.PanicProcessor;
import org.ethereum.config.Constants;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ContractDetails;
import org.ethereum.db.ReceiptStore;
import org.ethereum.listener.EthereumListener;
import org.ethereum.listener.EthereumListenerAdapter;
import org.ethereum.vm.*;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.ProgramResult;
import org.ethereum.vm.program.invoke.ProgramInvoke;
import org.ethereum.vm.program.invoke.ProgramInvokeFactory;
import org.ethereum.vm.trace.ProgramTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.ArrayUtils.getLength;
import static org.apache.commons.lang3.ArrayUtils.isEmpty;
import static org.ethereum.util.BIUtil.*;
import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.ethereum.vm.VMUtils.saveProgramTraceFile;

/**
 * @author Roman Mandeleil
 * @since 19.12.2014
 */
public class TransactionExecutor {

    private static final Logger logger = LoggerFactory.getLogger("execute");
    private static final PanicProcessor panicProcessor = new PanicProcessor();

    private final RskSystemProperties config;
    private final Transaction tx;
    private final int txindex;
    private final Repository track;
    private final Repository cacheTrack;
    private final BlockStore blockStore;
    private final ReceiptStore receiptStore;
    private String executionError = "";
    private final long gasUsedInTheBlock;
    private BigInteger paidFees;
    private boolean readyToExecute = false;

    private final ProgramInvokeFactory programInvokeFactory;
    private final RskAddress coinbase;

    private TransactionReceipt receipt;
    private ProgramResult result = new ProgramResult();
    private final Block executionBlock;

    private final EthereumListener listener;

    private VM vm;
    private Program program;

    PrecompiledContracts.PrecompiledContract precompiledContract;

    BigInteger mEndGas = BigInteger.ZERO;
    long basicTxCost = 0;
    List<LogInfo> logs = null;

    boolean localCall = false;

    public TransactionExecutor(RskSystemProperties config, Transaction tx, int txindex, RskAddress coinbase, Repository track, BlockStore blockStore, ReceiptStore receiptStore,
                               ProgramInvokeFactory programInvokeFactory, Block executionBlock) {
        this(config, tx, txindex, coinbase, track, blockStore, receiptStore, programInvokeFactory, executionBlock, new EthereumListenerAdapter(), 0);
    }

    public TransactionExecutor(RskSystemProperties config, Transaction tx, int txindex, RskAddress coinbase, Repository track, BlockStore blockStore, ReceiptStore receiptStore,
                               ProgramInvokeFactory programInvokeFactory, Block executionBlock,
                               EthereumListener listener, long gasUsedInTheBlock) {
        this.config = config;
        this.tx = tx;
        this.txindex = txindex;
        this.coinbase = coinbase;
        this.track = track;
        this.cacheTrack = track.startTracking();
        this.blockStore = blockStore;
        this.receiptStore = receiptStore;
        this.programInvokeFactory = programInvokeFactory;
        this.executionBlock = executionBlock;
        this.listener = listener;
        this.gasUsedInTheBlock = gasUsedInTheBlock;
    }


    /**
     * Do all the basic validation, if the executor
     * will be ready to run the transaction at the end
     * set readyToExecute = true
     */
    public boolean init() {
        basicTxCost = tx.transactionCost(config, executionBlock);

        if (localCall) {
            readyToExecute = true;
            return readyToExecute;
        }

        BigInteger txGasLimit = new BigInteger(1, tx.getGasLimit());
        BigInteger curBlockGasLimit = new BigInteger(1, executionBlock.getGasLimit());

        boolean cumulativeGasReached = txGasLimit.add(BigInteger.valueOf(gasUsedInTheBlock)).compareTo(curBlockGasLimit) > 0;
        if (cumulativeGasReached) {
            execError(String.format("Too much gas used in this block: Require: %s Got: %s",
                    curBlockGasLimit.longValue() - toBI(tx.getGasLimit()).longValue(),
                    toBI(tx.getGasLimit()).longValue()));

            return false;
        }

        if (txGasLimit.compareTo(BigInteger.valueOf(basicTxCost)) < 0) {
            execError(String.format("Not enough gas for transaction execution: Require: %s Got: %s", basicTxCost, txGasLimit));
            return false;
        }

        BigInteger reqNonce = track.getNonce(tx.getSender());
        BigInteger txNonce = toBI(tx.getNonce());
        if (isNotEqual(reqNonce, txNonce)) {

            if (logger.isWarnEnabled()) {
                logger.warn("Invalid nonce: sender {}, required: {} , tx.nonce: {}, tx {}", tx.getSender(), reqNonce, txNonce, tx.getHash());
                logger.warn("Transaction Data: {}", tx);
                logger.warn("Tx Included in the following block: {}", this.executionBlock.getShortDescr());
            }

            execError(String.format("Invalid nonce: required: %s , tx.nonce: %s", reqNonce, txNonce));
            return false;
        }


        BigInteger totalCost = BigInteger.ZERO;
        if (basicTxCost > 0 ) {
            // Estimate transaction cost only if is not a free trx
            BigInteger txGasCost = toBI(tx.getGasPrice()).multiply(txGasLimit);
            totalCost = toBI(tx.getValue()).add(txGasCost);
        }

        BigInteger senderBalance = track.getBalance(tx.getSender());

        if (!isCovers(senderBalance, totalCost)) {

            logger.warn("Not enough cash: Require: {}, Sender cash: {}, tx {}", totalCost, senderBalance, tx.getHash());
            logger.warn("Transaction Data: {}", tx);
            logger.warn("Tx Included in the following block: {}", this.executionBlock);

            execError(String.format("Not enough cash: Require: %s, Sender cash: %s", totalCost, senderBalance));

            return false;
        }

        // Prevent transactions with excessive address size
        byte[] receiveAddress = tx.getReceiveAddress().getBytes();
        if (receiveAddress != null && !Arrays.equals(receiveAddress, EMPTY_BYTE_ARRAY) && receiveAddress.length > Constants.getMaxAddressByteLength()) {
            logger.warn("Receiver address to long: size: {}, tx {}", receiveAddress.length, tx.getHash());
            logger.warn("Transaction Data: {}", tx);
            logger.warn("Tx Included in the following block: {}", this.executionBlock);

            return false;
        }

        if (!tx.acceptTransactionSignature(config.getBlockchainConfig().getCommonConstants().getChainId())) {
            logger.warn("Transaction {} signature not accepted: {}", tx.getHash(), tx.getSignature());
            logger.warn("Transaction Data: {}", tx);
            logger.warn("Tx Included in the following block: {}", this.executionBlock);

            panicProcessor.panic("invalidsignature", String.format("Transaction %s signature not accepted: %s", tx.getHash(), tx.getSignature().toString()));
            execError(String.format("Transaction signature not accepted: %s", tx.getSignature().toString()));

            return false;
        }

        readyToExecute = true;
        return true;
    }

    public void execute() {

        if (!readyToExecute) {
            return;
        }

        logger.trace("Execute transaction {} {}", toBI(tx.getNonce()), tx.getHash());

        if (!localCall) {

            track.increaseNonce(tx.getSender());

            BigInteger txGasLimit = toBI(tx.getGasLimit());
            BigInteger txGasCost = toBI(tx.getGasPrice()).multiply(txGasLimit);
            track.addBalance(tx.getSender(), txGasCost.negate());

            if (logger.isTraceEnabled()){
                logger.trace("Paying: txGasCost: [{}], gasPrice: [{}], gasLimit: [{}]", txGasCost, toBI(tx.getGasPrice()), txGasLimit);
            }
        }

        if (tx.isContractCreation()) {
            create();
        } else {
            call();
        }
    }

    private void call() {
        if (!readyToExecute) {
            return;
        }

        logger.trace("Call transaction {} {}", toBI(tx.getNonce()), tx.getHash());

        RskAddress targetAddress = tx.getReceiveAddress();

        // DataWord(targetAddress)) can fail with exception:
        // java.lang.RuntimeException: Data word can't exceed 32 bytes:
        // if targetAddress size is greater than 32 bytes.
        // But init() will detect this earlier
        precompiledContract = PrecompiledContracts.getContractForAddress(config, new DataWord(targetAddress.getBytes()));

        if (precompiledContract != null) {
            precompiledContract.init(tx, executionBlock, track, blockStore, receiptStore, result.getLogInfoList());
            long requiredGas = precompiledContract.getGasForData(tx.getData());
            BigInteger txGasLimit = toBI(tx.getGasLimit());

            if (!localCall && txGasLimit.compareTo(BigInteger.valueOf(requiredGas)) < 0) {
                // no refund
                // no endowment
                execError(String.format("Out of Gas calling precompiled contract 0x%s, required: %d, left: %s ",
                        targetAddress.toString(), (requiredGas + basicTxCost), mEndGas));
                mEndGas = BigInteger.ZERO;
                return;
            } else {
                long gasUsed = requiredGas + basicTxCost;
                mEndGas = txGasLimit.subtract(BigInteger.valueOf(requiredGas + basicTxCost));

                // FIXME: save return for vm trace
                try {
                    byte[] out = precompiledContract.execute(tx.getData());
                    result.setHReturn(out);
                } catch (RuntimeException e) {
                    result.setException(e);
                }

                result.spendGas(gasUsed);
            }
        } else {
            byte[] code = track.getCode(targetAddress);
            if (isEmpty(code)) {
                mEndGas = toBI(tx.getGasLimit()).subtract(BigInteger.valueOf(basicTxCost));
                result.spendGas(basicTxCost);
            } else {
                ProgramInvoke programInvoke =
                        programInvokeFactory.createProgramInvoke(tx, txindex, executionBlock, cacheTrack, blockStore);

                this.vm = new VM(config);
                this.program = new Program(config, code, programInvoke, tx);
            }
        }

        if (result.getException() == null) {
            BigInteger endowment = toBI(tx.getValue());
            transfer(cacheTrack, tx.getSender(), targetAddress, endowment);
        }
    }

    private void create() {
        RskAddress newContractAddress = tx.getContractAddress();
        if (isEmpty(tx.getData())) {
            mEndGas = toBI(tx.getGasLimit()).subtract(BigInteger.valueOf(basicTxCost));
            cacheTrack.createAccount(newContractAddress);
        } else {
            ProgramInvoke programInvoke = programInvokeFactory.createProgramInvoke(tx, txindex, executionBlock, cacheTrack, blockStore);

            this.vm = new VM(config);
            this.program = new Program(config, tx.getData(), programInvoke, tx);

            // reset storage if the contract with the same address already exists
            // TCK test case only - normally this is near-impossible situation in the real network
            ContractDetails contractDetails = program.getStorage().getContractDetails(newContractAddress);
            for (DataWord key : contractDetails.getStorageKeys()) {
                program.storageSave(key, DataWord.ZERO);
            }
        }

        BigInteger endowment = toBI(tx.getValue());
        transfer(cacheTrack, tx.getSender(), newContractAddress, endowment);
    }

    private void execError(Throwable err) {
        logger.warn("execError: ", err);
        executionError = err.getMessage();
    }

    private void execError(String err) {
        logger.warn(err);
        executionError = err;
    }

    public void go() {
        if (!readyToExecute) {
            return;
        }

        // TODO: transaction call for pre-compiled  contracts
        if (vm == null) {
            cacheTrack.commit();
            return;
        }

        logger.trace("Go transaction {} {}", toBI(tx.getNonce()), tx.getHash());

        try {

            // Charge basic cost of the transaction
            program.spendGas(tx.transactionCost(config, executionBlock), "TRANSACTION COST");

            if (config.playVM()) {
                vm.play(program);
            }

            result = program.getResult();
            mEndGas = toBI(tx.getGasLimit()).subtract(toBI(program.getResult().getGasUsed()));

            if (tx.isContractCreation() && !result.isRevert()) {
                int createdContractSize = getLength(program.getResult().getHReturn());
                int returnDataGasValue = createdContractSize * GasCost.CREATE_DATA;
                if (mEndGas.compareTo(BigInteger.valueOf(returnDataGasValue)) < 0) {
                    program.setRuntimeFailure(
                            Program.ExceptionHelper.notEnoughSpendingGas(
                                    "No gas to return just created contract",
                                    returnDataGasValue,
                                    program));
                    result = program.getResult();
                    result.setHReturn(EMPTY_BYTE_ARRAY);
                } else if (createdContractSize > Constants.getMaxContractSize()) {
                    program.setRuntimeFailure(
                            Program.ExceptionHelper.tooLargeContractSize(
                                    Constants.getMaxContractSize(),
                                    createdContractSize));
                    result = program.getResult();
                    result.setHReturn(EMPTY_BYTE_ARRAY);
                } else {
                    mEndGas = mEndGas.subtract(BigInteger.valueOf(returnDataGasValue));
                    program.spendGas(returnDataGasValue, "CONTRACT DATA COST");
                    cacheTrack.saveCode(tx.getContractAddress(), result.getHReturn());
                }
            }

            if (result.getException() != null || result.isRevert()) {
                result.clearFieldsOnException();
                cacheTrack.rollback();

                if (result.getException() != null) {
                    throw result.getException();
                } else {
                    execError("REVERT opcode executed");
                }
            } else {
                cacheTrack.commit();
            }

        } catch (Throwable e) {

            // TODO: catch whatever they will throw on you !!!
//            https://github.com/ethereum/cpp-ethereum/blob/develop/libethereum/Executive.cpp#L241
            cacheTrack.rollback();
            mEndGas = BigInteger.ZERO;
            execError(e);

        }
    }

    public TransactionReceipt getReceipt() {
        if (receipt == null) {
            receipt = new TransactionReceipt();
            long totalGasUsed = gasUsedInTheBlock + getGasUsed();
            receipt.setCumulativeGas(totalGasUsed);
            receipt.setTransaction(tx);
            receipt.setLogInfoList(getVMLogs());
            receipt.setGasUsed(getGasUsed());
            receipt.setStatus(executionError.isEmpty()?TransactionReceipt.SUCCESS_STATUS:TransactionReceipt.FAILED_STATUS);
        }
        return receipt;
    }


    public void finalization() {
        if (!readyToExecute) {
            return;
        }

        // RSK if local call gas balances must not be changed
        if (localCall) {
            return;
        }

        logger.trace("Finalize transaction {} {}", toBI(tx.getNonce()), tx.getHash());

        cacheTrack.commit();

        // Should include only LogInfo's that was added during not rejected transactions
        List<LogInfo> notRejectedLogInfos = result.getLogInfoList().stream()
                .filter(logInfo -> !logInfo.isRejected())
                .collect(Collectors.toList());

        TransactionExecutionSummary.Builder summaryBuilder = TransactionExecutionSummary.builderFor(tx)
                .gasLeftover(mEndGas)
                .logs(notRejectedLogInfos)
                .result(result.getHReturn());

        if (result != null) {
            // Accumulate refunds for suicides
            result.addFutureRefund((long)result.getDeleteAccounts().size() * GasCost.SUICIDE_REFUND);
            long gasRefund = Math.min(result.getFutureRefund(), result.getGasUsed() / 2);
            RskAddress addr = tx.isContractCreation() ? tx.getContractAddress() : tx.getReceiveAddress();
            mEndGas = mEndGas.add(BigInteger.valueOf(gasRefund));

            summaryBuilder
                    .gasUsed(toBI(result.getGasUsed()))
                    .gasRefund(toBI(gasRefund))
                    .deletedAccounts(result.getDeleteAccounts())
                    .internalTransactions(result.getInternalTransactions());

            ContractDetails cdetails = track.getContractDetails(addr);

            if (cdetails != null) {
                summaryBuilder.storageDiff(cdetails.getStorage());
            }

            if (result.getException() != null) {
                summaryBuilder.markAsFailed();
            }
        }

        logger.trace("Building transaction execution summary");

        TransactionExecutionSummary summary = summaryBuilder.build();

        // Refund for gas leftover
        track.addBalance(tx.getSender(), summary.getLeftover().add(summary.getRefund()));
        logger.trace("Pay total refund to sender: [{}], refund val: [{}]", tx.getSender(), summary.getRefund());

        // Transfer fees to miner
        BigInteger summaryFee = summary.getFee();

        //TODO: REMOVE THIS WHEN THE LocalBLockTests starts working with REMASC
        if(config.isRemascEnabled()) {
            logger.trace("Adding fee to remasc contract account");
            track.addBalance(PrecompiledContracts.REMASC_ADDR, summaryFee);
        } else {
            track.addBalance(coinbase, summaryFee);
        }

        this.paidFees = summaryFee;

        if (result != null) {
            logger.trace("Processing result");
            logs = notRejectedLogInfos;

            result.getCodeChanges().forEach((key, value) -> track.saveCode(new RskAddress(key), value));
            // Traverse list of suicides
            result.getDeleteAccounts().forEach(address -> track.delete(new RskAddress(address)));
        }

        if (listener != null) {
            listener.onTransactionExecuted(summary);
        }

        logger.trace("tx listener done");

        if (config.vmTrace() && program != null && result != null) {
            ProgramTrace trace = program.getTrace().result(result.getHReturn()).error(result.getException());
            String txHash = tx.getHash().toString();
            try {
                saveProgramTraceFile(config, txHash, trace);
                if (listener != null) {
                    listener.onVMTraceCreated(txHash, trace);
                }
            } catch (IOException e) {
                String errorMessage = String.format("Cannot write trace to file: %s", e.getMessage());
                panicProcessor.panic("executor", errorMessage);
                logger.error(errorMessage);
            }
        }

        logger.trace("tx finalization done");
    }

    public TransactionExecutor setLocalCall(boolean localCall) {
        this.localCall = localCall;
        return this;
    }

    public List<LogInfo> getVMLogs() {
        return logs;
    }

    public ProgramResult getResult() {
        return result;
    }

    public long getGasUsed() {
        return toBI(tx.getGasLimit()).subtract(mEndGas).longValue();
    }

    public BigInteger getPaidFees() { return paidFees; }
}
