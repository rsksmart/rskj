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

import co.rsk.peg.Bridge;
import co.rsk.remasc.RemascContract;
import co.rsk.vm.BitSet;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.AccountState;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.ContractDetails;
import org.ethereum.util.*;
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
import org.spongycastle.util.encoders.Hex;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.*;

import static java.lang.StrictMath.min;
import static java.lang.String.format;
import static java.math.BigInteger.ZERO;
import static org.apache.commons.lang3.ArrayUtils.*;
import static org.ethereum.util.BIUtil.*;
import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

/**
 * @author Roman Mandeleil
 * @since 01.06.2014
 */
public class Program {
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

    private Transaction transaction;

    private ProgramInvoke invoke;
    private ProgramInvokeFactory programInvokeFactory = new ProgramInvokeFactoryImpl();

    private ProgramOutListener listener;
    private ProgramTraceListener traceListener = new ProgramTraceListener();
    private CompositeProgramListener programListener = new CompositeProgramListener();

    private Stack stack;
    private Memory memory;
    private Storage storage;

    private ProgramResult result = new ProgramResult();
    private ProgramTrace trace = new ProgramTrace();

    private byte[] ops;
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
    private java.util.Stack<DataWord> dataWordPool;

    private static Boolean useDataWordPool = true;

    boolean isLogEnabled;
    boolean isGasLogEnabled;

    public Program(byte[] ops, ProgramInvoke programInvoke) {
        isLogEnabled = logger.isInfoEnabled();
        isGasLogEnabled =gasLogger.isInfoEnabled();

        this.invoke = programInvoke;

        this.ops = nullToEmpty(ops);

        this.memory = setupProgramListener(new Memory());
        this.stack = setupProgramListener(new Stack());
        this.stack.ensureCapacity(1024); // faster?
        this.storage = setupProgramListener(new Storage(programInvoke));
        this.trace = new ProgramTrace(programInvoke);

        if (useDataWordPool)
            this.dataWordPool= new java.util.Stack<DataWord>();
            else
            this.dataWordPool=null;

        if (dataWordPool!=null) {
            this.dataWordPool.ensureCapacity(1024); // faster?
        }

        precompile();
    }

    public Program(byte[] ops, ProgramInvoke programInvoke, Transaction transaction) {
        this(ops, programInvoke);
        this.transaction = transaction;
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



    private InternalTransaction addInternalTx(byte[] nonce, DataWord gasLimit, byte[] senderAddress, byte[] receiveAddress,
                                              BigInteger value, byte[] data, String note) {

        InternalTransaction result = null;
        if (transaction != null) {
            byte[] senderNonce = isEmpty(nonce) ? getStorage().getNonce(senderAddress).toByteArray() : nonce;

            result = getResult().addInternalTransaction(transaction.getHash(), getCallDeep(), senderNonce,
                    getGasPrice(), gasLimit, senderAddress, receiveAddress, value.toByteArray(), data, note);
        }

        return result;
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

    public DataWord getNewDataWordFast() {
        if (dataWordPool==null) {
            return new DataWord();
        }
        if (dataWordPool.empty())
            return new DataWord();
        else {
            return dataWordPool.pop();
        }
    }

    public void stackPush(byte[] data) {
        DataWord dw=getNewDataWordFast();
        dw.assign(data);
        stackPush(dw);
    }

    public void stackPushZero() {
        DataWord dw=getNewDataWordFast();
        dw.zero();
        stackPush(dw);
    }

    public void stackPushOne() {
        DataWord stackWord=getNewDataWordFast();
        stackWord.assignData(DataWord.ONE.getData());
        stackPush(stackWord);
    }

    public void stackClear(){
        if (dataWordPool==null) {
            stack.clear();
            return;
        }

        while (stack.size()>0) {
            disposeWord(stack.pop());
        }

    }

    public DataWord newDataWord(byte[] data) {
        DataWord dw=getNewDataWordFast();
        dw.assignData(data);;
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
        if (dataWordPool==null) {
            return ;
        }
        // If there are enough cached values, just really dispose
        if (dataWordPool.size()<1024)
            dataWordPool.push(dw);
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

        if (pc + n > ops.length)
            stop();

        byte[] data = Arrays.copyOfRange(ops, pc, pc + n);
        pc += n;
        if (pc >= ops.length) {
            stop();
        }

        return data;
    }

    public int getArgument() {
        return ops[pc] & 0xff;
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
        if (stack.size() < stackSize) {
            throw Program.Exception.tooSmallStack(stackSize, stack.size());
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

    public void memorySaveLimited(int addr, byte[] data, int dataSize) {
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

    public byte[] getOwnerAddressLast20Bytes() {
        // An opportunity to cache
        return getOwnerAddress().getLast20Bytes();
    }

    public void suicide(DataWord obtainerAddress) {

        byte[] owner = getOwnerAddressLast20Bytes();
        BigInteger balance = getStorage().getBalance(owner);

        if (!balance.equals(ZERO)) {
            byte[] obtainer = obtainerAddress.getLast20Bytes();

            if (isLogEnabled)
                logger.info("Transfer to: [{}] heritage: [{}]",
                        Hex.toHexString(obtainer),
                        balance);

            addInternalTx(null, null, owner, obtainer, balance, null, "suicide");

            if (FastByteComparisons.compareTo(owner, 0, 20, obtainer, 0, 20) == 0) {
                // if owner == obtainer just zeroing account according to Yellow Paper
                getStorage().addBalance(owner, balance.negate());
            } else {
                transfer(getStorage(), owner, obtainer, balance);
            }
        }
        // In any case, remove the account
        getResult().addDeleteAccount(this.getOwnerAddress());

    }

    public void send(DataWord destAddress,BigInteger amount) {

        byte[] owner = getOwnerAddressLast20Bytes();
        byte[] dest = destAddress.getLast20Bytes();
        BigInteger balance = getStorage().getBalance(owner);

        if (isNotCovers(balance, amount)) {
            return; // does not do anything.
        }

        if (isLogEnabled)
            logger.info("Transfer to: [{}] amount: [{}]",
                    Hex.toHexString(dest),
                    amount);

        addInternalTx(null, null, owner, dest, amount, null, "send");

        transfer(getStorage(), owner, dest, amount);
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

        byte[] senderAddress = this.getOwnerAddressLast20Bytes();
        BigInteger endowment = value.value();
        if (isNotCovers(getStorage().getBalance(senderAddress), endowment)) {
            stackPushZero();
            return;
        }

        // [1] FETCH THE CODE FROM THE MEMORY
        byte[] programCode = memoryChunk(memStart.intValue(), memSize.intValue());

        if (isLogEnabled)
            logger.info("creating a new contract inside contract run: [{}]", Hex.toHexString(senderAddress));

        //  actual gas subtract
        long gasLimit = getRemainingGas();
        spendGas(gasLimit, "internal call");

        // [2] CREATE THE CONTRACT ADDRESS
        byte[] nonce = getStorage().getNonce(senderAddress).toByteArray();
        byte[] newAddress = HashUtil.calcNewAddr(getOwnerAddressLast20Bytes(), nonce);

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

        Repository track = getStorage().startTracking();

        //In case of hashing collisions, check for any balance before createAccount()
        if (track.isExist(newAddress)) {
            BigInteger oldBalance = track.getBalance(newAddress);
            track.createAccount(newAddress);
            track.addBalance(newAddress, oldBalance);
        } else
            track.createAccount(newAddress);

        // [4] TRANSFER THE BALANCE
        track.addBalance(senderAddress, endowment.negate());
        BigInteger newBalance = ZERO;
        if (!byTestingSuite()) {
            newBalance = track.addBalance(newAddress, endowment);
        }


        // [5] COOK THE INVOKE AND EXECUTE
        InternalTransaction internalTx = addInternalTx(nonce, getGasLimit(), senderAddress, null, endowment, programCode, "create");
        ProgramInvoke programInvoke = programInvokeFactory.createProgramInvoke(
                this, new DataWord(newAddress), getOwnerAddress(), getCallValue(), gasLimit,
                newBalance, null, track, this.invoke.getBlockStore(), byTestingSuite());

        ProgramResult result = ProgramResult.empty();
        if (isNotEmpty(programCode)) {

            VM vm = new VM();
            Program program = new Program(programCode, programInvoke, internalTx);
            vm.play(program);
            result = program.getResult();

            getResult().merge(result);
        }

        // 4. CREATE THE CONTRACT OUT OF RETURN
        byte[] code = result.getHReturn();

        long storageCost = getLength(code) * GasCost.CREATE_DATA;
        long afterSpend = programInvoke.getGas() - storageCost - result.getGasUsed();
        if (afterSpend < 0) {
            result.setException(Program.Exception.notEnoughSpendingGas("No gas to return just created contract",
                        storageCost, this));
        } else {
            result.spendGas(storageCost);
            track.saveCode(newAddress, code);
        }

        if (result.getException() != null) {
            if (isLogEnabled)
              logger.debug("contract run halted by Exception: contract: [{}], exception: [{}]",
                    Hex.toHexString(newAddress),
                    result.getException());

            if (internalTx == null)
                throw new NullPointerException();

            internalTx.reject();
            result.rejectInternalTransactions();
            result.rejectLogInfos();

            track.rollback();
            stackPushZero();
            return;
        }

        track.commit();
        getResult().addDeleteAccounts(result.getDeleteAccounts());
        getResult().addLogInfos(result.getLogInfoList());

        // IN SUCCESS PUSH THE ADDRESS INTO THE STACK
        stackPush(new DataWord(newAddress));

        // 5. REFUND THE REMAIN GAS
        long refundGas = gasLimit - result.getGasUsed();
        if (refundGas > 0) {
            refundGas(refundGas, "remain gas from the internal call");
            if (isGasLogEnabled) {
                gasLogger.info("The remaining gas is refunded, account: [{}], gas: [{}] ",
                        Hex.toHexString(getOwnerAddressLast20Bytes()),
                        refundGas);
            }
        }
    }

    public static long limitToMaxLong(DataWord gas) {
        return gas.longValueSafe();

    }

    public static long limitToMaxGas(DataWord gas) {
        long r =gas.longValueSafe();
        if (r>MAX_GAS)
            return MAX_GAS;
        return r;

    }

    public static long limitToMaxGas(BigInteger gas) {
        long r =limitToMaxLong(gas);
        if (r>MAX_GAS)
            return MAX_GAS;
        return r;
    }

    public static long limitToMaxLong(BigInteger gas) {
        try {
            long r = gas.longValueExact();
            if (r<0)  // check if this can happen
                return Long.MAX_VALUE;
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
        byte[] codeAddress = msg.getCodeAddress().getLast20Bytes();
        byte[] senderAddress = getOwnerAddressLast20Bytes();
        byte[] contextAddress = msg.getType().isStateless() ? senderAddress : codeAddress;

        if (isLogEnabled)
            logger.info(msg.getType().name() + " for existing contract: address: [{}], outDataOffs: [{}], outDataSize: [{}]  ",
                    Hex.toHexString(contextAddress), msg.getOutDataOffs().longValue(), msg.getOutDataSize().longValue());

        Repository track = getStorage().startTracking();

        // Abort if destination contract is hibernated
        Boolean dstExists = getStorage().isExist(codeAddress);
        AccountState dstState = null;
        if (dstExists) {
            dstState = getStorage().getAccountState(codeAddress);
        }
        // 2.1 PERFORM THE VALUE (endowment) PART
        BigInteger endowment = msg.getEndowment().value();
        BigInteger senderBalance = track.getBalance(senderAddress);
        if (isNotCovers(senderBalance, endowment)) {
            stackPushZero();
            refundGas(msg.getGas().longValue(), "refund gas from message call");
            return;
        }

        // Abort if destination contract is hibernated
        // I'm not sure if it should return false or it should execute the destination contract as it was empty
        // I think that it should abort since as we don't know the scriptVersion of the dest contract,
        // we don't know if it should take the value transferred or requires ACCEPTVALUE (of this op is implemented)
        if (dstExists) {
            if (dstState.isHibernated()) {
                stackPushZero();
                refundGas(msg.getGas().longValue(), "refund gas from message call");
                return;
            }
        }

        // FETCH THE CODE
        byte[] programCode = dstExists ? getStorage().getCode(codeAddress) : EMPTY_BYTE_ARRAY;// If scriptVersion is not zero, then value must be accepted explicitely.

        // Always first remove funds from sender
        track.addBalance(senderAddress, endowment.negate());

        BigInteger contextBalance = ZERO;

        if (byTestingSuite()) {
            // This keeps track of the calls created for a test
            getResult().addCallCreate(data, contextAddress,
                        msg.getGas().longValueSafe(),
                    msg.getEndowment().getNoLeadZeroesData());
            return;
        }

        // Only transfer immediately balance if it's a new account or scriptVersion=0
        if ((!dstExists)) {
            contextBalance = track.addBalance(contextAddress, endowment);
        }

        // CREATE CALL INTERNAL TRANSACTION
        InternalTransaction internalTx = addInternalTx(null, getGasLimit(), senderAddress, contextAddress, endowment, programCode, "call");


        boolean callResult;
        if (isNotEmpty(programCode)) {
            callResult = executeCode(msg,contextAddress, contextBalance,internalTx,track,programCode,senderAddress,data);
        }
        else {
            track.commit();
            callResult = true;
            refundGas(msg.getGas().longValue(), "remaining gas from the internal call");
        }
        if (callResult)
            stackPushOne();
        else
            stackPushZero();

    }

    public boolean executeCode(
            MessageCall msg,
            byte[] contextAddress,
            BigInteger contextBalance,
            InternalTransaction internalTx,
            Repository track,
            byte[] programCode,
            byte[] senderAddress,
            byte[] data ) {

        ProgramResult childResult = null;
        ProgramInvoke programInvoke = programInvokeFactory.createProgramInvoke(
                this, new DataWord(contextAddress),
                msg.getType() == MsgType.DELEGATECALL ? getCallerAddress() : getOwnerAddress(),
                msg.getType() == MsgType.DELEGATECALL ? getCallValue() : msg.getEndowment(),
                limitToMaxLong(msg.getGas()), contextBalance, data, track, this.invoke.getBlockStore(), byTestingSuite());

        VM vm = new VM();
        Program program = new Program(programCode, programInvoke, internalTx);
        vm.play(program);
        childResult  = program.getResult();

        getTrace().merge(program.getTrace());
        getResult().merge(childResult );

        if (childResult .getException() != null) {
            if (isGasLogEnabled)
                gasLogger.debug("contract run halted by Exception: contract: [{}], exception: [{}]",
                    Hex.toHexString(contextAddress),
                    childResult .getException());

            internalTx.reject();
            childResult .rejectInternalTransactions();
            childResult.rejectLogInfos();

            track.rollback();
            return false;
        }


        // 3. APPLY RESULTS: childResult.getHReturn() into out_memory allocated
        byte[] buffer = childResult .getHReturn();
        int offset = msg.getOutDataOffs().intValue();
        int size = msg.getOutDataSize().intValue();

        memorySaveLimited(offset, buffer, size);

        // 4. THE FLAG OF SUCCESS IS ONE PUSHED INTO THE STACK
        track.commit();


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
            if (isGasLogEnabled)
                gasLogger.info("The remaining gas refunded, account: [{}], gas: [{}] ",
                        Hex.toHexString(senderAddress),
                        refundGas.toString());
        }
        return true;
    }

    public void spendGas(long gasValue, String cause) {
        if (isGasLogEnabled)
           gasLogger.info("[{}] Spent for cause: [{}], gas: [{}]", invoke.hashCode(), cause, gasValue);

        if (getRemainingGas()  < gasValue) {
            throw Program.Exception.notEnoughSpendingGas(cause, gasValue, this);
        }
        getResult().spendGas(gasValue);
    }

    public void restart() {
        setPC(startAddr);
        stackClear();
        clearUsedGas();
        stopped=false;
    }

    public void clearUsedGas() {
        getResult().clearUsedGas();
    }

    public void spendAllGas() {
        spendGas(getRemainingGas(), "Spending all remaining");
    }

    public void refundGas(long gasValue, String cause) {
        if (isGasLogEnabled)
            gasLogger.info("[{}] Refund for cause: [{}], gas: [{}]", invoke.hashCode(), cause, gasValue);
        getResult().refundGas(gasValue);
    }

    public void futureRefundGas(long gasValue) {
        if (isLogEnabled)
            logger.info("Future refund added: [{}]", gasValue);
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

    public void storageSave(byte[] key, byte[] val) {
        // DataWord constructor some times reference the passed byte[] instead
        // of making a copy.
        DataWord keyWord = new DataWord(key);
        DataWord valWord = new DataWord(val);

        // If DataWords will be reused, then we must clone them.
        if (useDataWordPool) {
            keyWord = keyWord.clone();
            valWord = valWord.clone();
        }

        getStorage().addStorageRow(getOwnerAddressLast20Bytes(), keyWord, valWord);
    }

    public byte[] getCode() {
        return ops;
    }

    public byte[] getCodeAt(DataWord address) {
        return getCodeAt(address.getLast20Bytes());
    }

    public byte[] getCodeAt(byte[] address20) {
        byte[] code = invoke.getRepository().getCode(address20);
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
        if ((index <  bn) && (index >= Math.max(0, bn - 256)))
            return new DataWord(this.invoke.getBlockStore().getBlockHashByNumber(index, getPrevHash().getData()));
        else
            return DataWord.ZERO.clone();
    }

    public DataWord getBalance(DataWord address) {
        BigInteger balance = getStorage().getBalance(address.getLast20Bytes());
        return new DataWord(balance.toByteArray());
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
        return getStorage().getStorageValue(getOwnerAddressLast20Bytes(), key);
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

    public DataWord getDifficulty() {
        return invoke.getDifficulty().clone();
    }

    public DataWord getGasLimit() {
        return invoke.getGaslimit().clone();
    }

    public ProgramResult getResult() {
        return result;
    }

    public void setRuntimeFailure(RuntimeException e) {
        getResult().setException(e);
    }

    public String memoryToString() {
        if (memory.size()>100000)
            return "<Memory too long to show>";
        else
            return memory.toString();
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

            ContractDetails contractDetails = getStorage().
                    getContractDetails(getOwnerAddressLast20Bytes());
            StringBuilder storageData = new StringBuilder();
            if (contractDetails != null) {
                List<DataWord> storageKeys = new ArrayList<>(contractDetails.getStorage().keySet());
                Collections.sort(storageKeys);
                for (DataWord key : storageKeys) {
                    storageData.append(" ").append(key).append(" -> ").
                            append(contractDetails.getStorage().get(key)).append("\n");
                }
                if (storageData.length() > 0) {
                    storageData.insert(0, "\n");
                }
            }

            StringBuilder memoryData = new StringBuilder();
            StringBuilder oneLine = new StringBuilder();
            if (memory.size() > 320)
                memoryData.append("... Memory Folded.... ")
                        .append("(")
                        .append(memory.size())
                        .append(") bytes");
            else
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
            if (memoryData.length() > 0) {
                memoryData.insert(0, "\n");
            }

            StringBuilder opsString = new StringBuilder();
            for (int i = 0; i < ops.length; ++i) {

                String tmpString = Integer.toString(ops[i] & 0xFF, 16);
                tmpString = tmpString.length() == 1 ? "0" + tmpString : tmpString;

                if (i != pc)
                    opsString.append(tmpString);
                else
                    opsString.append(" >>").append(tmpString).append("");

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

            if (pc != 0)
                globalOutput.append("[Op: ").append(OpCode.code(lastOp).name()).append("]\n");

            globalOutput.append(" -- OPS --     ").append(opsString).append("\n");
            globalOutput.append(" -- STACK --   ").append(stackData).append("\n");
            globalOutput.append(" -- MEMORY --  ").append(memoryData).append("\n");
            globalOutput.append(" -- STORAGE -- ").append(storageData).append("\n");

            if (getResult().getHReturn() != null)
                globalOutput.append("\n  HReturn: ").append(
                        Hex.toHexString(getResult().getHReturn()));

            // sophisticated assumption that msg.data != codedata
            // means we are calling the contract not creating it
            byte[] txData = invoke.getDataCopy(DataWord.ZERO, getDataSize());
            if (!Arrays.equals(txData, ops))
                globalOutput.append("\n  msg.data: ").append(Hex.toHexString(txData));
            globalOutput.append("\n\n  Spent Gas: ").append(getResult().getGasUsed());

            if (listener != null)
                listener.output(globalOutput.toString());
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

    public int processAndSkipCodeHeader(int offset) {
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

    public void precompile() {
        int i = 0;
        exeVersion = 0;
        scriptVersion = 0;
        startAddr = 0;
        pc = 0;
        i = processAndSkipCodeHeader(i);
        computeJumpDests(i);
    }

    public void computeJumpDests(int start) {
        if (jumpdestSet == null)
            jumpdestSet = new BitSet(ops.length);

        for (int i = start; i < ops.length; ++i) {
            OpCode op = OpCode.code(ops[i]);

            if (op == null)
                continue;

            if (op == OpCode.JUMPDEST)
                jumpdestSet.set(i);

            if (op.asInt() >= OpCode.PUSH1.asInt() && op.asInt() <= OpCode.PUSH32.asInt())
                i += op.asInt() - OpCode.PUSH1.asInt() + 1;
            else if (op == OpCode.DUPN || op == OpCode.SWAPN)
                i++;
        }
    }

    static String formatBinData(byte[] binData, int startPC) {
        StringBuilder ret = new StringBuilder();
        for (int i = 0; i < binData.length; i+= 16) {
            ret.append(Utils.align("" + Integer.toHexString(startPC + (i)) + ":", ' ', 8, false));
            ret.append(Hex.toHexString(binData, i, min(16, binData.length - i))).append('\n');
        }
        return ret.toString();
    }

    public static String stringifyMultiline(byte[] code) {
        int index = 0;
        StringBuilder sb = new StringBuilder();
        BitSet mask = buildReachableBytecodesMask(code);
        ByteArrayOutputStream binData = new ByteArrayOutputStream();
        int binDataStartPC = -1;

        while (index < code.length) {
            final byte opCode = code[index];
            OpCode op = OpCode.code(opCode);

            if (!mask.get(index)) {
                if (binDataStartPC == -1) {
                    binDataStartPC = index;
                }
                binData.write(code[index]);
                index ++;
                if (index < code.length) {
                    continue;
                }
            }

            if (binDataStartPC != -1) {
                sb.append(formatBinData(binData.toByteArray(), binDataStartPC));
                binDataStartPC = -1;
                binData = new ByteArrayOutputStream();
                if (index == code.length) {
                    continue;
                }
            }

            sb.append(Utils.align("" + Integer.toHexString(index) + ":", ' ', 8, false));

            if (op == null) {
                sb.append("<UNKNOWN>: ").append(0xFF & opCode).append("\n");
                index ++;
                continue;
            }

            if (op.name().startsWith("PUSH")) {
                sb.append(' ').append(op.name()).append(' ');

                int nPush = op.val() - OpCode.PUSH1.val() + 1;
                byte[] data = Arrays.copyOfRange(code, index + 1, index + nPush + 1);
                BigInteger bi = new BigInteger(1, data);
                sb.append("0x").append(bi.toString(16));
                if (bi.bitLength() <= 32) {
                    sb.append(" (").append(new BigInteger(1, data).toString()).append(") ");
                }

                index += nPush + 1;
            }
            else if (op.name().equals("DUPN") || op.name().equals("SWAPN")) {
                    sb.append(' ').append(op.name()).append(' ');

                    byte[] data = Arrays.copyOfRange(code, index + 1, index + 2);
                    BigInteger bi = new BigInteger(1, data);
                    sb.append("0x").append(bi.toString(16));
                    if (bi.bitLength() <= 32) {
                        sb.append(" (").append(new BigInteger(1, data).toString()).append(") ");
                    }

                    index++;
            } else {
                sb.append(' ').append(op.name());
                index++;
            }
            sb.append('\n');
        }

        return sb.toString();
    }

    static class ByteCodeIterator {
        byte[] code;
        int pc;

        public ByteCodeIterator(byte[] code) {
            this.code = code;
        }

        public void setPC(int pc) {
            this.pc = pc;
        }

        public int getPC() {
            return pc;
        }

        public OpCode getCurOpcode() {
            return pc < code.length ? OpCode.code(code[pc]) : null;
        }

        public boolean isPush() {
            return getCurOpcode() != null ? getCurOpcode().name().startsWith("PUSH") : false;
        }

        public boolean isDupN() {
            return getCurOpcode() != null ? getCurOpcode().name().equals("DUPN") : false;
        }

        public boolean isSwapN() {
            return getCurOpcode() != null ? getCurOpcode().name().equals("SWAPN") : false;
        }

        public byte[] getCurOpcodeArg() {
            if (isPush()) {
                int nPush = getCurOpcode().val() - OpCode.PUSH1.val() + 1;
                byte[] data = Arrays.copyOfRange(code, pc + 1, pc + nPush + 1);
                return data;
            } else if (isDupN() || isSwapN()) {
                return Arrays.copyOfRange(code, pc + 1, pc + 2);
            }
            else {
                return new byte[0];
            }
        }

        public boolean next() {
            pc += 1 + getCurOpcodeArg().length;
            return pc < code.length;
        }
    }

    static BitSet buildReachableBytecodesMask(byte[] code) {
        NavigableSet<Integer> gotos = new TreeSet<>();
        ByteCodeIterator it = new ByteCodeIterator(code);
        BitSet ret = new BitSet(code.length);
        int lastPush = 0;
        int lastPushPC = 0;
        do {
            ret.set(it.getPC()); // reachable bytecode
            if (it.isPush()) {
                lastPush = new BigInteger(1, it.getCurOpcodeArg()).intValue();
                lastPushPC = it.getPC();
            }
            if (it.getCurOpcode() == OpCode.JUMP || it.getCurOpcode() == OpCode.JUMPI) {
                if (it.getPC() != lastPushPC + 1) {
                    // some PC arithmetic we totally can't deal with
                    // assuming all bytecodes are reachable as a fallback
                    ret.setAll();
                    return ret;
                }
                int jumpPC = lastPush;
                if (!ret.get(jumpPC)) {
                    // code was not explored yet
                    gotos.add(jumpPC);
                }
            }
            if (it.getCurOpcode() == OpCode.JUMP || it.getCurOpcode() == OpCode.RETURN ||
                    it.getCurOpcode() == OpCode.STOP) {
                if (gotos.isEmpty()) {
                    break;
                }
                it.setPC(gotos.pollFirst());
            }
        } while(it.next());
        return ret;
    }

    public void addListener(ProgramOutListener listener) {
        this.listener = listener;
    }

    public int verifyJumpDest(DataWord nextPC) {
        // This is painstankly slow
        if (nextPC.occupyMoreThan(4)) {
            throw Program.Exception.badJumpDestination(-1);
        }
        int ret = nextPC.intValue(); // could be negative
        if (ret < 0 || ret >= jumpdestSet.size() || !jumpdestSet.get(ret)) {
            throw Program.Exception.badJumpDestination(ret);
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

        byte[] senderAddress = this.getOwnerAddressLast20Bytes();
        byte[] codeAddress = msg.getCodeAddress().getLast20Bytes();
        byte[] contextAddress = msg.getType().isStateless() ? senderAddress : codeAddress;

        BigInteger endowment = msg.getEndowment().value();
        BigInteger senderBalance = track.getBalance(senderAddress);
        if (senderBalance.compareTo(endowment) < 0) {
            stackPushZero();
            this.refundGas(msg.getGas().longValue(), "refund gas from message call");
            return;
        }

        byte[] data = this.memoryChunk(msg.getInDataOffs().intValue(),
                msg.getInDataSize().intValue());

        // Charge for endowment - is not reversible by rollback
        transfer(track, senderAddress, contextAddress, msg.getEndowment().value());

        if (byTestingSuite()) {
            // This keeps track of the calls created for a test
            this.getResult().addCallCreate(data,
                    msg.getCodeAddress().getLast20Bytes(),
                    msg.getGas().longValueSafe(),
                    msg.getEndowment().getNoLeadZeroesData());

            stackPushOne();
            return;
        }


        long requiredGas = contract.getGasForData(data);
        if (requiredGas > msg.getGas().longValue()) {

            this.refundGas(0, "call pre-compiled"); //matches cpp logic
            this.stackPushZero();
            track.rollback();
        } else {

            this.refundGas(msg.getGas().longValue() - requiredGas, "call pre-compiled");

            if (contract instanceof Bridge || contract instanceof RemascContract) {
                // CREATE CALL INTERNAL TRANSACTION
                InternalTransaction internalTx = addInternalTx(null, getGasLimit(), senderAddress, contextAddress, endowment, EMPTY_BYTE_ARRAY, "call");

                Block executionBlock = new Block(getPrevHash().getData(), EMPTY_BYTE_ARRAY, getCoinbase().getData(), EMPTY_BYTE_ARRAY,
                        getDifficulty().getData(), getNumber().longValue(), getGasLimit().getData(), 0, getTimestamp().longValue(),
                        EMPTY_BYTE_ARRAY, EMPTY_BYTE_ARRAY, EMPTY_BYTE_ARRAY, new ArrayList<>(), new ArrayList<>(), null);

                contract.init(internalTx, executionBlock, track, this.invoke.getBlockStore(), null, null);
            }
            byte[] out = contract.execute(data);

            this.memorySave(msg.getOutDataOffs().intValue(), out);
            this.stackPushOne();
            track.commit();
        }
    }

    public boolean byTestingSuite() {
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

    public static class Exception {

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
            return new IllegalOperationException("Invalid operation code: opCode[%s];", Hex.toHexString(opCode, 0, 1));
        }

        public static BadJumpDestinationException badJumpDestination(int pc) {
            return new BadJumpDestinationException("Operation with pc isn't 'JUMPDEST': PC[%d];", pc);
        }

        public static StackTooSmallException tooSmallStack(int expectedSize, int actualSize) {
            return new StackTooSmallException("Expected stack size %d but actual %d;", expectedSize, actualSize);
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
