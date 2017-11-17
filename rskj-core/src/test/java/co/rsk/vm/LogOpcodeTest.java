package co.rsk.vm;


import org.ethereum.vm.VM;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.invoke.ProgramInvokeMockImpl;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

import static org.junit.Assert.assertEquals;

/**
 * Created by SerAdmin on 11/17/2017.
 */
public class LogOpcodeTest {
    private ProgramInvokeMockImpl invoke;
    private BytecodeCompiler compiler;
    private Program program;
    @Before
    public void setup() {

        invoke = new ProgramInvokeMockImpl();
        compiler = new BytecodeCompiler();
    }

    @After
    public void tearDown() {
        invoke.getRepository().close();
    }

    @Test
    public void testLogNewAccountTree() {
        testCode(
                "CALLVALUE "   // Store value in memory position 0
                +"PUSH1 0x00 " // Offset in memory to store
                +"MSTORE " // store in memory
        +"PUSH1 0x00 "
            +"NOT " // 1st topic: Topic 0xff......0xff
            +"PUSH1 0x12 " // 2nd topic: Sample topic
            +"CALLER " // 3nd topic: source address
            +"LASTEVENTBLOCKNUMBER "  // 4th topic
            +"PUSH1 0x20 " // memSize
            +"PUSH1 0x00 " // memStart
            +"LOG4 "
            +"LASTEVENTBLOCKNUMBER", 12,
                "0000000000000000000000000000000000000000000000000000000000000021"); // 0x21 = 33 is the mock block number
    // Gasused = 2163
      // Now check that the last event has been set to 33.
        // This means that a light client must always fetch two consecutive headers, in the first
        // it finds the previous event block number. In the second, the logged event


    }

    private void testCode(String code, int nsteps, String expected) {
        testCode(compiler.compile(code), nsteps, expected);
    }

    private void runCode(String code, int nsteps) {
        runCode(compiler.compile(code), nsteps);
    }

    private void runCode(byte[] code, int nsteps) {
        VM vm = new VM();
        program = new Program(code, invoke);

        for (int k = 0; k < nsteps; k++)
            vm.step(program);
    }

    private void testCode(byte[] code, int nsteps, String expected) {
        VM vm = new VM();
        program = new Program(code, invoke);

        for (int k = 0; k < nsteps; k++)
            vm.step(program);

        assertEquals(expected, Hex.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

}
