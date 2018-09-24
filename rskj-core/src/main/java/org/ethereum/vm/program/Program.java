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

package org.ethereum.vm.program;

import co.rsk.config.VmConfig;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.peg.Bridge;
import co.rsk.remasc.RemascContract;
import co.rsk.vm.BitSet;
import com.google.common.annotations.VisibleForTesting;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.BlockchainConfig;
import org.ethereum.config.Constants;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.FastByteComparisons;
import org.ethereum.vm.*;
import org.ethereum.vm.MessageCall.MsgType;
import org.ethereum.vm.PrecompiledContracts.PrecompiledContract;
import org.ethereum.vm.program.invoke.ProgramInvoke;
import org.ethereum.vm.program.invoke.ProgramInvokeFactory;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.ethereum.vm.program.listener.CompositeProgramListener;
import org.ethereum.vm.program.listener.ProgramListenerAware;
import org.ethereum.vm.trace.ProgramTrace;
import org.ethereum.vm.trace.ProgramTraceListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.*;

import static java.lang.String.format;
import static org.apache.commons.lang3.ArrayUtils.*;
import static org.ethereum.util.BIUtil.*;
import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

/**
 * @author Roman Mandeleil
 * @since 01.06.2014
 */
public class Program {
    // These logs should never be in Info mode in production
    private static final Logger logger = LoggerFactory.getLogger("VM");
    private static final Logger gasLogger = LoggerFactory.getLogger("gas");

    /**
     * This attribute defines the number of recursive calls allowed in the EVM
     * Note: For the JVM to reach this level without a StackOverflow exception,
     * ethereumj may need to be started with a JVM argument to increase
     * the stack size. For example: -Xss10m
     */
    private static final int MAX_DEPTH = 1024;

    // MAX_GAS is 2^62-1. It is less than Long.MAX_VALUE (half) to
    // give som gap for small additions and skip checking for overflows
    // after each addition (instead, just check at the end).
    public static final long MAX_GAS = 0x3fffffffffffffffL;

    public static final long MAX_MEMORY = (1<<30);

    //Max size for stack checks
    private static final int MAX_STACKSIZE = 1024;

    private final BlockchainConfig blockchainConfig;
    private final Transaction transaction;

    private final ProgramInvoke invoke;
    private final ProgramInvokeFactory programInvokeFactory = new ProgramInvokeFactoryImpl();

    private ProgramOutListener listener;
    private final ProgramTraceListener traceListener;
    private final CompositeProgramListener programListener = new CompositeProgramListener();

    private final Stack stack;
    private final Memory memory;
    private final Storage storage;
    private byte[] returnDataBuffer;

    private final ProgramResult result = new ProgramResult();
    private final ProgramTrace trace;

    private final byte[] ops;
    private int pc;
    private byte lastOp;
    private byte previouslyExecutedOp;
    private boolean stopped;
    private byte exeVersion;    // currently limited to 0..127
    private byte scriptVersion; // currently limited to 0..127
    private int startAddr;

    private BitSet jumpdestSet;
    /**********************************************************************************************************
     * About DataWord Pool:
     *---------------------------------------------------------------------------------------------------------
     * Preliminaries:
     * (source: http://programmers.stackexchange.com/questions/149563/should-we-avoid-object-creation-in-java)
     *
     * There is a misconception that creating many small short lived objects causes the JVM to pause
     * for long periods of time, this is now false as well. Current GC algorithms are actually optimized
     * for creating many many small objects that are short lived, that is basically the 99% heuristic
     * for Java objects in every program. Object Pooling will actually make the JVM preform worse in most
     * cases.
     * The modern GC algorithms don't have this problem because they don't deallocate on a schedule, they
     * deallocate when free memory is needed in a certain generation.
     *
     * SDL analysis:
     *
     * THIS IS NOT THE CASE for an application that creates millions of objects per second (as the VM can do).
     *
     * Here are the results of runs of the VMPerformaceTest.testFibonacciLongTime() that show a mixed result:
     *----------------------------------------------------------------
     * CASE 1: HIGH MEMORY USE / useDataWordPool =  false
     * Creating 10000000 linked  objects..
     * Program.useDataWordPool =  false
     * Time elapsed [ms]: 28969 [s]:28
     * RealTime elapsed [ms]: 39626 [s]:39
     * GCTime elapsed [ms]: 10372 [s]:10
     * Instructions executed: : 170400032
     *----------------------------------------------------------------
     * CASE 2: HIGH MEMORY USE / useDataWordPool =  true
     * Creating 10000000 linked  objects..
     * Program.useDataWordPool =  true
     * Time elapsed [ms]: 35724 [s]:35
     * RealTime elapsed [ms]: 35783 [s]:35
     * GCTime elapsed [ms]: 0 [s]:0
     * Instructions executed: : 170400032
     *----------------------------------------------------------------
     * CASE 3: VERY LOW MEMORY USE / useDataWordPool =  false
     * Creating 0 linked  objects..
     * Program.useDataWordPool =  false
     * Time elapsed [ms]: 28516 [s]:28
     * RealTime elapsed [ms]: 29287 [s]:29
     * GCTime elapsed [ms]: 291 [s]:0
     * Instructions executed: : 170400032
     *----------------------------------------------------------------
     * If we compare the cases where memory is full of objects (1 and 2), using the memory pool resulted
     * in 35 seconds of processing (RealTime) while not using the pool resulted in 38 seconds (realTime).
     * Using useDataWordPool makes the VM go 8% faster.
     *
     * In case 2 the time dedicated to GC was actually zero.
     *
     * In case 3, there was no use of memory (apart from the VM itself). In this case using the pool took takes also 35
     * seconds (actual run not shown), but not using the pool takes only 29 seconds (RealTime). Therefore not using the
     * pool makes the VM faster by 17%.
     *
     * However the speedup the DataWord pool provides depends in the application that is run (the real full-node).
     * Garbage collection time depends on the number of live object pointers. In a test-case that number
     * is low, therefore garbage collecting is fast. The full-node stores in memory a huge amount of interrelated
     * objects (such as the Trie). That increases the GC time. To determine if dataWordPool should be activated by
     * default or disabled by default , additional test cases involving a real full-node with a large worldstate must be
     * performed. Until that moment, dataWordPool is enabled by setting useDataWordPool=true
     *
     *******************************************************************************************************************/
    private final java.util.Stack<DataWord> dataWordPool;

    private static Boolean useDataWordPool = true;

    private final VmConfig config;
    private final PrecompiledContracts precompiledContracts;

    private boolean isLogEnabled;
    private boolean isGasLogEnabled;

    private RskAddress rskOwnerAddress;

    public Program(
            VmConfig config,
            PrecompiledContracts precompiledContracts,
            BlockchainConfig blockchainConfig,
            byte[] ops,
            ProgramInvoke programInvoke,
            Transaction transaction) {
        this.config = config;
        this.precompiledContracts = precompiledContracts;
        this.blockchainConfig = blockchainConfig;
        this.transaction = transaction;
        isLogEnabled = logger.isInfoEnabled();
        isGasLogEnabled = gasLogger.isInfoEnabled();

        if (isLogEnabled ) {
            logger.warn("WARNING! VM logging is enabled. This will make the VM 200 times slower. Do not use in production.");
        }

        if (isGasLogEnabled) {
            gasLogger.warn("WARNING! Gas logging is enabled. This will the make VM 200 times slower. Do not use in production.");
        }

        this.invoke = programInvoke;

        this.ops = nullToEmpty(ops);

        this.memory = setupProgramListener(new Memory());
        this.stack = setupProgramListener(new Stack());
        this.stack.ensureCapacity(1024); // faster?
        this.storage = setupProgramListener(new Storage(programInvoke));
        this.trace = new ProgramTrace(config, programInvoke);

        if (useDataWordPool) {
            this.dataWordPool = new java.util.Stack<>();
            this.dataWordPool.ensureCapacity(1024); // faster?
        } else {
            this.dataWordPool = null;
        }

        precompile();
        traceListener = new ProgramTraceListener(config);
    }

    public static void setUseDataWordPool(Boolean value) {
        useDataWordPool = value;
    }

    public static Boolean getUseDataWordPool() {
        return useDataWordPool;
    }

    public int getCallDeep() {
        return invoke.getCallDeep();
    }



    private InternalTransaction addInternalTx(byte[] nonce, DataWord gasLimit, RskAddress senderAddress, RskAddress receiveAddress,
                                              Coin value, byte[] data, String note) {
        if (transaction == null) {
            return null;
        }

        byte[] senderNonce = isEmpty(nonce) ? getStorage().getNonce(senderAddress).toByteArray() : nonce;

        return getResult().addInternalTransaction(
                transaction.getHash().getBytes(),
                getCallDeep(),
                senderNonce,
                getGasPrice(),
                gasLimit,
                senderAddress.getBytes(),
                receiveAddress.getBytes(),
                value.getBytes(),
                data,
                note);
    }

    private <T extends ProgramListenerAware> T setupProgramListener(T traceListenerAware) {
        if (programListener.isEmpty()) {
            programListener.addListener(traceListener);
        }

        traceListenerAware.setTraceListener(traceListener);
        return traceListenerAware;
    }

    public byte getOp(int pc) {
        return (getLength(ops) <= pc) ? 0 : ops[pc];
    }

    public byte getCurrentOp() {
        return isEmpty(ops) ? 0 : ops[pc];
    }

    /**
     * Last Op can only be set publicly (no getLastOp method), is used for logging.
     */
    public void setLastOp(byte op) {
        this.lastOp = op;
    }

    /**
     * Should be set only after the OP is fully executed.
     */
    public void setPreviouslyExecutedOp(byte op) {
        this.previouslyExecutedOp = op;
    }

    /**
     * Returns the last fully executed OP.
     */
    public byte getPreviouslyExecutedOp() {
        return this.previouslyExecutedOp;
    }

    private DataWord getNewDataWordFast() {
        if (dataWordPool==null) {
            return new DataWord();
        }
        if (dataWordPool.empty()) {
            return new DataWord();
        } else {
            return dataWordPool.pop();
        }
    }

    public void stackPush(byte[] data) {
        DataWord dw=getNewDataWordFast();
        dw.assign(data);
        stackPush(dw);
    }

    private void stackPushZero() {
        DataWord dw=getNewDataWordFast();
        dw.zero();
        stackPush(dw);
    }

    private void stackPushOne() {
        DataWord stackWord=getNewDataWordFast();
        stackWord.assignData(DataWord.ONE.getData());
        stackPush(stackWord);
    }

    private void stackClear(){
        if (dataWordPool==null) {
            stack.clear();
            return;
        }

        while (!stack.isEmpty()) {
            disposeWord(stack.pop());
        }

    }

    public DataWord newDataWord(byte[] data) {
        DataWord dw=getNewDataWordFast();
        dw.assignData(data);
        return dw;
    }
    public DataWord newDataWord(int  v) {
        DataWord dw=getNewDataWordFast();
        dw.assign(v);
        return dw;
    }

    public DataWord newDataWord(long  v) {
        DataWord dw=getNewDataWordFast();
        dw.assign(v);
        return dw;
    }
    public DataWord newDataWord(DataWord idw) {
        DataWord dw=getNewDataWordFast();
        dw.assignData(idw.getData());
        return dw;
    }

    public DataWord newEmptyDataWord() {
        DataWord dw=getNewDataWordFast();
        dw.zero();
        return dw;
    }

    public void stackPush(DataWord stackWord) {
        verifyStackOverflow(0, 1); //Sanity Check
        stack.push(stackWord);
    }

    public void disposeWord(DataWord dw) {
        if (dataWordPool == null) {
            return ;
        }

        if (dw == DataWord.ZERO || dw == DataWord.ONE) {
            throw new IllegalArgumentException("Can't dispose a global DataWord");
        }

        // If there are enough cached values, just really dispose
        if (dataWordPool.size() < 1024) {
            dataWordPool.push(dw);
        }
    }

    public Stack getStack() {
        return this.stack;
    }

    public int getPC() {
        return pc;
    }

    public void setPC(DataWord pc) {
        this.setPC(pc.intValue());
    }

    public void setPC(int pc) {
        this.pc = pc;

        if (this.pc >= ops.length) {
            stop();
        }
    }

    public boolean isStopped() {
        return stopped;
    }

    public void stop() {
        stopped = true;
    }

    public void setHReturn(byte[] buff) {
        getResult().setHReturn(buff);
    }

    public void step() {
        setPC(pc + 1);
    }


    public byte[] byteSweep(int n) {

        if (pc + n > ops.length) {
            stop();
        }

        byte[] data = Arrays.copyOfRange(ops, pc, pc + n);
        pc += n;
        if (pc >= ops.length) {
            stop();
        }

        return data;
    }

    public DataWord sweepGetDataWord(int n) {
          if (pc + n > ops.length) {
              stop();
              // In this case partial data is copied. At least Ethereumj does this
              // Asummes LSBs are zero. assignDataRange undestands this semantics.
          }

        DataWord dw = getNewDataWordFast();
        dw.assignDataRange(ops, pc, n);
        pc += n;
        if (pc >= ops.length) {
            stop();
        }

        return dw;
    }

    public DataWord stackPop() {
        return stack.pop();
    }

    /**
     * Verifies that the stack is at least <code>stackSize</code>
     *
     * @param stackSize int
     * @throws StackTooSmallException If the stack is
     *                                smaller than <code>stackSize</code>
     */
    public void verifyStackSize(int stackSize) {
        if (stackSize < 0 || stack.size() < stackSize) {
            throw ExceptionHelper.tooSmallStack(stackSize, stack.size());
        }
    }

    public void verifyStackOverflow(int argsReqs, int returnReqs) {
        if ((stack.size() - argsReqs + returnReqs) > MAX_STACKSIZE) {
            throw new StackTooLargeException("Expected: overflow " + MAX_STACKSIZE + " elements stack limit");
        }
    }

    public int getMemSize() {
        return memory.size();
    }

    public void memorySave(DataWord addrB, DataWord value) {
        //
        memory.write(addrB.intValue(), value.getData(), value.getData().length, false);
    }

    private void memorySaveLimited(int addr, byte[] data, int dataSize) {
        memory.write(addr, data, dataSize, true);
    }

    public void memorySave(int addr, byte[] value) {
        memory.write(addr, value, value.length, false);
    }

    public void memoryExpand(DataWord outDataOffs, DataWord outDataSize) {
        if (!outDataSize.isZero()) {
            memory.extend(outDataOffs.intValue(), outDataSize.intValue());
        }
    }

    /**
     * Allocates a piece of memory and stores value at given offset address
     *
     * @param addr      is the offset address
     * @param allocSize size of memory needed to write
     * @param value     the data to write to memory
     */
    public void memorySave(int addr, int allocSize, byte[] value) {
        memory.extendAndWrite(addr, allocSize, value);
    }


    public DataWord memoryLoad(DataWord addr) {
        return memory.readWord(addr.intValue());
    }

    public DataWord memoryLoad(int address) {
        return memory.readWord(address);
    }

    public byte[] memoryChunk(int offset, int size) {
        return memory.read(offset, size);
    }

    /**
     * Allocates extra memory in the program for
     * a specified size, calculated from a given offset
     *
     * @param offset the memory address offset
     * @param size   the number of bytes to allocate
     */
    public void allocateMemory(int offset, int size) {
        memory.extend(offset, size);
    }

    public void suicide(DataWord obtainerAddress) {

        RskAddress owner = getOwnerRskAddress();
        Coin balance = getStorage().getBalance(owner);

        if (!balance.equals(Coin.ZERO)) {
            RskAddress obtainer = new RskAddress(obtainerAddress);

            logger.info("Transfer to: [{}] heritage: [{}]", obtainer, balance);

            addInternalTx(null, null, owner, obtainer, balance, null, "suicide");

            if (FastByteComparisons.compareTo(owner.getBytes(), 0, 20, obtainer.getBytes(), 0, 20) == 0) {
                // if owner == obtainer just zeroing account according to Yellow Paper
                getStorage().addBalance(owner, balance.negate());
            } else {
                getStorage().transfer(owner, obtainer, balance);
            }
        }
        // In any case, remove the account
        getResult().addDeleteAccount(this.getOwnerAddress());

    }

    public void send(DataWord destAddress, Coin amount) {

        RskAddress owner = getOwnerRskAddress();
        RskAddress dest = new RskAddress(destAddress);
        Coin balance = getStorage().getBalance(owner);

        if (isNotCovers(balance, amount)) {
            return; // does not do anything.
        }

        if (isLogEnabled) {
            logger.info("Transfer to: [{}] amount: [{}]",
                    dest,
                    amount);
        }

        addInternalTx(null, null, owner, dest, amount, null, "send");

        getStorage().transfer(owner, dest, amount);
    }

    public Repository getStorage() {
        return this.storage;
    }

    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    public void createContract(DataWord value, DataWord memStart, DataWord memSize) {

        if (getCallDeep() == MAX_DEPTH) {
            stackPushZero();
            return;
        }

        RskAddress senderAddress = getOwnerRskAddress();
        Coin endowment = new Coin(value.getData());
        if (isNotCovers(getStorage().getBalance(senderAddress), endowment)) {
            stackPushZero();
            return;
        }

        // [1] FETCH THE CODE FROM THE MEMORY
        byte[] programCode = memoryChunk(memStart.intValue(), memSize.intValue());

        if (isLogEnabled) {
            logger.info("creating a new contract inside contract run: [{}]", senderAddress);
        }

        //  actual gas subtract
        long gasLimit = getRemainingGas();
        spendGas(gasLimit, "internal call");

        // [2] CREATE THE CONTRACT ADDRESS
        byte[] nonce = getStorage().getNonce(senderAddress).toByteArray();
        byte[] newAddressBytes = HashUtil.calcNewAddr(getOwnerAddress().getLast20Bytes(), nonce);
        RskAddress newAddress = new RskAddress(newAddressBytes);

        if (byTestingSuite()) {
            // This keeps track of the contracts created for a test
            getResult().addCallCreate(programCode, EMPTY_BYTE_ARRAY,
                    gasLimit,
                    value.getNoLeadZeroesData());
        }

        // [3] UPDATE THE NONCE
        // (THIS STAGE IS NOT REVERTED BY ANY EXCEPTION)
        if (!byTestingSuite()) {
            getStorage().increaseNonce(senderAddress);
        }
        // Start tracking repository changes for the constructor of the contract
        Repository track = getStorage().startTracking();

        //In case of hashing collisions, check for any balance before createAccount()
        if (track.isExist(newAddress)) {
            Coin oldBalance = track.getBalance(newAddress);
            track.createAccount(newAddress);
            track.addBalance(newAddress, oldBalance);
        } else {
            track.createAccount(newAddress);

        }
        track.setupContract(newAddress);

        // [4] TRANSFER THE BALANCE
        track.addBalance(senderAddress, endowment.negate());
        Coin newBalance = Coin.ZERO;
        if (!byTestingSuite()) {
            newBalance = track.addBalance(newAddress, endowment);
        }


        // [5] COOK THE INVOKE AND EXECUTE
        InternalTransaction internalTx = addInternalTx(nonce, getGasLimit(), senderAddress, RskAddress.nullAddress(), endowment, programCode, "create");
        ProgramInvoke programInvoke = programInvokeFactory.createProgramInvoke(
                this, new DataWord(newAddressBytes), getOwnerAddress(), value, gasLimit,
                newBalance, null, track, this.invoke.getBlockStore(), false, byTestingSuite());

        ProgramResult programResult = ProgramResult.empty();
        returnDataBuffer = null; // reset return buffer right before the call
        if (isNotEmpty(programCode)) {
            VM vm = new VM(config, precompiledContracts);
            Program program = new Program(config, precompiledContracts, blockchainConfig, programCode, programInvoke, internalTx);
            vm.play(program);
            programResult = program.getResult();
        }

        if (programResult.getException() != null || programResult.isRevert()) {
            if (isLogEnabled) {
                logger.debug("contract run halted by Exception: contract: [{}], exception: [{}]",
                      newAddress,
                      programResult.getException());
            }

            if (internalTx == null) {
                throw new NullPointerException();
            }

            internalTx.reject();
            programResult.rejectInternalTransactions();
            programResult.rejectLogInfos();

            track.rollback();
            stackPushZero();
            if (programResult.getException() != null) {
                return;
            } else {
                returnDataBuffer = result.getHReturn();
            }
        }
        else {
            // 4. CREATE THE CONTRACT OUT OF RETURN
            byte[] code = programResult.getHReturn();
            int codeLength = getLength(code);

            long storageCost = (long) codeLength * GasCost.CREATE_DATA;
            long afterSpend = programInvoke.getGas() - storageCost - programResult.getGasUsed();
            if (afterSpend < 0) {
                programResult.setException(
                        ExceptionHelper.notEnoughSpendingGas(
                                "No gas to return just created contract",
                                storageCost,
                                this));
            } else if (codeLength > Constants.getMaxContractSize()) {
                programResult.setException(
                        ExceptionHelper.tooLargeContractSize(
                                Constants.getMaxContractSize(),
                                codeLength));
            } else {
                programResult.spendGas(storageCost);
                track.saveCode(newAddress, code);
            }

            track.commit();


            getResult().addDeleteAccounts(programResult.getDeleteAccounts());
            getResult().addLogInfos(programResult.getLogInfoList());

            // IN SUCCESS PUSH THE ADDRESS INTO THE STACK
            stackPush(new DataWord(newAddressBytes));
        }

        // 5. REFUND THE REMAIN GAS
        long refundGas = gasLimit - programResult.getGasUsed();
        if (refundGas > 0) {
            refundGas(refundGas, "remain gas from the internal call");
            if (isGasLogEnabled) {
                gasLogger.info("The remaining gas is refunded, account: [{}], gas: [{}] ",
                        Hex.toHexString(getOwnerAddress().getLast20Bytes()),
                        refundGas);
            }
        }
    }

    public static long limitToMaxLong(DataWord gas) {
        return gas.longValueSafe();

    }

    public static long limitToMaxGas(DataWord gas) {
        long r =gas.longValueSafe();
        if (r>MAX_GAS) {
            return MAX_GAS;
        }
        return r;

    }

    public static long limitToMaxGas(BigInteger gas) {
        long r =limitToMaxLong(gas);
        if (r>MAX_GAS) {
            return MAX_GAS;
        }
        return r;
    }

    public static long limitToMaxLong(BigInteger gas) {
        try {
            long r = gas.longValueExact();
            if (r<0)  // check if this can happen
            {
                return Long.MAX_VALUE;
            }
            return r;
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    public static long multiplyLimitToMaxLong(long a,long b) {
        long d;

        try {
            d = Math.multiplyExact(a, b);
        } catch (ArithmeticException e) {
            d = Long.MAX_VALUE;
        }
        return d;
    }

    public static long addLimitToMaxLong(long a,long b) {
        long d;
        try {
            d = Math.addExact(a,b);
        } catch (ArithmeticException e) {
            d= Long.MAX_VALUE;
        }
        return d;
    }

    /**
     * That method is for internal code invocations
     * <p/>
     * - Normal calls invoke a specified contract which updates itself
     * - Stateless calls invoke code from another contract, within the context of the caller
     *
     * @param msg is the message call object
     */
    public void callToAddress(MessageCall msg) {

        if (getCallDeep() == MAX_DEPTH) {
            stackPushZero();
            refundGas(msg.getGas().longValue(), " call deep limit reach");
            return;
        }

        byte[] data = memoryChunk(msg.getInDataOffs().intValue(), msg.getInDataSize().intValue());

        // FETCH THE SAVED STORAGE
        RskAddress codeAddress = new RskAddress(msg.getCodeAddress());
        RskAddress senderAddress = getOwnerRskAddress();
        RskAddress contextAddress = msg.getType().isStateless() ? senderAddress : codeAddress;

        if (isLogEnabled) {
            logger.info("{} for existing contract: address: [{}], outDataOffs: [{}], outDataSize: [{}]  ", msg.getType().name(),
                    contextAddress, msg.getOutDataOffs().longValue(), msg.getOutDataSize().longValue());
        }

        Repository track = getStorage().startTracking();

        // 2.1 PERFORM THE VALUE (endowment) PART
        Coin endowment = new Coin(msg.getEndowment().getData());
        Coin senderBalance = track.getBalance(senderAddress);
        if (isNotCovers(senderBalance, endowment)) {
            stackPushZero();
            refundGas(msg.getGas().longValue(), "refund gas from message call");
            return;
        }

        // FETCH THE CODE
        byte[] programCode = getStorage().isExist(codeAddress) ? getStorage().getCode(codeAddress) : EMPTY_BYTE_ARRAY;
        // programCode  can be null

        // Always first remove funds from sender
        track.addBalance(senderAddress, endowment.negate());

        Coin contextBalance;

        if (byTestingSuite()) {
            // This keeps track of the calls created for a test
            getResult().addCallCreate(data, contextAddress.getBytes(),
                        msg.getGas().longValueSafe(),
                    msg.getEndowment().getNoLeadZeroesData());
            return;
        }

        contextBalance = track.addBalance(contextAddress, endowment);

        // CREATE CALL INTERNAL TRANSACTION
        InternalTransaction internalTx = addInternalTx(null, getGasLimit(), senderAddress, contextAddress, endowment, programCode, "call");

        boolean callResult;

        if (isNotEmpty(programCode)) {
            callResult = executeCode(msg, contextAddress, contextBalance, internalTx, track, programCode, senderAddress, data);
        }
        else {
            track.commit();
            callResult = true;
            refundGas(msg.getGas().longValue(), "remaining gas from the internal call");
        }

        // 4. THE FLAG OF SUCCESS IS ONE PUSHED INTO THE STACK
        if (callResult) {
            stackPushOne();
        }
        else {
            stackPushZero();
        }
    }

    private boolean executeCode(
            MessageCall msg,
            RskAddress contextAddress,
            Coin contextBalance,
            InternalTransaction internalTx,
            Repository track,
            byte[] programCode,
            RskAddress senderAddress,
            byte[] data) {

        returnDataBuffer = null; // reset return buffer right before the call
        ProgramResult childResult = null;

        ProgramInvoke programInvoke = programInvokeFactory.createProgramInvoke(
                this, new DataWord(contextAddress.getBytes()),
                msg.getType() == MsgType.DELEGATECALL ? getCallerAddress() : getOwnerAddress(),
                msg.getType() == MsgType.DELEGATECALL ? getCallValue() : msg.getEndowment(),
                limitToMaxLong(msg.getGas()), contextBalance, data, track, this.invoke.getBlockStore(),
                msg.getType() == MsgType.STATICCALL || isStaticCall(), byTestingSuite());

        VM vm = new VM(config, precompiledContracts);
        Program program = new Program(config, precompiledContracts, blockchainConfig, programCode, programInvoke, internalTx);
        vm.play(program);
        childResult  = program.getResult();

        getTrace().merge(program.getTrace());
        getResult().merge(childResult);

        boolean childCallSuccessful = true;

        if (childResult.getException() != null || childResult.isRevert()) {
            if (isGasLogEnabled) {
                gasLogger.debug("contract run halted by Exception: contract: [{}], exception: [{}]",
                    contextAddress,
                    childResult .getException());
            }

            internalTx.reject();
            childResult.rejectInternalTransactions();
            childResult.rejectLogInfos();

            track.rollback();
            // when there's an exception we skip applying results and refunding gas,
            // and we only do that when the call is successful or there's a REVERT operation.
            if (childResult.getException() != null) {
                return false;
            }

            childCallSuccessful = false;
        } else {
            // 4. THE FLAG OF SUCCESS IS ONE PUSHED INTO THE STACK
            track.commit();
        }


        // 3. APPLY RESULTS: childResult.getHReturn() into out_memory allocated
        byte[] buffer = childResult.getHReturn();
        int offset = msg.getOutDataOffs().intValue();
        int size = msg.getOutDataSize().intValue();

        memorySaveLimited(offset, buffer, size);

        returnDataBuffer = buffer;

        // 5. REFUND THE REMAIN GAS
        BigInteger refundGas = msg.getGas().value().subtract(toBI(childResult.getGasUsed()));
        if (isPositive(refundGas)) {
            // Since the original gas transferred was < Long.MAX_VALUE then the refund
            // also fits in a long.
            // SUICIDE refunds 24.000 and SSTORE clear refunds 15.000 gas.
            // The accumulated refund can not exceed half the gas used
            // for the current context (i.e. the initial call)
            // Therefore, the regundGas also fits in a long.
            refundGas(refundGas.longValue(), "remaining gas from the internal call");
            if (isGasLogEnabled) {
                gasLogger.info("The remaining gas refunded, account: [{}], gas: [{}] ",
                        senderAddress,
                        refundGas.toString());
            }
        }
        return childCallSuccessful;
    }

    public void spendGas(long gasValue, String cause) {
        if (isGasLogEnabled) {
            gasLogger.info("[{}] Spent for cause: [{}], gas: [{}]", invoke.hashCode(), cause, gasValue);
        }

        if (getRemainingGas()  < gasValue) {
            throw ExceptionHelper.notEnoughSpendingGas(cause, gasValue, this);
        }
        getResult().spendGas(gasValue);
    }

    public void restart() {
        setPC(startAddr);
        stackClear();
        clearUsedGas();
        stopped=false;
    }

    private void clearUsedGas() {
        getResult().clearUsedGas();
    }

    public void spendAllGas() {
        spendGas(getRemainingGas(), "Spending all remaining");
    }

    private void refundGas(long gasValue, String cause) {
        if (isGasLogEnabled) {
            gasLogger.info("[{}] Refund for cause: [{}], gas: [{}]", invoke.hashCode(), cause, gasValue);
        }
        getResult().refundGas(gasValue);
    }

    public void futureRefundGas(long gasValue) {
        if (isLogEnabled) {
            logger.info("Future refund added: [{}]", gasValue);
        }
        getResult().addFutureRefund(gasValue);
    }

    public void resetFutureRefund() {
        getResult().resetFutureRefund();
    }

    public int replaceCode(byte[] newCode) {

        // In any case, remove the account
        getResult().addCodeChange(this.getOwnerAddress(), newCode);

        return 1; // in the future code size will be bounded.
    }

    public void storageSave(DataWord word1, DataWord word2) {
        storageSave(word1.getData(), word2.getData());
    }

    private void storageSave(byte[] key, byte[] val) {
        // DataWord constructor some times reference the passed byte[] instead
        // of making a copy.
        DataWord keyWord = new DataWord(key);
        DataWord valWord = new DataWord(val);

        // If DataWords will be reused, then we must clone them.
        if (useDataWordPool) {
            keyWord = keyWord.clone();
            valWord = valWord.clone();
        }

        getStorage().addStorageRow(getOwnerRskAddress(), keyWord, valWord);
    }

    private RskAddress getOwnerRskAddress() {
        if (rskOwnerAddress == null) {
            rskOwnerAddress = new RskAddress(getOwnerAddress());
        }

        return rskOwnerAddress;
    }

    public byte[] getCode() {
        return ops;
    }

    public byte[] getCodeHashAt(RskAddress addr) {
        return invoke.getRepository().getCodeHash(addr);
    }

    public int getCodeLengthAt(RskAddress addr) {
        return invoke.getRepository().getCodeLength(addr);
    }


    public int getCodeLengthAt(DataWord address) {
        return getCodeLengthAt(new RskAddress(address));

    }
    public byte[] getCodeAt(DataWord address) {
        return getCodeAt(new RskAddress(address));
    }

    private byte[] getCodeAt(RskAddress addr) {
        byte[] code = invoke.getRepository().getCode(addr);
        return nullToEmpty(code);
    }

    public DataWord getOwnerAddress() {
        return invoke.getOwnerAddress();
    }

    public DataWord getBlockHash(DataWord dw) {
        long blockIndex = dw.longValueSafe();
        // always returns positive, returns Integer.MAX_VALUE on overflows
        // block number would normally overflow int32 after ~1000 years (at 1 block every 10 seconds)
        // So int32 arithmetic is ok, but we use int64 anyway.
        return getBlockHash(blockIndex);
    }

    public DataWord getBlockHash(long index) {
       long bn = this.getNumber().longValue();
        if ((index <  bn) && (index >= Math.max(0, bn - 256))) {
            return new DataWord(this.invoke.getBlockStore().getBlockHashByNumber(index, getPrevHash().getData())).clone();
        } else {
            return DataWord.ZERO.clone();
        }
    }

    public DataWord getBalance(DataWord address) {
        Coin balance = getStorage().getBalance(new RskAddress(address));
        return new DataWord(balance.getBytes());
    }

    public DataWord getOriginAddress() {
        return invoke.getOriginAddress().clone();
    }

    public DataWord getCallerAddress() {
        return invoke.getCallerAddress().clone();
    }

    public DataWord getGasPrice() {
        return invoke.getMinGasPrice().clone();
    }

    public long getRemainingGas() {
        return invoke.getGas()- getResult().getGasUsed();
    }

    public DataWord getCallValue() {
            return invoke.getCallValue().clone();
    }

    public DataWord getDataSize() {
        return invoke.getDataSize().clone();
    }

    public DataWord getDataValue(DataWord index) {
        return invoke.getDataValue(index);
    }

    public byte[] getDataCopy(DataWord offset, DataWord length) {
        return invoke.getDataCopy(offset, length);
    }

    public DataWord storageLoad(DataWord key) {
        return getStorage().getStorageValue(getOwnerRskAddress(), key);
    }

    public DataWord getPrevHash() {
        return invoke.getPrevHash().clone();
    }

    public DataWord getCoinbase() {
        return invoke.getCoinbase().clone();
    }

    public DataWord getTimestamp() {
        return invoke.getTimestamp().clone();
    }

    public DataWord getNumber() {
        return invoke.getNumber().clone();
    }

    public DataWord getTransactionIndex() {
        return invoke.getTransactionIndex().clone();
    }

    public DataWord getDifficulty() {
        return invoke.getDifficulty().clone();
    }

    public DataWord getGasLimit() {
        return invoke.getGaslimit().clone();
    }

    public boolean isStaticCall() {
        return invoke.isStaticCall();
    }

    public ProgramResult getResult() {
        return result;
    }

    public void setRuntimeFailure(RuntimeException e) {
        getResult().setException(e);
    }

    public String memoryToString() {
        if (memory.size()>100000) {
            return "<Memory too long to show>";
        } else {
            return memory.toString();
        }
    }

    public void fullTrace() {

        if (logger.isTraceEnabled() || listener != null) {

            StringBuilder stackData = new StringBuilder();
            for (int i = 0; i < stack.size(); ++i) {
                stackData.append(" ").append(stack.get(i));
                if (i < stack.size() - 1) {
                    stackData.append("\n");
                }
            }

            if (stackData.length() > 0) {
                stackData.insert(0, "\n");
            }

            RskAddress ownerAddress = new RskAddress(getOwnerAddress());
            StringBuilder storageData = new StringBuilder();
            if (getStorage().isContract(ownerAddress)) {
                Iterator<DataWord> it = getStorage().getStorageKeys(ownerAddress);
                while (it.hasNext()) {
                    DataWord key = it.next();
                    storageData.append(" ").append(key).append(" -> ").
                            append(getStorage().getStorageValue(ownerAddress, key)).append('\n');
                }
                if (storageData.length() > 0) {
                    storageData.insert(0, "\n");
                }
            }

            StringBuilder memoryData = new StringBuilder();
            StringBuilder oneLine = new StringBuilder();
            if (memory.size() > 320) {
                memoryData.append("... Memory Folded.... ")
                        .append("(")
                        .append(memory.size())
                        .append(") bytes");
            }
            else {
                for (int i = 0; i < memory.size(); ++i) {

                    byte value = memory.readByte(i);
                    oneLine.append(ByteUtil.oneByteToHexString(value)).append(" ");

                    if ((i + 1) % 16 == 0) {
                        String tmp = format("[%4s]-[%4s]", Integer.toString(i - 15, 16),
                                Integer.toString(i, 16)).replace(" ", "0");
                        memoryData.append("").append(tmp).append(" ");
                        memoryData.append(oneLine);
                        if (i < memory.size()) {
                            memoryData.append("\n");
                        }
                        oneLine.setLength(0);
                    }
                }
            }
            if (memoryData.length() > 0) {
                memoryData.insert(0, "\n");
            }

            StringBuilder opsString = new StringBuilder();
            for (int i = 0; i < ops.length; ++i) {

                String tmpString = Integer.toString(ops[i] & 0xFF, 16);
                tmpString = tmpString.length() == 1 ? "0" + tmpString : tmpString;

                if (i != pc) {
                    opsString.append(tmpString);
                } else {
                    opsString.append(" >>").append(tmpString).append("");
                }

            }
            if (pc >= ops.length) {
                opsString.append(" >>");
            }
            if (opsString.length() > 0) {
                opsString.insert(0, "\n ");
            }

            logger.trace(" -- OPS --     {}", opsString);
            logger.trace(" -- STACK --   {}", stackData);
            logger.trace(" -- MEMORY --  {}", memoryData);
            logger.trace(" -- STORAGE -- {}\n", storageData);
            logger.trace("\n  Spent Gas: [{}]/[{}]\n  Left Gas:  [{}]\n",
                    getResult().getGasUsed(),
                    invoke.getGas(),
                    getRemainingGas());

            StringBuilder globalOutput = new StringBuilder("\n");
            if (stackData.length() > 0) {
                stackData.append("\n");
            }

            if (pc != 0) {
                globalOutput.append("[Op: ").append(OpCode.code(lastOp).name()).append("]\n");
            }

            globalOutput.append(" -- OPS --     ").append(opsString).append("\n");
            globalOutput.append(" -- STACK --   ").append(stackData).append("\n");
            globalOutput.append(" -- MEMORY --  ").append(memoryData).append("\n");
            globalOutput.append(" -- STORAGE -- ").append(storageData).append("\n");

            if (getResult().getHReturn() != null) {
                globalOutput.append("\n  HReturn: ").append(
                        Hex.toHexString(getResult().getHReturn()));
            }

            // sophisticated assumption that msg.data != codedata
            // means we are calling the contract not creating it
            byte[] txData = invoke.getDataCopy(DataWord.ZERO, getDataSize());
            if (!Arrays.equals(txData, ops)) {
                globalOutput.append("\n  msg.data: ").append(Hex.toHexString(txData));
            }
            globalOutput.append("\n\n  Spent Gas: ").append(getResult().getGasUsed());

            if (listener != null) {
                listener.output(globalOutput.toString());
            }
        }
    }

    public void saveOpTrace() {
        if (this.pc < ops.length) {
            trace.addOp(ops[pc], pc, getCallDeep(), getRemainingGas(), traceListener.resetActions());
        }
    }

    public static int getScriptVersionInCode(byte[] ops){
        if (ops.length >= 4) {
            OpCode op = OpCode.code(ops[0]);
            if ((op!=null) && op == OpCode.HEADER) {
                return ops[2];
            }
        }
        return 0;
    }

    public ProgramTrace getTrace() {
        return trace;
    }

    private int processAndSkipCodeHeader(int offset) {
        int ret = offset;
        if (ops.length >= 4) {
            OpCode op = OpCode.code(ops[0]);
            if ((op != null) && op == OpCode.HEADER) {
                // next byte is executable format version
                // header length in bytes
                int exe = ops[1] & 0xff;
                // limit to positive to prevent version 0xff < 0x00
                exeVersion = (byte) Math.min(exe, 127);

                // limit to positive to prevent version 0xff < 0x00
                int script = ops[2] & 0xff;
                scriptVersion = (byte) Math.min(script, 127);
                int extHeaderLen = ops[3] & 0xff;
                ret = offset + 4 + extHeaderLen;
                startAddr = ret;
                pc = ret;
            }
        }
        return ret;
    }

    private void precompile() {
        int i = 0;
        exeVersion = 0;
        scriptVersion = 0;
        startAddr = 0;
        pc = 0;
        i = processAndSkipCodeHeader(i);
        computeJumpDests(i);
    }

    private void computeJumpDests(int start) {
        if (jumpdestSet == null) {
            jumpdestSet = new BitSet(ops.length);
        }

        for (int i = start; i < ops.length; ++i) {
            OpCode op = OpCode.code(ops[i]);

            if (op == null) {
                continue;
            }

            if (op == OpCode.JUMPDEST) {
                jumpdestSet.set(i);
            }

            if (op.asInt() >= OpCode.PUSH1.asInt() && op.asInt() <= OpCode.PUSH32.asInt()) {
                i += op.asInt() - OpCode.PUSH1.asInt() + 1;
            }
        }
    }

    public DataWord getReturnDataBufferSize() {
        return new DataWord(getReturnDataBufferSizeI());
    }

    private int getReturnDataBufferSizeI() {
        return returnDataBuffer == null ? 0 : returnDataBuffer.length;
    }

    public Optional<byte[]> getReturnDataBufferData(DataWord off, DataWord size) {
        long endPosition = (long) off.intValueSafe() + size.intValueSafe();
        if (endPosition > getReturnDataBufferSizeI()) {
            return Optional.empty();
        }

        if (returnDataBuffer == null) {
            return Optional.of(new byte[0]);
        }

        byte[] copiedData = Arrays.copyOfRange(returnDataBuffer, off.intValueSafe(), Math.toIntExact(endPosition));
        return Optional.of(copiedData);
    }

    public BlockchainConfig getBlockchainConfig() {
        return blockchainConfig;
    }

    public void addListener(ProgramOutListener listener) {
        this.listener = listener;
    }

    public int verifyJumpDest(DataWord nextPC) {
        // This is painstankly slow
        if (nextPC.occupyMoreThan(4)) {
            throw ExceptionHelper.badJumpDestination(-1);
        }
        int ret = nextPC.intValue(); // could be negative
        if (ret < 0 || ret >= jumpdestSet.size() || !jumpdestSet.get(ret)) {
            throw ExceptionHelper.badJumpDestination(ret);
        }
        return ret;
    }

    public void callToPrecompiledAddress(MessageCall msg, PrecompiledContract contract) {

        if (getCallDeep() == MAX_DEPTH) {
            stackPushZero();
            this.refundGas(msg.getGas().longValue(), " call deep limit reach");
            return;
        }

        Repository track = getStorage().startTracking();

        RskAddress senderAddress = getOwnerRskAddress();
        RskAddress codeAddress = new RskAddress(msg.getCodeAddress());
        RskAddress contextAddress = msg.getType().isStateless() ? senderAddress : codeAddress;

        Coin endowment = new Coin(msg.getEndowment().getData());
        Coin senderBalance = track.getBalance(senderAddress);
        if (senderBalance.compareTo(endowment) < 0) {
            stackPushZero();
            this.refundGas(msg.getGas().longValue(), "refund gas from message call");
            return;
        }

        byte[] data = this.memoryChunk(msg.getInDataOffs().intValue(),
                msg.getInDataSize().intValue());

        // Charge for endowment - is not reversible by rollback
        track.transfer(senderAddress, contextAddress, new Coin(msg.getEndowment().getData()));

        if (byTestingSuite()) {
            // This keeps track of the calls created for a test
            this.getResult().addCallCreate(data,
                    codeAddress.getBytes(),
                    msg.getGas().longValueSafe(),
                    msg.getEndowment().getNoLeadZeroesData());

            stackPushOne();
            return;
        }

        // Special initialization for Bridge and Remasc contracts
        if (contract instanceof Bridge || contract instanceof RemascContract) {
            // CREATE CALL INTERNAL TRANSACTION
            InternalTransaction internalTx = addInternalTx(null, getGasLimit(), senderAddress, contextAddress, endowment, EMPTY_BYTE_ARRAY, "call");

            // Propagate the "local call" nature of the originating transaction down to the callee
            internalTx.setLocalCallTransaction(this.transaction.isLocalCallTransaction());

            Block executionBlock = new Block(getPrevHash().getData(), EMPTY_BYTE_ARRAY, getCoinbase().getLast20Bytes(), EMPTY_BYTE_ARRAY,
                getDifficulty().getData(), getNumber().longValue(), getGasLimit().getData(), 0, getTimestamp().longValue(),
                EMPTY_BYTE_ARRAY, EMPTY_BYTE_ARRAY, EMPTY_BYTE_ARRAY, new ArrayList<>(), new ArrayList<>(), null);

            contract.init(internalTx, executionBlock, track, this.invoke.getBlockStore(), null, null);
        }

        long requiredGas = contract.getGasForData(data);
        if (requiredGas > msg.getGas().longValue()) {

            this.refundGas(0, "call pre-compiled"); //matches cpp logic
            this.stackPushZero();
            track.rollback();
        } else {

            this.refundGas(msg.getGas().longValue() - requiredGas, "call pre-compiled");

            byte[] out = contract.execute(data);

            if (getBlockchainConfig().isRskip90()) {
                this.returnDataBuffer = out;
            }

            this.memorySave(msg.getOutDataOffs().intValue(), out);
            this.stackPushOne();
            track.commit();
        }
    }

    private boolean byTestingSuite() {
        return invoke.byTestingSuite();
    }

    public interface ProgramOutListener {
        void output(String out);
    }

    @SuppressWarnings("serial")
    public static class OutOfGasException extends RuntimeException {

        public OutOfGasException(String message, Object... args) {
            super(format(message, args));
        }
    }

    @SuppressWarnings("serial")
    public static class IllegalOperationException extends RuntimeException {

        public IllegalOperationException(String message, Object... args) {
            super(format(message, args));
        }
    }

    @SuppressWarnings("serial")
    public static class BadJumpDestinationException extends RuntimeException {

        public BadJumpDestinationException(String message, Object... args) {
            super(format(message, args));
        }
    }

    @SuppressWarnings("serial")
    public static class StackTooSmallException extends RuntimeException {

        public StackTooSmallException(String message, Object... args) {
            super(format(message, args));
        }
    }

    @SuppressWarnings("serial")
    public static class StaticCallModificationException extends RuntimeException {
        public StaticCallModificationException() {
            super("Attempt to call a state modifying opcode inside STATICCALL");
        }
    }

    public static class ExceptionHelper {

        private ExceptionHelper() { }

        public static StaticCallModificationException modificationException() {
            return new StaticCallModificationException();
        }

        public static OutOfGasException notEnoughOpGas(OpCode op, long opGas, long programGas) {
            return new OutOfGasException("Not enough gas for '%s' operation executing: opGas[%d], programGas[%d];", op, opGas, programGas);
        }

        public static OutOfGasException notEnoughOpGas(OpCode op, DataWord opGas, DataWord programGas) {
            return notEnoughOpGas(op, opGas.longValue(), programGas.longValue());
        }

        public static OutOfGasException notEnoughOpGas(OpCode op, BigInteger opGas, BigInteger programGas) {
            return notEnoughOpGas(op, opGas.longValue(), programGas.longValue());
        }

        public static OutOfGasException notEnoughSpendingGas(String cause, long gasValue, Program program) {
            return new OutOfGasException("Not enough gas for '%s' cause spending: invokeGas[%d], gas[%d], usedGas[%d];",
                    cause, program.invoke.getGas(), gasValue, program.getResult().getGasUsed());
        }

        public static OutOfGasException gasOverflow(BigInteger actualGas, BigInteger gasLimit) {
            return new OutOfGasException("Gas value overflow: actualGas[%d], gasLimit[%d];", actualGas.longValue(), gasLimit.longValue());
        }
        public static OutOfGasException gasOverflow(long actualGas, BigInteger gasLimit) {
            return new OutOfGasException("Gas value overflow: actualGas[%d], gasLimit[%d];", actualGas, gasLimit.longValue());
        }
        public static IllegalOperationException invalidOpCode(byte... opCode) {
            return new IllegalOperationException("Invalid operation code: opcode[%s];", Hex.toHexString(opCode, 0, 1));
        }

        public static BadJumpDestinationException badJumpDestination(int pc) {
            return new BadJumpDestinationException("Operation with pc isn't 'JUMPDEST': PC[%d];", pc);
        }

        public static StackTooSmallException tooSmallStack(int expectedSize, int actualSize) {
            return new StackTooSmallException("Expected stack size %d but actual %d;", expectedSize, actualSize);
        }

        public static RuntimeException tooLargeContractSize(int maxSize, int actualSize) {
            return new RuntimeException(format("Maximum contract size allowed %d but actual %d;", maxSize, actualSize));
        }
    }

    @SuppressWarnings("serial")
    public class StackTooLargeException extends RuntimeException {
        public StackTooLargeException(String message) {
            super(message);
        }
    }

    /**
     * used mostly for testing reasons
     */
    public byte[] getMemory() {
        return memory.read(0, memory.size());
    }

    /**
     * used mostly for testing reasons
     */
    public void initMem(byte[] data) {
        this.memory.write(0, data, data.length, false);
    }

    public byte getExeVersion() {
        return exeVersion;
    }
    public byte getScriptVersion() {
        return scriptVersion;
    }
    public int getStartAddr(){
        return startAddr;
    }

    @VisibleForTesting
    public BitSet getJumpdestSet() { return this.jumpdestSet; }
}
