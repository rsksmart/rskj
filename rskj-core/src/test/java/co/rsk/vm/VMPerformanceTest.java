/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.vm;

import co.rsk.config.TestSystemProperties;
import co.rsk.config.VmConfig;
import org.ethereum.config.BlockchainConfig;
import org.ethereum.config.blockchain.regtest.RegTestGenesisConfig;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.OpCode;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.VM;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.invoke.ProgramInvokeMockImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static org.ethereum.TestUtils.padLeft;
import static org.ethereum.TestUtils.padRight;
import static org.junit.Assert.assertEquals;

// Remove junit imports for standalone use

/**
 * Created by Sergio on 03/07/2016.
 */
public class VMPerformanceTest {
    private final TestSystemProperties config = new TestSystemProperties();
    private final VmConfig vmConfig = config.getVmConfig();
    private final PrecompiledContracts precompiledContracts = new PrecompiledContracts(config);
    private final BlockchainConfig blockchainConfig = new RegTestGenesisConfig();
    private ProgramInvokeMockImpl invoke;
    private Program program;
    ThreadMXBean thread;
    VM vm;

    final static int million = 1000*1000;
    final static int maxLoops = 1*million;
    final static int maxGroups = 1;
    final static boolean useProfiler = false;

    // To execute as a standalone application, remove @before @after @test and run main.
    public static void main(String args[]) throws Exception {
        VMPerformanceTest vmpt = new VMPerformanceTest();
        vmpt.setup();
        vmpt.testVMPerformance1();
        vmpt.tearDown();
    }

    public static void runWithLogging(ResultLogger resultLogger) {
        VMPerformanceTest vmpt = new VMPerformanceTest();
        vmpt.setup();
        vmpt.testVMPerformance1(resultLogger);
        vmpt.tearDown();
    }

    @Before
    public void setup() {
        invoke = new ProgramInvokeMockImpl();
        long million=1000000;
        invoke.setGasLimit(1000*million);
    }

    @After
    public void tearDown() {
        invoke.getRepository().close();
    }

    public void waitForProfiler() {
        System.out.println("Waiting for profiler to connect..");

        try {
            Thread.sleep(20000); // 10 seconds sleep to prepare profiler
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("go!");
    }
    /**************************************************************************************************************************
     * Sample results of testVMPerformance1():
     PUSH/POP    : full:      25 ref:      25 (% ref):     0 gas:    500 time/gas:      5 fullReal:      27 mem[Kb]:          0
     I2O1(OR)    : full:      81 ref:      81 (% ref):     0 gas:   1000 time/gas:      8 fullReal:     105 mem[Kb]:        992
     I2O1(OR)    : full:      71 ref:      71 (% ref):     0 gas:   1000 time/gas:      7 fullReal:      71 mem[Kb]:        651
     ADD         : full:      82 ref:      11 (% ref):    15 gas:   1100 time/gas:      7 fullReal:      84 mem[Kb]:        651
     ADD         : full:      31 ref:     -40 (% ref):   -56 gas:   1100 time/gas:      2 fullReal:      31 mem[Kb]:        651
     MUL         : full:      71 ref:       0 (% ref):     0 gas:   1300 time/gas:      5 fullReal:      74 mem[Kb]:       9576
     MUL         : full:      65 ref:      -6 (% ref):    -8 gas:   1300 time/gas:      5 fullReal:      65 mem[Kb]:      59738
     SUB         : full:      60 ref:     -11 (% ref):   -15 gas:   1100 time/gas:      5 fullReal:      61 mem[Kb]:     282089
     SUB         : full:      51 ref:     -20 (% ref):   -28 gas:   1100 time/gas:      4 fullReal:      52 mem[Kb]:     268191
     DIV         : full:      60 ref:     -11 (% ref):   -15 gas:   1300 time/gas:      4 fullReal:      61 mem[Kb]:     314099
     DIV         : full:      60 ref:     -11 (% ref):   -15 gas:   1300 time/gas:      4 fullReal:      60 mem[Kb]:     306181
     SDIV        : full:      59 ref:     -12 (% ref):   -16 gas:   1300 time/gas:      4 fullReal:      61 mem[Kb]:     301976
     SDIV        : full:      67 ref:      -4 (% ref):    -5 gas:   1300 time/gas:      5 fullReal:      67 mem[Kb]:     306826
     MOD         : full:      60 ref:     -11 (% ref):   -15 gas:   1300 time/gas:      4 fullReal:      61 mem[Kb]:     282044
     MOD         : full:      51 ref:     -20 (% ref):   -28 gas:   1300 time/gas:      3 fullReal:      50 mem[Kb]:     272231
     SMOD        : full:      60 ref:     -11 (% ref):   -15 gas:   1300 time/gas:      4 fullReal:      63 mem[Kb]:     303955
     SMOD        : full:      59 ref:     -12 (% ref):   -16 gas:   1300 time/gas:      4 fullReal:      59 mem[Kb]:     294067
     EXP         : full:     157 ref:      86 (% ref):   121 gas:   2800 time/gas:      5 fullReal:     159 mem[Kb]:     296861
     EXP         : full:     143 ref:      72 (% ref):   101 gas:   2800 time/gas:      5 fullReal:     143 mem[Kb]:     374146
     SIGNEXTEND  : full:      32 ref:     -39 (% ref):   -54 gas:   1300 time/gas:      2 fullReal:      31 mem[Kb]:          0
     SIGNEXTEND  : full:      31 ref:     -40 (% ref):   -56 gas:   1300 time/gas:      2 fullReal:      30 mem[Kb]:          0
     LT          : full:      35 ref:     -36 (% ref):   -50 gas:   1100 time/gas:      3 fullReal:      35 mem[Kb]:          0
     LT          : full:      31 ref:     -40 (% ref):   -56 gas:   1100 time/gas:      2 fullReal:      32 mem[Kb]:          0
     GT          : full:      43 ref:     -28 (% ref):   -39 gas:   1100 time/gas:      3 fullReal:      44 mem[Kb]:     121177
     GT          : full:      39 ref:     -32 (% ref):   -45 gas:   1100 time/gas:      3 fullReal:      38 mem[Kb]:     121177
     SLT         : full:      43 ref:     -28 (% ref):   -39 gas:   1100 time/gas:      3 fullReal:      43 mem[Kb]:     121177
     SLT         : full:      42 ref:     -29 (% ref):   -40 gas:   1100 time/gas:      3 fullReal:      43 mem[Kb]:     121177
     SGT         : full:      45 ref:     -26 (% ref):   -36 gas:   1100 time/gas:      4 fullReal:      44 mem[Kb]:     121177
     SGT         : full:      42 ref:     -29 (% ref):   -40 gas:   1100 time/gas:      3 fullReal:      42 mem[Kb]:     123175
     EQ          : full:      35 ref:     -36 (% ref):   -50 gas:   1100 time/gas:      3 fullReal:      35 mem[Kb]:       1998
     EQ          : full:      34 ref:     -37 (% ref):   -52 gas:   1100 time/gas:      3 fullReal:      34 mem[Kb]:          0
     AND         : full:      35 ref:     -36 (% ref):   -50 gas:   1100 time/gas:      3 fullReal:      36 mem[Kb]:          0
     AND         : full:      32 ref:     -39 (% ref):   -54 gas:   1100 time/gas:      2 fullReal:      32 mem[Kb]:          0
     OR          : full:      35 ref:     -36 (% ref):   -50 gas:   1100 time/gas:      3 fullReal:      35 mem[Kb]:          0
     OR          : full:      31 ref:     -40 (% ref):   -56 gas:   1100 time/gas:      2 fullReal:      31 mem[Kb]:          0
     XOR         : full:      31 ref:     -40 (% ref):   -56 gas:   1100 time/gas:      2 fullReal:      32 mem[Kb]:          0
     XOR         : full:      29 ref:     -42 (% ref):   -59 gas:   1100 time/gas:      2 fullReal:      29 mem[Kb]:          0
     BYTE        : full:      34 ref:     -37 (% ref):   -52 gas:   1100 time/gas:      3 fullReal:      34 mem[Kb]:          0
     BYTE        : full:      34 ref:     -37 (% ref):   -52 gas:   1100 time/gas:      3 fullReal:      33 mem[Kb]:          0
     I3O1(ADDMOD): full:      39 ref:      39 (% ref):     0 gas:   1300 time/gas:      3 fullReal:      38 mem[Kb]:          0
     I3O1(ADDMOD): full:      39 ref:      39 (% ref):     0 gas:   1300 time/gas:      3 fullReal:      38 mem[Kb]:          0
     ADDMOD      : full:      79 ref:      40 (% ref):   102 gas:   1900 time/gas:      4 fullReal:      79 mem[Kb]:     428783
     ADDMOD      : full:      70 ref:      31 (% ref):    79 gas:   1900 time/gas:      3 fullReal:      71 mem[Kb]:     427745
     MULMOD      : full:      81 ref:      42 (% ref):   107 gas:   1900 time/gas:      4 fullReal:      81 mem[Kb]:     106577
     MULMOD      : full:      84 ref:      45 (% ref):   115 gas:   1900 time/gas:      4 fullReal:      84 mem[Kb]:     127826
     I1O1(ISZERO): full:      15 ref:      15 (% ref):     0 gas:    500 time/gas:      3 fullReal:      14 mem[Kb]:          0
     I1O1(ISZERO): full:      12 ref:      12 (% ref):     0 gas:    500 time/gas:      2 fullReal:      12 mem[Kb]:          0
     ISZERO      : full:      21 ref:       9 (% ref):    75 gas:    800 time/gas:      2 fullReal:      20 mem[Kb]:       1818
     ISZERO      : full:      20 ref:       8 (% ref):    66 gas:    800 time/gas:      2 fullReal:      19 mem[Kb]:          0
     NOT         : full:      23 ref:      11 (% ref):    91 gas:    800 time/gas:      2 fullReal:      24 mem[Kb]:          0
     NOT         : full:      23 ref:      11 (% ref):    91 gas:    800 time/gas:      2 fullReal:      23 mem[Kb]:          0
    */
    static Boolean shortArg = false;

    @Ignore
    @Test
    public void testVMPerformance1() {
        testVMPerformance1(null);
    }

    private void testVMPerformance1(ResultLogger resultLogger) {
        thread = ManagementFactory.getThreadMXBean();
        if (!thread.isThreadCpuTimeSupported()) return;

        Boolean old = thread.isThreadCpuTimeEnabled();
        thread.setThreadCpuTimeEnabled(true);
        vm = new VM(config.getVmConfig(), new PrecompiledContracts(config));
        if (useProfiler)
            waitForProfiler();

        System.out.println("Configuration: Program.useDataWordPool =  " + Program.getUseDataWordPool().toString());
        System.out.println("Configuration: shortArg =  " + shortArg.toString());

        // Program
        measureProgram("PUSH/POP", Hex.decode("60A0" + "50"), 2, 2, 0, 100, null); // push "A0", pop it
        //measureOpcode(OpCode.NOT, false, 0); // For reference, to see general overhead
        //measureOpcode(OpCode.NOT, false,0); // Re-measure to see if JIT does something

        /* Standard */
        OpCode[] simpleOpcodes = new OpCode[]{
                OpCode.ADD, OpCode.MUL, OpCode.SUB,
                OpCode.DIV, OpCode.SDIV, OpCode.MOD,
                OpCode.SMOD, OpCode.EXP,
                OpCode.SIGNEXTEND, OpCode.LT,
                OpCode.GT, OpCode.SLT,
                OpCode.SGT, OpCode.EQ,
                OpCode.AND, OpCode.OR,
                OpCode.XOR, OpCode.BYTE};

        //the reference must be measured for a longer time since its faster and has more jitter
        long refTime21 = measureOpcode(OpCode.OR, true, 0, null);
        //measureOpcode(OpCode.ADD, false, refTime21);
        if (true) {
            for (int i = 0; i < simpleOpcodes.length; i++) {
                measureOpcode(simpleOpcodes[i], false, refTime21, resultLogger);
            }
            long refTime31 = measureOpcode(OpCode.ADDMOD, true, 0, null);
            measureOpcode(OpCode.ADDMOD, false, refTime31, resultLogger);
            measureOpcode(OpCode.MULMOD, false, refTime31, resultLogger);

            long refTime11 = measureOpcode(OpCode.ISZERO, true, 0, null);
            measureOpcode(OpCode.ISZERO, false, refTime11, resultLogger);
            measureOpcode(OpCode.NOT, false, refTime11, resultLogger);
        }
        thread.setThreadCpuTimeEnabled(old);
    }



    public long measureOpcode(OpCode opcode, Boolean reference, long refTime, ResultLogger resultLogger) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int iCount = 0;
        DataWord maxValue = new DataWord();
        maxValue.bnot();
        // PUSH
        for (int inp = 0; inp < opcode.require(); inp++) {
            if (shortArg) {
                baos.write(0x60);
                baos.write(0xA0); // argument
            } else {
                baos.write(0x7f); // PUSH32
                try {
                    baos.write(maxValue.getData());
                    // decrement maxValue so that each value pushed is a little different
                    maxValue.sub(DataWord.ONE);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            iCount++;
        }

        if (!reference) {
            baos.write(opcode.asInt());
            iCount++;

        } else {
            // Comparar con un pop
            if (opcode.require() > 1) {
                baos.write(0x50);
                iCount++;

            }
        }
        // POP
        for (int out = 0; out < opcode.ret(); out++) {
            baos.write(0x50);
            iCount++;
        }

        String name = opcode.name();
        if (reference)
            name = "I" + opcode.require() + "O" + opcode.ret()+"("+name+")";

        // execute twice each case.
        // Both times the executed method is exactly equal. We just want to detect any huge variance because of the GC interrupting.
        measureProgram(name, baos.toByteArray(), iCount, 1, refTime, 100, resultLogger);
        return measureProgram(name, baos.toByteArray(), iCount, 1, refTime, 100, resultLogger);

    }

    /**
     * This method guarantees that garbage collection is
     * done unlike <code>{@link System#gc()}</code>
     */
    public static void forceGc() {
        Object obj = new Object();
        WeakReference ref = new WeakReference<Object>(obj);
        obj = null;
        while (ref.get() != null) {
            System.gc();
        }
    }

    public class PerfRes {
        public long deltaUsedMemory;
        public long deltaRealTime;
        public long wallClockTimeMillis; // in milliseconds
        public long deltaTime; // in nanoseconds.
        public long gas;

    }

    public interface ResultLogger {
        void log(String name, PerfRes result);
    }

    public long measureProgram(String opcode, byte[] code, int insCount, int divisor, long refTime, int cloneCount, ResultLogger resultLogger) {
        // Repeat program 100 times to reduce the overhead of clearing the stack
        // the program must not loop.
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (int i = 0; i < cloneCount; i++)
            baos.write(code, 0, code.length);
        byte[] newCode = baos.toByteArray();

        program = new Program(vmConfig, precompiledContracts, blockchainConfig, newCode, invoke, null);
        int sa = program.getStartAddr();

        long myLoops = maxLoops / cloneCount;
        insCount = insCount * cloneCount;

        Runtime rt = Runtime.getRuntime();
        PerfRes best = null;
        forceGc();
        PerfRes pr = new PerfRes();
        for (int g = 0; g < maxGroups; g++) {
            long startUsedMemory = (rt.totalMemory() - rt.freeMemory());
            long startRealTime = System.currentTimeMillis();
            long startTime = thread.getCurrentThreadCpuTime(); // in nanoseconds.
            long startGas = program.getResult().getGasUsed();


            long xUsedMemory;
            // Al parecer el gc puede interrumpir en cualquier momento y hacer estragos
            // Mejor que repetir y tomar el promedio es ejecutar muchas veces y quedarse con
            // el menor tiempo logrado
            for (int loops = 0; loops < myLoops; loops++) {
            /*for (int i=0;i<insCount;i++) {
                vm.step(program);
            }*/
                vm.steps(program, insCount);

                //xUsedMemory = (rt.totalMemory() - rt.freeMemory());
                // trick: Now force the program to restart, clear stack
                if (loops == 0) {
                    long endGas = program.getResult().getGasUsed();
                    pr.gas = endGas - startGas;
                }
                program.restart();

            }


            long endTime = thread.getCurrentThreadCpuTime();
            long endRealTime = System.currentTimeMillis();
            pr.deltaTime = (endTime - startTime) / maxLoops / divisor ; // nano
            pr.wallClockTimeMillis = (endRealTime - startRealTime);
            pr.deltaRealTime = (endRealTime - startRealTime) * 1000 *1000 / maxLoops / divisor; // de milli a nano
            long endUsedMemory = (rt.totalMemory() - rt.freeMemory());


            pr.deltaUsedMemory = endUsedMemory - startUsedMemory;
            if ((best == null) || (pr.deltaTime < best.deltaTime)) {
                best = pr;
                if (best.deltaTime == 0)
                    System.out.println("bad time");
                pr = new PerfRes();
            }
        }
        long percent;
        if (refTime != 0)
            percent = (best.deltaTime - refTime) * 100 / refTime;
        else
            percent = 0;

        System.out.println(
                padRight(opcode, 12) + ":" +
                        " wctime[msec]: "+padLeft(Long.toString(best.wallClockTimeMillis), 7) +
                        " full: " + padLeft(Long.toString(best.deltaTime), 7) +
                        " ref: " + padLeft(Long.toString(best.deltaTime - refTime), 7) +
                        " (% ref): " + padLeft(Long.toString(percent), 5) +
                        " gas: " + padLeft(Long.toString(best.gas), 6) +
                        " time/gas: " + padLeft(Long.toString(best.deltaTime * 100 / best.gas), 6) +
                        " fullReal: " + padLeft(Long.toString(best.deltaRealTime), 7) +
                        " mem[Kb]: " + padLeft(Long.toString(best.deltaUsedMemory / 1000), 10));


        if (resultLogger != null) {
            resultLogger.log(opcode, best);
        }

        return best.deltaTime;
    }

    long getGarbageCollectorTimeMillis()
    {
        long t=0;
        List<GarbageCollectorMXBean> gcs = ManagementFactory.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean gc :gcs) {
            t +=gc.getCollectionTime();
        }
        return t;
    }

    //ThreadMXBean thread;
    long startTime;
    long startRealTime;
    long startGCTime;

    void startMeasure() {

        Boolean oldMode;
        vm.resetVmCounter();
        startTime = 0;
        thread = ManagementFactory.getThreadMXBean();
        if (thread.isThreadCpuTimeSupported())

        {
            oldMode = thread.isThreadCpuTimeEnabled();
            thread.setThreadCpuTimeEnabled(true);
            startTime = thread.getCurrentThreadCpuTime(); // in nanoseconds.
        }
        startRealTime = System.currentTimeMillis();
        startGCTime = getGarbageCollectorTimeMillis();




    }

    long deltaTime; // in nanoseconds
    long deltaRealTime;

    void endMeasure() {
        if (startTime != 0) {
            long endTime = thread.getCurrentThreadCpuTime();
            deltaTime = (endTime - startTime); // nano
            //System.out.println("Time elapsed [us]: " + Long.toString(deltaTime)+" [s]:"+ Long.toString(deltaTime/1000/1000));
            System.out.println("Time elapsed [ms]: " + Long.toString(deltaTime/1000)+" [s]:"+ Long.toString(deltaTime/1000/1000));
        }

        if (startRealTime!=0) {
            long endRealTime =System.currentTimeMillis();
            deltaRealTime = (endRealTime - startRealTime);
            System.out.println("RealTime elapsed [ms]: " + Long.toString(deltaRealTime)+" [s]:"+ Long.toString(deltaRealTime/1000));
        }
        long endGCTime = getGarbageCollectorTimeMillis();
        long deltaGCTime = (endGCTime - startGCTime);
        System.out.println("GCTime elapsed [ms]: " + Long.toString(deltaGCTime)+" [s]:"+ Long.toString(deltaGCTime/1000));

    }

    @Ignore //
    @Test
    public void testLongOperation() {
        /* bad example because requires ABI parsing
        contract Fibonacci {
            function fib() returns (uint r) {
                uint256 a;
                uint256 b;
                uint256 c;

                a=0;
                b=1;
                for (uint i = 1; i < 55; i++) {
                    c = a+b;
                    a = b;
                    b = c;
                }
                r = b;
            }
        } // contract

     // Good example
    contract Fibonacci {
    function()  {
        uint256 a;
        uint256 b;
        uint256 c;

        a=0;
        b=1;
        for (uint i = 1; i < 50; i++) {
            c = a+b;
            a = b;
            b = c;
        }
        assembly {
                mstore(0x0, b)
                return(0x0, 32)
        }
    }
} // contract
        */

        vm = new VM(config.getVmConfig(), new PrecompiledContracts(config));
        // Strip the first 16 bytes which are added by Solidity to store the contract.
        byte[] codePlusPrefix = Hex.decode(
                //---------------------------------------------------------------------------------------------------------------------nn
                "606060405260618060106000396000f360606040523615600d57600d565b605f5b6000600060006000600093508350600192508250600190505b60"+
                        //"32"+ // 55
                        "FE"+   // 254
                        "811015604f5782840191508150829350835081925082505b80806001019150506029565b8260005260206000f35b50505050565b00");
        //"606060405260618060106000396000f360606040523615600d57600d565b605f5b6000600060006000600093508350600192508250600190505b600f811015604f5782840191508150829350835081925082505b80806001019150506029565b8260005260206000f35b50505050565b00"

        /* Prefix code
        Instr.#    addrs.      mnemonic        operand                                                            xrefs                          description

        ------------------------------------------------------------------------------------------------------------------------------------------------------
        [       0] [0x00000000] PUSH1           0x60 ('`')                                                                                        # Place 1 byte item on stack.
        [       1] [0x00000002] PUSH1           0x40 ('@')                                                                                        # Place 1 byte item on stack.
        [       2] [0x00000004] MSTORE                                                                                                            # Save word to memory.
        [       3] [0x00000005] PUSH1           0x61 ('a')   This is the real contract length                                                                                     # Place 1 byte item on stack.
        [       4] [0x00000007] DUP1                                                                                                              # Duplicate 1st stack item.
        [       5] [0x00000008] PUSH1           0x10                                                                                              # Place 1 byte item on stack.
        [       6] [0x0000000a] PUSH1           0x00                                                                                              # Place 1 byte item on stack.
        [       7] [0x0000000c] CODECOPY                                                                                                          # Copy code running in current environment to memory.
        [       8] [0x0000000d] PUSH1           0x00                                                                                              # Place 1 byte item on stack.
        [       9] [0x0000000f] RETURN
        ------------------------------------------------------------------------------------------------------------------------------------------------------*/
        byte[] code = Arrays.copyOfRange(codePlusPrefix,16,codePlusPrefix.length);

        program =new Program(vmConfig, precompiledContracts, blockchainConfig, code, invoke, null);

        //String s_expected_1 = "000000000000000000000000000000000000000000000000000000033FFC1244"; // 55
        //String s_expected_1 = "00000000000000000000000000000000000000000000000000000002EE333961";// 50
        String s_expected_1 = "0000000000000000000090A7ED63052BFF49E105B6B7BC90D0B352C89BA1AD59"; // 254

        startMeasure();
        vm.steps(program,Long.MAX_VALUE);
        endMeasure();
        byte[] actualHReturn = null;
        if (program.getResult().getHReturn() != null) {
            actualHReturn = program.getResult().getHReturn();
        }

        //if (!Arrays.equals(expectedHReturn, actualHReturn)) {

        // DataWord item1 = program.stackPop();
        assertEquals(s_expected_1, Hex.toHexString(actualHReturn).toUpperCase());
    }

    LinkedList<Object> lk;
    int maxLLSize = 0;

    public void createLarseSetOfMemoryObjects(){
        lk = new LinkedList<>();
        System.out.println("Creating "+Integer.toString(maxLLSize)+" linked  objects..");

        for (int i=0;i<maxLLSize;i++) {
            lk.add(new Object());
        }
    }

    @Ignore //
    @Test
    public void testFibonacciLongTime() {
        /********************************************************************************************
         *  This is the Solidity contract that was compiled:

         contract Fibonacci50Times {
         function()  {
             uint256 a;
             uint256 b;
             uint256 c;
             for (uint k = 0; k < 1000; k++) {  // The maximum value of k was varied
                 a=0;
                 b=1;
                 for (uint i = 1; i < 50; i++) {
                     c = a+b;
                     a = b;
                     b = c;
                 }
             }
             assembly {
                 mstore(0x0, b)
                 return(0x0, 32)
             }
         }
         } // contract
         ********************************************************************************************/
        vm = new VM(config.getVmConfig(), new PrecompiledContracts(config));
        /////////////////////////////////////////////////////////////////////////////////////////////////
        // To increase precesion of the measurement, the maximum k value was increased
        // until the contract took more than 30 seconds
        // max k=100
        // byte[] codePlusPrefix = Hex.decode("6060604052607e8060106000396000f360606040523615600d57600d565b607c5b60006000600060006000600091505b6064821015606b57600094508450600193508350600190505b6032811015605e5783850192508250839450845082935083505b80806001019150506038565b5b8180600101925050601f565b8360005260206000f35b5050505050565b00");
        // max k=1000
        // byte[] codePlusPrefix = Hex.decode("6060604052607f8060106000396000f360606040523615600d57600d565b607d5b60006000600060006000600091505b6103e8821015606c57600094508450600193508350600190505b6032811015605f5783850192508250839450845082935083505b80806001019150506039565b5b8180600101925050601f565b8360005260206000f35b5050505050565b00");
        // max k=10K
        //  byte[] codePlusPrefix = Hex.decode("6060604052607f8060106000396000f360606040523615600d57600d565b607d5b60006000600060006000600091505b612710821015606c57600094508450600193508350600190505b6032811015605f5783850192508250839450845082935083505b80806001019150506039565b5b8180600101925050601f565b8360005260206000f35b5050505050565b00");
        //  max k=100K
        /////////////////////////////////////////////////////////////////////////////////////////////////
        byte[] codePlusPrefix = Hex.decode("606060405260808060106000396000f360606040523615600d57600d565b607e5b60006000600060006000600091505b620186a0821015606d57600094508450600193508350600190505b603281101560605783850192508250839450845082935083505b8080600101915050603a565b5b8180600101925050601f565b8360005260206000f35b5050505050565b00");
        byte[] code = Arrays.copyOfRange(codePlusPrefix, 16, codePlusPrefix.length);

        // vm.computeGas = false;

        String s_expected = "00000000000000000000000000000000000000000000000000000002EE333961";// 50

        System.out.println("Creating a large set of linked memory objects to force GC...");
        maxLLSize = 10*1000*1000; // 10 million linked objects.
        createLarseSetOfMemoryObjects();
        System.out.println("done.");

        // now measure with and without Data Word Pool
        Program.setUseDataWordPool(true);
        testRunTime(code, s_expected);
        Program.setUseDataWordPool(false);
        testRunTime(code, s_expected);
    }
    /****************** RESULTS 30/12/2016 ******************************************
    Creating a large set of linked memory objects to force GC...
    Creating 10000000 linked  objects..
    done.
    Starting test....
    Configuration: Program.useDataWordPool =  true
    Time elapsed [ms]: 10062 [s]:10
    RealTime elapsed [ms]: 10263 [s]:10
    GCTime elapsed [ms]: 0 [s]:0
    Instructions executed: : 170400032
    Instructions per second: 16934898
    Avg Time per instructions [us]: 0
    Avg Time per instructions [ns]: 59
    -----------------------------------------------------------------------------
    Starting test....
    Configuration: Program.useDataWordPool =  false
    Time elapsed [ms]: 16957 [s]:16
    RealTime elapsed [ms]: 28273 [s]:28
    GCTime elapsed [ms]: 10868 [s]:10
    Instructions executed: : 170400032
    Instructions per second: 10048766
    Avg Time per instructions [us]: 0
    Avg Time per instructions [ns]: 99
    -----------------------------------------------------------------------------*/

    public void testRunTime(byte[] code, String s_expected) {
        program = new Program(vmConfig, precompiledContracts, blockchainConfig, code, invoke, null);
        System.out.println("-----------------------------------------------------------------------------");
        System.out.println("Starting test....");
        System.out.println("Configuration: Program.useDataWordPool =  " + Program.getUseDataWordPool().toString());
        startMeasure();
        vm.steps(program, Long.MAX_VALUE);
        endMeasure();
        System.out.println("Instructions executed: : " + Integer.toString(vm.getVmCounter()));
        System.out.println("Gas consumed: " + Long.toString(program.getResult().getGasUsed()));
        System.out.println("Average Gas per instruction: " + Long.toString(program.getResult().getGasUsed()/vm.getVmCounter()));

        long M = 1000 * 1000;
        long insPerSecond = vm.getVmCounter() * M / deltaTime;
        System.out.println("Instructions per second: " + Long.toString(insPerSecond));
        System.out.println("Avg Time per instructions [us]: " + Long.toString(M / insPerSecond));
        System.out.println("Avg Time per instructions [ns]: " + Long.toString(1000 * M / insPerSecond));

        byte[] actualHReturn = null;
        if (program.getResult().getHReturn() != null) {
            actualHReturn = program.getResult().getHReturn();
        }
        assertEquals(s_expected, Hex.toHexString(actualHReturn).toUpperCase());
        System.out.println("-----------------------------------------------------------------------------");
    }
    /* TEST CASE LIST END */
}
