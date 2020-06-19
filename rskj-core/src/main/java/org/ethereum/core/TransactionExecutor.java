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
import co.rsk.metrics.profilers.Profiler;
import co.rsk.metrics.profilers.ProfilerFactory;
import co.rsk.panic.PanicProcessor;
import co.rsk.rpc.modules.trace.ProgramSubtrace;
import co.rsk.core.types.ints.Uint24;

import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.MutableRepository; //#mish todo remove this later, added for testing
import org.ethereum.vm.*;
import org.ethereum.vm.program.RentData;
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

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;
import java.time.Instant;

import static co.rsk.util.ListArrayUtil.getLength;
import static co.rsk.util.ListArrayUtil.isEmpty;
import static org.ethereum.util.BIUtil.*;
import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

/**
 * @author Roman Mandeleil
 * @since 19.12.2014
 */
/** #mish notes:
 // Overview: 
 * 4 stages: init() -> exec() -> go() -> finalize()
 * init(): does basic checks, addr are valid, gaslimits, sufficient balance, valid nonce
 * exec(): * Transfer basic cost + gasLImits from sender. Then switch to call() or create()
           * call(): either PCC or not. 
                - If PCC execute and return the result. 
                - If not PCC, getCode(), set up vm and prog for next stage `go()`
           * create() : create account + storage root, but code is not saved to trie yet. 
                        Setup vm and prog for next stage createContract() in go().
 * go(): * if PCC.. nothing to do, commit the cache
         * else (call to non PCC or create), then use vm and prog setup earlier to execute prog i.e. vm.Play(prog)
         * if create, then call createContract()
                - this computes contract size, gascost, saves the code to repository
 * finalize(): - commit changes to repository, make refunds, execution summary and logs.. wrap things up. 
//  Gas spending and endowment changes along the way at every step..  some permanent (track),
//  some temporary via cacheTrack or spendgas() thru program.result. Can be committed or rolledback.
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
    private final boolean playVm;
    private final boolean enableRemasc;
    private String executionError = "";
    private final long gasUsedInTheBlock;
    private Coin paidFees;

    private final ProgramInvokeFactory programInvokeFactory;
    private final RskAddress coinbase; // #mish todo computing rent for coinbase account here (should do only once per block?)

    private TransactionReceipt receipt;
    private ProgramResult result = new ProgramResult();
    private final Block executionBlock;

    private VM vm;
    private Program program;
    private List<ProgramSubtrace> subtraces;

    private PrecompiledContracts.PrecompiledContract precompiledContract;

    private long mEndGas = 0;
    private long mEndRentGas = 0;
    private long refTimeStamp; // reference to estimate storage rent
    private long estRentGas = 0; //#mish unlike execution gas, rent gas is only collected at EOT. Use this for tracking estimateduse
    private long basicTxCost = 0;
    private List<LogInfo> logs = null;
    private final Set<DataWord> deletedAccounts;
    private SignatureCache signatureCache;

    private boolean localCall = false;

    public TransactionExecutor(
            Constants constants, ActivationConfig activationConfig, Transaction tx, int txindex, RskAddress coinbase,
            Repository track, BlockStore blockStore, ReceiptStore receiptStore, BlockFactory blockFactory,
            ProgramInvokeFactory programInvokeFactory, Block executionBlock, long gasUsedInTheBlock, VmConfig vmConfig,
            boolean playVm, boolean remascEnabled, PrecompiledContracts precompiledContracts, Set<DataWord> deletedAccounts,
            SignatureCache signatureCache) {
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
        this.gasUsedInTheBlock = gasUsedInTheBlock;
        this.vmConfig = vmConfig;
        this.precompiledContracts = precompiledContracts;
        this.playVm = playVm;
        this.enableRemasc = remascEnabled;
        this.deletedAccounts = new HashSet<>(deletedAccounts);
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
        //System.out.println("done execute() in Tx Exec ");
        this.go();
        //System.out.println("done with go() in Tx Exec ");
        this.finalization();
        return true;
    }

    /**
     * Do all the basic validation, if the executor
     * will be ready to run the transaction at the end
     * set readyToExecute = true
     */
    private boolean init() {
        //e.g. 21K TX or 53K contract creation (32K for create + 21k) + 'data' cost (68 per non=0 byte) 
        basicTxCost = tx.transactionCost(constants, activations);

        if (localCall) {
            return true;
        }

        // #mish: this is only execution gas limit
        long txGasLimit = GasCost.toGas(tx.getGasLimit());
        long txRentGasLimit = GasCost.toGas(tx.getRentGasLimit());

        long curBlockGasLimit = GasCost.toGas(executionBlock.getGasLimit());

        if (!gasIsValid(txGasLimit, curBlockGasLimit)) { //rentGasLimit does not count towards block gas limits
            return false;
        }

        if (!nonceIsValid()) {
            return false;
        }

        Coin totalCost = tx.getValue();

        if (basicTxCost > 0 ) {
            // add gas cost only for priced transactions
            //execution cost
            Coin txGasCost = tx.getGasPrice().multiply(BigInteger.valueOf(txGasLimit));
            //storage rent cost
            Coin txRentGasCost = tx.getGasPrice().multiply(BigInteger.valueOf(txRentGasLimit));
            totalCost = totalCost.add(txGasCost).add(txRentGasCost);
        }
        
        Coin senderBalance = track.getBalance(tx.getSender());

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
                logger.warn("Invalid nonce: sender {}, required: {} , tx.nonce: {}, tx {}", tx.getSender(), reqNonce, txNonce, tx.getHash());
                logger.warn("Transaction Data: {}", tx);
                logger.warn("Tx Included in the following block: {}", this.executionBlock.getShortDescr());
            }

            execError(String.format("Invalid nonce: required: %s , tx.nonce: %s", reqNonce, txNonce));
            return false;
        }

        return true;
    }

    //note: storage rent gas does not count towards block gas limits 
    private boolean gasIsValid(long txGasLimit, long curBlockGasLimit) {
        // if we've passed the curBlockGas limit we must stop exec
        // cumulativeGas being equal to GasCost.MAX_GAS is a border condition
        // which is used on some stress tests, but its far from being practical
        // as the current gas limit on blocks is 6.8M... several orders of magnitude
        // less than the theoretical max gas on blocks.
        long cumulativeGas = GasCost.add(txGasLimit, gasUsedInTheBlock);

        boolean cumulativeGasReached = cumulativeGas > curBlockGasLimit || cumulativeGas == GasCost.MAX_GAS;
        if (cumulativeGasReached) {
            execError(String.format("Too much gas used in this block: available in block: %s tx sent: %s",
                    curBlockGasLimit - txGasLimit,
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
            // set reference timestamp for rent computations: Todo: should this be at the block level?
            refTimeStamp = Instant.now().getEpochSecond();
            // #mish add sender to Map of accessed nodes (for storage rent tracking)
            // but don't add receiver address yet as it may be a pre-compiled contract
            accessedNodeAdder(this.coinbase, track, result);
            accessedNodeAdder(tx.getSender(),track, result);    
            
            track.increaseNonce(tx.getSender());
    
            long txGasLimit = GasCost.toGas(tx.getGasLimit());
            long txRentGasLimit = GasCost.toGas(tx.getRentGasLimit());
            //execution gas limit  gas2Coin
            Coin txGasCost = tx.getGasPrice().multiply(BigInteger.valueOf(txGasLimit));
            //storage rent gas limit gas2Coin
            Coin txRentGasCost = tx.getGasPrice().multiply(BigInteger.valueOf(txRentGasLimit));
            // reduce balance first
            track.addBalance(tx.getSender(), txGasCost.add(txRentGasCost).negate());

            logger.trace("Paying: txGasCost: [{}],  gasPrice: [{}], gasLimit: [{}]",
                                    txGasCost, tx.getGasPrice(), txGasLimit);
        }

        if (tx.isContractCreation()) {
            create();
        } else {
            call();
        }
    }

    // used in call()
    private boolean enoughGas(long txGasLimit, long requiredGas, long gasUsed) {
        if (!activations.isActive(ConsensusRule.RSKIP136)) {
            return txGasLimit >= requiredGas;
        }
        //the following is more restrictive since gasUsed = baseTxCost (fixed) + requiredGas
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
            Metric metric = profiler.start(Profiler.PROFILING_TYPE.PRECOMPILED_CONTRACT_INIT);
            precompiledContract.init(tx, executionBlock, track, blockStore, receiptStore, result.getLogInfoList());
            profiler.stop(metric);
            metric = profiler.start(Profiler.PROFILING_TYPE.PRECOMPILED_CONTRACT_EXECUTE);

            long requiredGas = precompiledContract.getGasForData(tx.getData());
            long txGasLimit = GasCost.toGas(tx.getGasLimit());
            long txRentGasLimit = GasCost.toGas(tx.getRentGasLimit());
            long gasUsed = GasCost.add(requiredGas, basicTxCost);
            if (!localCall && !enoughGas(txGasLimit, requiredGas, gasUsed)) {
                // no refund no endowment
                execError(String.format( "Out of Gas calling precompiled contract at block %d " +
                                "for address 0x%s. required: %s, used: %s, left: %s ",
                        executionBlock.getNumber(), targetAddress.toString(), requiredGas, gasUsed, mEndGas));
                mEndGas = 0;
                // #mish: if exec gas OOG, do not refund all rent Gas.. keep 25% as per RSKIP113
                mEndRentGas = 3*txRentGasLimit/4; //#mish: with pre compiles should all rent gas be refunded?
                // increase estimated rentgas
                estRentGas += txRentGasLimit/4;
                profiler.stop(metric);
                return;
            }
            // continue with pcc call.. update refund amount to limit minus used so far
            mEndGas = activations.isActive(ConsensusRule.RSKIP136) ?
                    GasCost.subtract(txGasLimit, gasUsed) :
                    txGasLimit - gasUsed;
            // update refund status of rentGas, could be > 0 sender account nodes (via accessedNodeAdder)
            mEndRentGas = activations.isActive(ConsensusRule.RSKIP136) ?
                    GasCost.subtract(txRentGasLimit, estRentGas) :
                    txRentGasLimit - estRentGas; 

            // FIXME: save return for vm trace
            try {
                byte[] out = precompiledContract.execute(tx.getData());
                this.subtraces = precompiledContract.getSubtraces();
                result.setHReturn(out);
                /** #mish: creating dummy accounts for pre-compiled contracts 
                 * Pre-compiled contracts to do not exist in Trie/repository (they don't have to).
                 * In ethereum a contract calling another contract costs 700. But if that contract does not exist,
                 * then a new account is created which costs an additional 25000 for `NEW_ACCT_CALL`
                 * see computecallgas() in VM.java
                 * One way to avoid this cost for pre compiled contracts is to create nodes in the trie for them
                 * as done here, so calls to PCCs cost 700 and not 700 + 25000. 
                 * As per SDL -> this check should ideally happen before a precompiled is executed.                 
                */
                if (!track.isExist(targetAddress)) {
                    track.createAccount(targetAddress);
                    track.setupContract(targetAddress);
                } else if (!track.isContract(targetAddress)) {
                    track.setupContract(targetAddress);
                }
            } catch (RuntimeException e) {
                result.setException(e);
            }
            result.spendGas(gasUsed);
            // #mish no storage rent implications here.. moving on.
            profiler.stop(metric);
        } else {    // #mish if not pre-compiled contract
            // add the node to accessed nodes Map for rent tracking
            accessedNodeAdder(tx.getReceiveAddress(),track, result);
            byte[] code = track.getCode(targetAddress);
            // Code can be null 
            // #mish Aside: even for empty code, storage rent will be > 0, because of overhead (RSKIP 113) 
            if (isEmpty(code)) {
                mEndGas = GasCost.subtract(GasCost.toGas(tx.getGasLimit()), basicTxCost);
                result.spendGas(basicTxCost);
            } else {
                ProgramInvoke programInvoke =
                        programInvokeFactory.createProgramInvoke(tx, txindex, executionBlock, cacheTrack, blockStore);

                this.vm = new VM(vmConfig, precompiledContracts);
                // #mish: same as in create(), except program arg (byte[] ops) is `code` instead of `tx.getData()`
                this.program = new Program(vmConfig, precompiledContracts, blockFactory, activations, code, programInvoke, tx, deletedAccounts);
            }
        }

        if (result.getException() == null) {
            Coin endowment = tx.getValue();
            cacheTrack.transfer(tx.getSender(), targetAddress, endowment);
        }
    }

    private void create() {
        RskAddress newContractAddress = tx.getContractAddress();
        cacheTrack.createAccount(newContractAddress); // pre-created

        if (isEmpty(tx.getData())) {
            mEndGas = GasCost.subtract(GasCost.toGas(tx.getGasLimit()), basicTxCost); //reduce refund by basicTxCost

            // If there is no data, then the account is created, but without code nor
            // storage. It doesn't even call setupContract() to setup a storage root
        } else {
            cacheTrack.setupContract(newContractAddress);
            ProgramInvoke programInvoke = programInvokeFactory.createProgramInvoke(tx, txindex, executionBlock, cacheTrack, blockStore);

            this.vm = new VM(vmConfig, precompiledContracts);
            // same as call, except using `tx.getData()`, rather than `code`
            this.program = new Program(vmConfig, precompiledContracts, blockFactory, activations, tx.getData(), programInvoke, tx, deletedAccounts);
 
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
        cacheTrack.transfer(tx.getSender(), newContractAddress, endowment);
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

        Metric metric = profiler.start(Profiler.PROFILING_TYPE.VM_EXECUTE);
        try {

            // Charge basic cost of the transaction
            program.spendGas(tx.transactionCost(constants, activations), "TRANSACTION COST");
            // #mish: program operations: `code` from repository (call to addr) or `tx.getData()` (in case of create)
            if (playVm) {
                vm.play(program);
            }
            // #mish: this is the first assignment of result. Next assignemnt (overwrit) happens within createContract() 
            result = program.getResult();
            //result.merge(program.getResult());
            

            mEndGas = GasCost.subtract(GasCost.toGas(tx.getGasLimit()), program.getResult().getGasUsed());

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
            mEndGas = 0;
            mEndRentGas = 0; // #mish todo should this be 0 or 75%?
            execError(e);
            profiler.stop(metric);
            return;
        }
        cacheTrack.commit();
        // add newly created contract nodes to createdNode Map for rent computation. After the cached repository is committed.
        if (tx.isContractCreation() && !result.isRevert()) {
                createdNodeAdder(tx.getContractAddress(), track, result);    
            }
        profiler.stop(metric);
    }

      
    /**#mish  createContract() is called in go(). It estimates gas costs based on contract size, saves code to cached repository 
     * In contrast, create() is called earlier [in execute()], where is sets up the account and storage root. 
    */ 
    private void createContract() {
        int createdContractSize = getLength(program.getResult().getHReturn());
        long returnDataGasValue = GasCost.multiply(GasCost.CREATE_DATA, createdContractSize);
        if (mEndGas < returnDataGasValue) {
            program.setRuntimeFailure(
                    Program.ExceptionHelper.notEnoughSpendingGas(
                            "No gas to return just created contract",
                            returnDataGasValue,
                            program));
            //result = program.getResult();
            //#mish merge with existing (result may have rent information)
            result.merge(program.getResult());
            result.setHReturn(EMPTY_BYTE_ARRAY);
        } else if (createdContractSize > Constants.getMaxContractSize()) {
            program.setRuntimeFailure(
                    Program.ExceptionHelper.tooLargeContractSize(
                            Constants.getMaxContractSize(),
                            createdContractSize));
            
            //result = program.getResult();
            //#mish merge with existing (result may have rent information) 
            result.merge(program.getResult());
            result.setHReturn(EMPTY_BYTE_ARRAY);
        } else {
            mEndGas = GasCost.subtract(mEndGas,  returnDataGasValue);
            program.spendGas(returnDataGasValue, "CONTRACT DATA COST");
            cacheTrack.saveCode(tx.getContractAddress(), result.getHReturn());
        }
    }
    // #mish todo: setStatus needs to be modified to reflect Manual revert, or rentgas OOG as per RSKIP113 
    public TransactionReceipt getReceipt() {
        if (receipt == null) {
            receipt = new TransactionReceipt();
            long totalGasUsed = GasCost.add(gasUsedInTheBlock, getGasUsed());
            receipt.setCumulativeGas(totalGasUsed);
            receipt.setTransaction(tx);
            receipt.setLogInfoList(getVMLogs());
            receipt.setGasUsed(getGasUsed() + getRentGasUsed()); //#mish combined gas usage (exec + rent)
            receipt.setStatus(executionError.isEmpty()?TransactionReceipt.SUCCESS_STATUS:TransactionReceipt.FAILED_STATUS); // #mish todo: RSKIP113
        }
        return receipt;
    }


    private void finalization() {
        // RSK if local call gas balances must not be changed
        if (localCall) {
            return;
        }

        logger.trace("Finalize transaction {} {}", toBI(tx.getNonce()), tx.getHash());

        cacheTrack.commit();

        //Transaction sender is stored in cache
        signatureCache.storeSender(tx);

        // Collect rent gas before finalization
        result.spendRentGas(estRentGas);
        

        // Should include only LogInfo's that was added during not rejected transactions
        List<LogInfo> notRejectedLogInfos = result.getLogInfoList().stream()
                .filter(logInfo -> !logInfo.isRejected())
                .collect(Collectors.toList());

        // #mish todo: rent mods in builder and here
        TransactionExecutionSummary.Builder summaryBuilder = TransactionExecutionSummary.builderFor(tx)
                .gasLeftover(BigInteger.valueOf(mEndGas + mEndRentGas)) // #mish combine exec and rent gas left over
                .logs(notRejectedLogInfos)
                .result(result.getHReturn());

        // Accumulate refunds for suicides
        result.addFutureRefund(GasCost.multiply(result.getDeleteAccounts().size(), GasCost.SUICIDE_REFUND));
        long gasRefund = Math.min(result.getFutureRefund(), result.getGasUsed() / 2);
         
        mEndGas = activations.isActive(ConsensusRule.RSKIP136) ?
                GasCost.add(mEndGas, gasRefund) :
                mEndGas + gasRefund;

        summaryBuilder
                .gasUsed(toBI(result.getGasUsed() + result.getRentGasUsed()))
                .gasRefund(toBI(gasRefund))
                .deletedAccounts(result.getDeleteAccounts())
                .internalTransactions(result.getInternalTransactions());

        if (result.getException() != null) {
            summaryBuilder.markAsFailed();
        }

        logger.trace("Building transaction execution summary");

        TransactionExecutionSummary summary = summaryBuilder.build();

        // Refund for gas leftover
        track.addBalance(tx.getSender(), summary.getLeftover().add(summary.getRefund()));
        logger.trace("Pay total refund to sender: [{}], refund val: [{}]", tx.getSender(), summary.getRefund());


        // Transfer fees to miner
        Coin summaryFee = summary.getFee();

        //TODO: REMOVE THIS WHEN THE LocalBLockTests starts working with REMASC
        if(enableRemasc) {
            logger.trace("Adding fee to remasc contract account");
            track.addBalance(PrecompiledContracts.REMASC_ADDR, summaryFee);
        } else {
            track.addBalance(coinbase, summaryFee);
        }

        this.paidFees = summaryFee;

        //#mish for testing
        System.out.println("\nexec gas " + result.getGasUsed() +
                           "\nrent gas " + estRentGas + 
                           "\ngas refund " + (mEndGas + mEndRentGas) +
                           "\nTx fees " + paidFees);

        logger.trace("Processing result");
        logs = notRejectedLogInfos;

        /* #mish testing before and after rent update remove later
        result.getAccessedNodes().forEach(
            (key, rentData) -> {
                    RskAddress tmpAddr = new RskAddress(Arrays.copyOfRange(key.getData(),12,32));
                    System.out.println(track.getAccountNodeLRPTime(tmpAddr));
            }
        );*/

        // save created and accessed node with updated rent timestamps to repository. Any value modifications 
        result.getCreatedNodes().forEach((key, rentData) -> track.updateNodeWithRent(key, rentData.getLRPTime()));        
        
        result.getAccessedNodes().forEach((key, rentData) -> track.updateNodeWithRent(key, rentData.getLRPTime()));
        
        /*
        System.out.println(this.coinbase);
        System.out.println(tx.getSender());
        System.out.println(tx.getReceiveAddress()+"\n\n");
        */

        //#mish testing before and after rent update remove later
        //System.out.println(track instanceof MutableRepository);
        
        result.getAccessedNodes().forEach(
            (key, rentData) -> {
                    //System.out.println(key.getData().length);
                    byte[] tmpkey = Arrays.copyOfRange(key.getData(),11,31);
                    RskAddress tmpAddr = new RskAddress(tmpkey);
                    System.out.println(track.getAccountNodeLRPTime(tmpAddr));
            }
        );



        // #mish what is this for? Git grep doesn't reveal anything (neither does github search)
        result.getCodeChanges().forEach((key, value) -> track.saveCode(new RskAddress(key), value));
        // Traverse list of suicides
        result.getDeleteAccounts().forEach(address -> track.delete(new RskAddress(address)));

        logger.trace("tx listener done");

        logger.trace("tx finalization done");
    }

    /**
     * This extracts the trace to an object in memory.
     * Refer to {@link org.ethereum.vm.VMUtils#saveProgramTraceFile} for a way to saving the trace to a file.
     * #mish: for tracing only this is not called as part of TX execution. 
     */
    public void extractTrace(ProgramTraceProcessor programTraceProcessor) {
        if (program != null) {
            // TODO improve this settings; the trace should already have the values
            ProgramTrace trace = program.getTrace().result(result.getHReturn()).error(result.getException()).revert(result.isRevert());
            programTraceProcessor.processProgramTrace(trace, tx.getHash());
        }
        else {
            // #mish: todo rentGas argument is from wire tx.. verify
            TransferInvoke invoke = new TransferInvoke(DataWord.valueOf(tx.getSender().getBytes()), DataWord.valueOf(tx.getReceiveAddress().getBytes()), 0L, GasCost.toGas(tx.getRentGasLimit()), DataWord.valueOf(tx.getValue().getBytes()));

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

    // #mish: This is used only twice and that too within getReceipt(). ProgramResult.getGasUsed() is used more generally.
    public long getGasUsed() {
        if (activations.isActive(ConsensusRule.RSKIP136)) {
            return GasCost.subtract(GasCost.toGas(tx.getGasLimit()), mEndGas);
        }
        return toBI(tx.getGasLimit()).subtract(toBI(mEndGas)).longValue();
    }

    
    public long getRentGasUsed() {
        if (activations.isActive(ConsensusRule.RSKIP136)) {
            return GasCost.subtract(GasCost.toGas(tx.getRentGasLimit()), mEndRentGas);
        }
        return toBI(tx.getRentGasLimit()).subtract(toBI(mEndRentGas)).longValue();
    }

    public Coin getPaidFees() { return paidFees; }

    /** #mish Helper methods for storage rent 
    */
    
    public long getRefTimeStamp(){
        return this.refTimeStamp;
    }
    
    public long getEstRentGas(){
        return this.estRentGas;
    }

    /* #mish Add nodes accessed by or created in a transaction to the maps
    * "accessedNodes" or "createdNodes" in programResult. 
     * Methods compute outstanding rent due for exsiting nodes and 6 months advance for new nodes.
     * node info obtained for each provided RSK addr via methods defined in mutableRepository
     * The method retreives nodes containing account state and for contracts: code and storage root. 
     * This should be called the first time any RSK addr is referenced in a TX or a child process
     * Storage nodes accessed via SLOAD or SSTORE are not included.
    */
    public void accessedNodeAdder(RskAddress addr, Repository repository, ProgramResult progRes){
        long rd = 0; // initalize rent due to 0
        ByteArrayWrapper accKey = repository.getAccountNodeKey(addr);
        // if the node is not in the map, add the rent owed to current estimate
        if (!progRes.getAccessedNodes().containsKey(accKey)){
            Uint24 vLen = repository.getAccountNodeValueLength(addr);
            long accLrpt = repository.getAccountNodeLRPTime(addr);
            RentData accNode = new RentData(vLen, accLrpt);
            // compute the rent due. Treat these nodes as 'modified' (since account state will be updated)
            accNode.setRentDue(this.getRefTimeStamp(), true); // account node value length may not change much
            rd = accNode.getRentDue();
            //System.out.println("accessed node rent added: "+ rd);
            estRentGas += rd;   //"collect" rent due
            accNode.setLRPTime(this.getRefTimeStamp()); //update rent paid timestamp
            // add to hashmap (internally this is a putIfAbsent) 
            progRes.addAccessedNode(accKey, accNode);
        }
        // if this is a contract then add info for storage root and code
        if (repository.isContract(addr)) {
            // code node
            ByteArrayWrapper cKey = repository.getCodeNodeKey(addr);
            if (!progRes.getAccessedNodes().containsKey(cKey)){
                Uint24 cLen = repository.getCodeNodeLength(addr);
                long cLrpt = repository.getCodeNodeLRPTime(addr);
                RentData codeNode = new RentData(cLen, cLrpt);
                // compute rent and update estRent as needed
                codeNode.setRentDue(this.getRefTimeStamp(), false); // code is unlikely to be modified
                rd = codeNode.getRentDue();
                estRentGas += rd;
                codeNode.setLRPTime(this.getRefTimeStamp());
                progRes.addAccessedNode(cKey, codeNode);
            }       
            // storage root node
            ByteArrayWrapper srKey = repository.getStorageRootKey(addr);
            if (!progRes.getAccessedNodes().containsKey(srKey)){
                Uint24 srLen = repository.getStorageRootValueLength(addr);
                long srLrpt = repository.getStorageRootLRPTime(addr);
                RentData srNode = new RentData(srLen, srLrpt);
                // compute rent and update estRent as needed
                srNode.setRentDue(this.getRefTimeStamp(), false);  // storage root value is never modified
                rd = srNode.getRentDue();
                estRentGas += rd;
                srNode.setLRPTime(this.getRefTimeStamp());
                progRes.addAccessedNode(srKey, srNode);
            }
        }
    }

    // Similar to accessednodes adder. Different HashMap, 6 months timestamp, rent computation, no check for prepaid rent
    // also, it's more likely that created nodes will be in a cached repository, e.g. cachetrack in Tx execution 
    public void createdNodeAdder(RskAddress addr, Repository repository, ProgramResult progRes){
        long advTS = this.getRefTimeStamp() + GasCost.SIX_MONTHS; //advanced time stamp time.now + 6 months
        long rd = 0; // rent due init 0
        ByteArrayWrapper accKey = repository.getAccountNodeKey(addr);
        // if the node is not in the map, add the rent owed to current estimate
        if (!progRes.getCreatedNodes().containsKey(accKey)){
            Uint24 vLen = repository.getAccountNodeValueLength(addr);
            RentData accNode = new RentData(vLen, advTS);
            // compute the rent due
            accNode.setSixMonthsRent();
            rd = accNode.getRentDue();
            estRentGas += rd;
            // add to hashmap (internally this is a putIfAbsent)
            progRes.addCreatedNode(accKey, accNode);
        }
        // if this is a contract then add info for storage root and code
        if (repository.isContract(addr)) {
            // code node
            ByteArrayWrapper cKey = repository.getCodeNodeKey(addr);
            if (!progRes.getCreatedNodes().containsKey(cKey)){
                Uint24 cLen = repository.getCodeNodeLength(addr);
                RentData codeNode = new RentData(cLen, advTS);
                // compute rent and update estRent as needed
                codeNode.setSixMonthsRent();
                rd = codeNode.getRentDue();
                estRentGas += rd;
                progRes.addCreatedNode(cKey, codeNode);
            }       
            // storage root node
            ByteArrayWrapper srKey = repository.getStorageRootKey(addr);
            if (!progRes.getCreatedNodes().containsKey(srKey)){
                Uint24 srLen = repository.getStorageRootValueLength(addr);
                RentData srNode = new RentData(srLen, advTS);
                // compute rent and update estRent as needed
                srNode.setSixMonthsRent();
                rd = srNode.getRentDue();                
                estRentGas += rd;
                progRes.addCreatedNode(srKey, srNode);
            }
        }
    }


}

