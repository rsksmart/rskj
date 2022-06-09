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
import co.rsk.helpers.PerformanceTestConstants;
import co.rsk.peg.utils.PegUtils;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.BlockFactory;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.OpCode;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.VM;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.invoke.ProgramInvokeMockImpl;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.HashSet;
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
    private final BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());
    private final VmConfig vmConfig = config.getVmConfig();
    private final PegUtils pegUtils = PegUtils.getInstance();
    private final PrecompiledContracts precompiledContracts = new PrecompiledContracts(config, null, pegUtils);
    private final ActivationConfig.ForBlock activations = ActivationConfigsForTest.all().forBlock(0);

    private ProgramInvokeMockImpl invoke;
    private Program program;
    ThreadMXBean thread;
    VM vm;
    // To measure garbage collection time, we must use 100 million in maxLoops
    final static int million = 1000*1000;
    final static int maxLoops = 10*million;
    final static int maxGroups = 1;
    final static boolean useProfiler = false;

    // To execute as a standalone application, remove @before @after @test and run main.
    public static void main(String args[]) throws Exception {
        VMPerformanceTest vmpt = new VMPerformanceTest();
        vmpt.setup();
        vmpt.testVMPerformance1();
    }

    public static void runWithLogging(ResultLogger resultLogger) {
        VMPerformanceTest vmpt = new VMPerformanceTest();
        vmpt.setup();
        vmpt.testVMPerformance1(resultLogger);
    }

    @Before
    public void setup() {
        invoke = new ProgramInvokeMockImpl();
        long million=1000000;
        invoke.setGasLimit(1000*million);
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

    static Boolean shortArg = false;

    @Ignore
    @Test
    public void testVMPerformance1() {
        testVMPerformance1(null);
    }

    private void testVMPerformance1(ResultLogger resultLogger) {
        maxLLSize = 1000*1000*10;
        createLarseSetOfMemoryObjects();

        thread = ManagementFactory.getThreadMXBean();
        if (!thread.isThreadCpuTimeSupported()) return;

        Boolean old = thread.isThreadCpuTimeEnabled();
        thread.setThreadCpuTimeEnabled(true);
        vm = new VM(config.getVmConfig(), new PrecompiledContracts(config, null, pegUtils));
        if (useProfiler)
            waitForProfiler();

        System.out.println("Configuration: shortArg =  " + shortArg.toString());

        // Program
        measureProgram("PUSH/POP", Hex.decode("60A0" + "50"), 2, 2, 0, 100, null); // push "A0", pop it
        //measureOpcode(OpCode.NOT, false, 0); // For reference, to see general overhead
        //measureOpcode(OpCode.NOT, false,0); // Re-measure to see if JIT does something

        measure11Opcodes(resultLogger);
        measure21Opcodes(resultLogger);
        measure31Opcodes(resultLogger);
        thread.setThreadCpuTimeEnabled(old);
    }

    void measure21Opcodes(ResultLogger resultLogger) {
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
        for (int i = 0; i < simpleOpcodes.length; i++) {
            measureOpcode(simpleOpcodes[i], false, refTime21, resultLogger);
        }
    }

    void measure31Opcodes(ResultLogger resultLogger) {
        long refTime31 = measureOpcode(OpCode.ADDMOD, true, 0, null);
        measureOpcode(OpCode.ADDMOD, false, refTime31, resultLogger);
        measureOpcode(OpCode.MULMOD, false, refTime31, resultLogger);
    }

    void measure11Opcodes(ResultLogger resultLogger) {
        long refTime11 = measureOpcode(OpCode.ISZERO, true, 0, null);
        measureOpcode(OpCode.ISZERO, false, refTime11, resultLogger);
        measureOpcode(OpCode.NOT, false, refTime11, resultLogger);
    }

    public long measureOpcode(OpCode opcode, Boolean reference, long refTime, ResultLogger resultLogger) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int iCount = 0;
        DataWord maxValue = DataWord.ZERO;
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
                    maxValue = maxValue.sub(DataWord.ONE);
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
        public long deltaRealTimeMillis;
        public long deltaGCTimeMillis;
        public long wallClockTimeMillis; // in milliseconds

        // Delta time is de difference in thread time
        public long deltaTime_nS; // in nanoseconds.
        public long gas;
    }

    public interface ResultLogger {
        void log(String name, PerfRes result);
    }

    byte[] getClonedCode(byte[] code,int cloneCount) {
        // Repeat program 100 times to reduce the overhead of clearing the stack
        // the program must not loop.
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (int i = 0; i < cloneCount; i++)
            baos.write(code, 0, code.length);
        byte[] newCode = baos.toByteArray();
        return newCode;
    }

    public long measureProgram(String opcode, byte[] code, int insCount, int divisor, long refTime, int cloneCount, ResultLogger resultLogger) {

        byte[] newCode = getClonedCode(code,cloneCount);

        program = new Program(vmConfig, precompiledContracts, blockFactory, activations, newCode, invoke, null, new HashSet<>());
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
            long startGCTime = getGarbageCollectorTimeMillis();

            // Al parecer el gc puede interrumpir en cualquier momento y hacer estragos
            // Mejor que repetir y tomar el promedio es ejecutar muchas veces y quedarse con
            // el menor tiempo logrado

            for (int loops = 0; loops < myLoops; loops++) {
                vm.steps(program, insCount);

                // trick: Now force the program to restart, clear stack
                if (loops == 0) {
                    //  endGas is the gas used BOTH for the reference code
                    // (push/pops) and the cod itself.
                    long endGas = program.getResult().getGasUsed();

                    // Store the gas per inner loop, not vm.steps()
                    pr.gas = (endGas - startGas) / cloneCount;
                }
                program.restart();

            }

            long endTime = thread.getCurrentThreadCpuTime();
            long endRealTime = System.currentTimeMillis();
            long endGCTime = getGarbageCollectorTimeMillis();

            // Delta times are divided by the number of repetitions
            pr.deltaTime_nS = (endTime - startTime) / maxLoops / divisor ; // nano
            pr.wallClockTimeMillis = (endRealTime - startRealTime);
            pr.deltaRealTimeMillis = (endRealTime - startRealTime) * 1000 *1000 / maxLoops / divisor; // de milli a nano
            pr.deltaGCTimeMillis = (endGCTime- startGCTime)*1000*1000/ maxLoops / divisor; // nanos

            long endUsedMemory = (rt.totalMemory() - rt.freeMemory());

            pr.deltaUsedMemory = endUsedMemory - startUsedMemory;
            if ((best == null) || (pr.deltaTime_nS < best.deltaTime_nS)) {
                best = pr;
                if (best.deltaTime_nS == 0)
                    System.out.println("bad time");
                pr = new PerfRes();
            }
        }
        long percent;
        if (refTime != 0)
            percent = (best.deltaTime_nS - refTime) * 100 / refTime;
        else
            percent = 0;

        int blockGasLimit = 7 * 1000 * 1000;
        long loopsPerBlock = blockGasLimit / best.gas;
        int spaces =4;
        double memToBlkMem = 1.0*loopsPerBlock/maxLoops/1000;
        long blockTime =loopsPerBlock*best.deltaRealTimeMillis /1000/1000;

        //  the gas is the gas used BOTH for the reference code (push/ pops)
        // and the instruction being evaluated itself.
        // which is the common case in normal code. As all opcodes push less
        // stack elements that they consume, every opcode will have at least an
        // associated PUSH.

        String nsPerGasUnit = String.format("%.02f", best.deltaTime_nS *1.0/ best.gas);
        System.out.println(
                padRight(opcode, 12) + ":" +
                        " wcT[ms]: "+padLeft(Long.toString(best.wallClockTimeMillis), spaces) +
                        "| full[ns]: " + padLeft(Long.toString(best.deltaTime_nS), spaces) +
                        "| ref[ns]: " + padLeft(Long.toString(best.deltaTime_nS - refTime), spaces) +
                        "| (%ref): " + padLeft(Long.toString(percent), 5) +
                        "| gas: " + padLeft(Long.toString(best.gas), 5) +
                        "| blkTime[ms]: "+padLeft(Long.toString(blockTime), spaces) +
                        "| T/gas[ns]: " + padLeft(nsPerGasUnit, spaces) +
                        "| real[ns]: " + padLeft(Long.toString(best.deltaRealTimeMillis), spaces) +
                        "| gcT[ns]: " + padLeft(Long.toString(best.deltaGCTimeMillis), spaces) +
                        "| memPerBlk[Kb]: " + padLeft(Long.toString(Math.round(best.deltaUsedMemory*memToBlkMem)), spaces+1));

        long memPerBlockMegabytes = Math.round(best.deltaUsedMemory*memToBlkMem/1000);
        Assert.assertTrue(blockTime<PerformanceTestConstants.maxBlockProcessingTimeMillis);
        Assert.assertTrue(memPerBlockMegabytes <PerformanceTestConstants.maxMegabytesConsumedPerBlock);

        if (resultLogger != null) {
            resultLogger.log(opcode, best);
        }

        return best.deltaTime_nS;
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
            //System.out.println("Time elapsed [us]: " + Long.toString(deltaTime_nS)+" [s]:"+ Long.toString(deltaTime_nS/1000/1000));
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

        vm = new VM(config.getVmConfig(), new PrecompiledContracts(config, null, pegUtils));
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

        program =new Program(vmConfig, precompiledContracts, blockFactory, activations, code, invoke, null, new HashSet<>());

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
        assertEquals(s_expected_1, ByteUtil.toHexString(actualHReturn).toUpperCase());
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
        vm = new VM(config.getVmConfig(), new PrecompiledContracts(config, null, pegUtils));
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
        program = new Program(vmConfig, precompiledContracts, blockFactory, activations, code, invoke, null, new HashSet<>());
        System.out.println("-----------------------------------------------------------------------------");
        System.out.println("Starting test....");
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
        assertEquals(s_expected, ByteUtil.toHexString(actualHReturn).toUpperCase());
        System.out.println("-----------------------------------------------------------------------------");
    }
    /* TEST CASE LIST END */
}
