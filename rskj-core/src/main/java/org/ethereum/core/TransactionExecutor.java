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
import org.spongycastle.util.encoders.Hex;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static co.rsk.config.RskSystemProperties.CONFIG;
import static org.apache.commons.lang3.ArrayUtils.getLength;
import static org.apache.commons.lang3.ArrayUtils.isEmpty;
import static org.ethereum.util.BIUtil.*;
import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.ethereum.util.ByteUtil.toHexString;
import static org.ethereum.vm.VMUtils.saveProgramTraceFile;

/**
 * @author Roman Mandeleil
 * @since 19.12.2014
 */
public class TransactionExecutor {

    private static final Logger logger = LoggerFactory.getLogger("execute");
    private static final PanicProcessor panicProcessor = new PanicProcessor();

    private Transaction tx;
    private int txindex;
    private Repository track;
    private Repository cacheTrack;
    private BlockStore blockStore;
    private ReceiptStore receiptStore;
    private final long gasUsedInTheBlock;
    private BigInteger paidFees;
    private boolean readyToExecute = false;

    private ProgramInvokeFactory programInvokeFactory;
    private byte[] coinbase;

    private ProgramResult result = new ProgramResult();
    private Block executionBlock;

    private final EthereumListener listener;

    private VM vm;
    private Program program;

    PrecompiledContracts.PrecompiledContract precompiledContract;

    BigInteger mEndGas = BigInteger.ZERO;
    long basicTxCost = 0;
    List<LogInfo> logs = null;

    boolean localCall = false;

    public TransactionExecutor(Transaction tx, int txindex, byte[] coinbase, Repository track, BlockStore blockStore, ReceiptStore receiptStore,
                               ProgramInvokeFactory programInvokeFactory, Block executionBlock) {

        this(tx, txindex, coinbase, track, blockStore, receiptStore, programInvokeFactory, executionBlock, new EthereumListenerAdapter(), 0);
    }

    public TransactionExecutor(Transaction tx, int txindex, byte[] coinbase, Repository track, BlockStore blockStore, ReceiptStore receiptStore,
                               ProgramInvokeFactory programInvokeFactory, Block executionBlock,
                               EthereumListener listener, long gasUsedInTheBlock) {

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
        basicTxCost = tx.transactionCost(executionBlock);

        if (localCall) {
            readyToExecute = true;
            return readyToExecute;
        }

        BigInteger txGasLimit = new BigInteger(1, tx.getGasLimit());
        BigInteger curBlockGasLimit = new BigInteger(1, executionBlock.getGasLimit());

        boolean cumulativeGasReached = txGasLimit.add(BigInteger.valueOf(gasUsedInTheBlock)).compareTo(curBlockGasLimit) > 0;
        if (cumulativeGasReached) {

            logger.warn("Too much gas used in this block: Block Gas Limit: {} , Tx Gas Limit {}, Gas Used in the Block: {}", curBlockGasLimit, txGasLimit, gasUsedInTheBlock);

            panicProcessor.panic("toomuchgasused", String.format("Too much gas used in this block: Block Gas Limit: %d , Tx Gas Limit %d, Gas Used in the Block: %d", curBlockGasLimit.longValue(), txGasLimit.longValue(), gasUsedInTheBlock));

            // TODO: save reason for failure
            return false;
        }

        if (txGasLimit.compareTo(BigInteger.valueOf(basicTxCost)) < 0) {

            logger.warn("Not enough gas for transaction execution: Require: {} Got: {}", basicTxCost, txGasLimit);

            panicProcessor.panic("notenoughgas", String.format("Not enough gas for transaction execution: Require: %d Got: %d", basicTxCost, txGasLimit.longValue()));

            // TODO: save reason for failure
            return false;
        }

        BigInteger reqNonce = track.getNonce(tx.getSender());
        BigInteger txNonce = toBI(tx.getNonce());
        if (isNotEqual(reqNonce, txNonce)) {

            if (logger.isWarnEnabled()) {
                logger.warn("Invalid nonce: sender {}, required: {} , tx.nonce: {}, tx {}", Hex.toHexString(tx.getSender()), reqNonce, txNonce, Hex.toHexString(tx.getHash()));
                logger.warn("Transaction Data: {}", tx);
                logger.warn("Tx Included in the following block: {}", this.executionBlock.getShortDescr());
            }
            // TODO: save reason for failure

            panicProcessor.panic("invalidnonce", String.format("Invalid nonce: sender %s, required: %d , tx.nonce: %d, tx %s", Hex.toHexString(tx.getSender()), reqNonce.longValue(), txNonce.longValue(), Hex.toHexString(tx.getHash())));

            return false;
        }

        BigInteger txGasCost = toBI(tx.getGasPrice()).multiply(txGasLimit);
        BigInteger totalCost = toBI(tx.getValue()).add(txGasCost);
        BigInteger senderBalance = track.getBalance(tx.getSender());

        if (!isCovers(senderBalance, totalCost)) {

            if (logger.isWarnEnabled()) {
                logger.warn("Not enough cash: Require: {}, Sender cash: {}, tx {}", totalCost, senderBalance, Hex.toHexString(tx.getHash()));
                logger.warn("Transaction Data: {}", tx);
                logger.warn("Tx Included in the following block: {}", this.executionBlock);
            }

            return false;
        }

        // Prevent transactions with excessive address size
        byte[] receiveAddress = tx.getReceiveAddress();
        if (receiveAddress != null && !Arrays.equals(receiveAddress, EMPTY_BYTE_ARRAY) && receiveAddress.length > Constants.getMaxAddressByteLength()) {
            if (logger.isWarnEnabled()) {
                logger.warn("Receiver address to long: size: {}, tx {}", receiveAddress.length, Hex.toHexString(tx.getHash()));
                logger.warn("Transaction Data: {}", tx);
                logger.warn("Tx Included in the following block: {}", this.executionBlock);
            }

            return false;
        }

        if (!tx.acceptTransactionSignature()) {
            if (logger.isWarnEnabled()) {
                logger.warn("Transaction {} signature not accepted: {}", Hex.toHexString(tx.getHash()), tx.getSignature());
                logger.warn("Transaction Data: {}", tx);
                logger.warn("Tx Included in the following block: {}", this.executionBlock);
            }

            panicProcessor.panic("invalidsignature", String.format("Transaction %s signature not accepted: %s", Hex.toHexString(tx.getHash()), tx.getSignature().toString()));

            return false;
        }

        readyToExecute = true;
        return true;
    }

    public void execute() {

        if (!readyToExecute) {
            return;
        }

        logger.info("Execute transaction {} {}", toBI(tx.getNonce()), Hex.toHexString(tx.getHash()));

        if (!localCall) {

            track.increaseNonce(tx.getSender());

            BigInteger txGasLimit = toBI(tx.getGasLimit());
            BigInteger txGasCost = toBI(tx.getGasPrice()).multiply(txGasLimit);
            track.addBalance(tx.getSender(), txGasCost.negate());

            if (logger.isInfoEnabled()) {
                logger.info("Paying: txGasCost: [{}], gasPrice: [{}], gasLimit: [{}]", txGasCost, toBI(tx.getGasPrice()), txGasLimit);
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

        logger.info("Call transaction {} {}", toBI(tx.getNonce()), Hex.toHexString(tx.getHash()));

        byte[] targetAddress = tx.getReceiveAddress();

        // DataWord(targetAddress)) can fail with exception:
        // java.lang.RuntimeException: Data word can't exceed 32 bytes:
        // if targetAddress size is greater than 32 bytes.
        // But init() will detect this earlier
        precompiledContract = PrecompiledContracts.getContractForAddress(new DataWord(targetAddress));

        if (precompiledContract != null) {
            precompiledContract.init(tx, executionBlock, track, blockStore, receiptStore, result.getLogInfoList());
            long requiredGas = precompiledContract.getGasForData(tx.getData());
            BigInteger txGasLimit = toBI(tx.getGasLimit());

            if (!localCall && txGasLimit.compareTo(BigInteger.valueOf(requiredGas)) < 0) {
                // no refund
                // no endowment
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

                this.vm = new VM();
                this.program = new Program(code, programInvoke, tx);
            }
        }

        if (result.getException() == null) {
            BigInteger endowment = toBI(tx.getValue());
            transfer(cacheTrack, tx.getSender(), targetAddress, endowment);
        }
    }

    private void create() {
        byte[] newContractAddress = tx.getContractAddress();
        if (isEmpty(tx.getData())) {
            mEndGas = toBI(tx.getGasLimit()).subtract(BigInteger.valueOf(basicTxCost));
            cacheTrack.createAccount(tx.getContractAddress());
        } else {
            ProgramInvoke programInvoke = programInvokeFactory.createProgramInvoke(tx, txindex, executionBlock, cacheTrack, blockStore);

            this.vm = new VM();
            this.program = new Program(tx.getData(), programInvoke, tx);

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

    public void go() {
        if (!readyToExecute) {
            return;
        }

        // TODO: transaction call for pre-compiled  contracts
        if (vm == null) {
            cacheTrack.commit();
            return;
        }

        logger.info("Go transaction {} {}", toBI(tx.getNonce()), Hex.toHexString(tx.getHash()));

        try {

            // Charge basic cost of the transaction
            program.spendGas(tx.transactionCost(executionBlock), "TRANSACTION COST");

            if (CONFIG.playVM()) {
                vm.play(program);
            }

            result = program.getResult();
            mEndGas = toBI(tx.getGasLimit()).subtract(toBI(program.getResult().getGasUsed()));

            if (tx.isContractCreation() && !result.isRevert()) {
                int createdContractSize = getLength(program.getResult().getHReturn());
                int returnDataGasValue = createdContractSize * GasCost.CREATE_DATA + GasCost.CREATE;
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
                }
            } else {
                cacheTrack.commit();
            }

        } catch (Throwable e) {

            // TODO: catch whatever they will throw on you !!!
//            https://github.com/ethereum/cpp-ethereum/blob/develop/libethereum/Executive.cpp#L241
            cacheTrack.rollback();
            mEndGas = BigInteger.ZERO;
        }
    }

    public void finalization() {
        if (!readyToExecute) {
            return;
        }

        // RSK if local call gas balances must not be changed
        if (localCall) {
            return;
        }

        logger.info("Finalize transaction {} {}", toBI(tx.getNonce()), Hex.toHexString(tx.getHash()));

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
            byte[] addr = tx.isContractCreation() ? tx.getContractAddress() : tx.getReceiveAddress();
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

        logger.info("Building transaction execution summary");

        TransactionExecutionSummary summary = summaryBuilder.build();

        // Refund for gas leftover
        track.addBalance(tx.getSender(), summary.getLeftover().add(summary.getRefund()));
        logger.info("Pay total refund to sender: [{}], refund val: [{}]", Hex.toHexString(tx.getSender()), summary.getRefund());

        // Transfer fees to miner
        BigInteger summaryFee = summary.getFee();

        //TODO: REMOVE THIS WHEN THE LocalBLockTests starts working with REMASC
        if(RskSystemProperties.CONFIG.isRemascEnabled()) {
            logger.info("Adding fee to remasc contract account");
            track.addBalance(Hex.decode(PrecompiledContracts.REMASC_ADDR), summaryFee);
        } else {
            track.addBalance(coinbase, summaryFee);
        }

        this.paidFees = summaryFee;

        if (result != null) {
            logger.info("Processing result");
            logs = notRejectedLogInfos;

            result.getCodeChanges().forEach((key, value) -> track.saveCode(key.getLast20Bytes(), value));
            // Traverse list of suicides
            result.getDeleteAccounts().forEach(address -> track.delete(address.getLast20Bytes()));
        }

        if (listener != null) {
            listener.onTransactionExecuted(summary);
        }

        logger.info("tx listener done");

        if (CONFIG.vmTrace() && program != null && result != null) {
            ProgramTrace trace = program.getTrace().result(result.getHReturn()).error(result.getException());
            String txHash = toHexString(tx.getHash());
            try {
                saveProgramTraceFile(txHash, CONFIG.vmTraceCompressed(), trace);
                if (listener != null) {
                    listener.onVMTraceCreated(txHash, trace);
                }
            } catch (IOException e) {
                String errorMessage = "Cannot write trace to file";
                panicProcessor.panic("executor", errorMessage + ": " + e);
                logger.error(errorMessage, e);
            }
        }

        logger.info("tx finalization done");
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
