package co.rsk.vm;

import co.rsk.config.TestSystemProperties;
import co.rsk.config.VmConfig;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.helpers.PerformanceTestConstants;
import co.rsk.helpers.PerformanceTestHelper;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import org.apache.commons.lang3.StringUtils;
import org.ethereum.config.BlockchainConfig;
import org.ethereum.core.Account;
import org.ethereum.core.Transaction;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.VM;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.invoke.ProgramInvokeMockImpl;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.math.BigInteger;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by Sergio Demian Lerner on 12/10/2018.
 */
public class VMSpecificOpcodesPerformanceTest {

    private ProgramInvokeMockImpl invoke;
    private Program program;
    private VM vm;

    private final TestSystemProperties config = new TestSystemProperties();
    private final VmConfig vmConfig = config.getVmConfig();
    private final PrecompiledContracts precompiledContracts = new PrecompiledContracts(config);

    public static String padZeroesLeft(String s, int n) {
        return StringUtils.leftPad(s, n, '0');
    }

    int loopCount = 0x1a00;

    String getLoopCountStr() {
        return padZeroesLeft(Long.toHexString(loopCount), 4);
    }

    @Ignore
    @Test
    public void testCallsToExistentAccounts() {
        int gasCostPerCALLLoop = 755;
        RskAddress shortAddress = new RskAddress("0000000000000000000000000000000000100000");
        invoke.addAccount(shortAddress,new Coin(BigInteger.valueOf(1)));

        // This code executes a CALL 256 times, to 256 different non-existing contracts
        // The cost of CALL opcode to a non-existent contract is 25K
        program = getProgram(
                compile(" PUSH3 0x100000 " +
                        " PUSH1 0x00 " + // counter
                        " JUMPDEST " +
                        " PUSH1 0x00" +
                        " PUSH1 0x00" +
                        " PUSH1 0x00" +
                        " PUSH1 0x00" +
                        " PUSH1 0x00" +
                        " DUP7 " +
                        " PUSH2 0x2000" +
                        " CALL " +
                        " POP " + // call result
                        // top of the stack is loop iterator
                        " PUSH1 0x01" +
                        " ADD " +
                        // tos is loop iterator
                        " DUP1 " +
                        // tos: ITE ITE
                        " PUSH2 0x" +getLoopCountStr()+
                        " EQ " +
                        // tos: EQ-result ITE
                        " ISZERO " +
                        " PUSH1 0x06" +
                        " JUMPI "
                ), createTransaction(0));

        program.fullTrace();
        PerformanceTestHelper stopWatch = new PerformanceTestHelper();
        stopWatch.setup();
        stopWatch.startMeasure();
        vm.steps(program, Long.MAX_VALUE);
        stopWatch.endMeasure("program execution");
        //
        printResults(stopWatch,gasCostPerCALLLoop);
    }

    @Ignore
    @Test
    public void testCallsToNonExistingContracts() {
        int gasCostPerCALLLoop = 25755;

        // This code executes a CALL 256 times, to 256 different non-existing contracts
        // The cost of CALL opcode to a non-existent contract is 25K

        program = getProgram(
                compile(" PUSH5 0x1000000000 " +
                        " PUSH1 0x00 " + // counter
                        " JUMPDEST " +
                        " PUSH1 0x00" +
                        " PUSH1 0x00" +
                        " PUSH1 0x00" +
                        " PUSH1 0x00" +
                        " PUSH1 0x00" +
                        " DUP7 " +
                        " PUSH2 0x2000" +
                        " CALL " +
                        " POP " + // call result
                        // top of the stack is loop iterator
                        " PUSH1 0x01" +
                        " ADD " +
                        // tos is loop iterator
                        " DUP1 " +
                        // tos: ITE ITE
                        " PUSH2 0x" +getLoopCountStr()+
                        " EQ " +
                        // tos: EQ-result ITE
                        " ISZERO " +
                        " PUSH1 0x08" +
                        " JUMPI "
                ), createTransaction(0));

        program.fullTrace();
        PerformanceTestHelper stopWatch = new PerformanceTestHelper();
        stopWatch.setup();
        stopWatch.startMeasure();
        vm.steps(program, Long.MAX_VALUE);
        stopWatch.endMeasure("program execution");
        //
        printResults(stopWatch,gasCostPerCALLLoop);
    }

    void printResults(PerformanceTestHelper stopWatch,int gasCostPerCALLLoop) {

        long CALLloopNanos = stopWatch.getDeltaTime() / loopCount ;
        System.out.println("Time per CALL [uS]: " + CALLloopNanos / 1000 + " uS per CALL");
        int blockGasLimit = PerformanceTestConstants.blockGasLimit;
        int CALLloopsPerBlock = blockGasLimit / gasCostPerCALLLoop;
        long blockTime =  CALLloopsPerBlock * CALLloopNanos / 1000 / 1000;
        System.out.println("Block gas limit: " + blockGasLimit);
        System.out.println("CALL loops per block: " + CALLloopsPerBlock);
        System.out.println("Block process time [ms]: " +blockTime);
        // A block should take less than 400 msec to process
        Assert.assertTrue(blockTime<PerformanceTestConstants.maxBlockProcessingTimeMillis);
    }

    private Program getProgram(byte[] code, Transaction transaction) {
        return new Program(vmConfig, precompiledContracts, getBlockchainConfig(), code, invoke, transaction);
    }

    private byte[] compile(String code) {
        return new BytecodeCompiler().compile(code);
    }

    private static Transaction createTransaction(int number) {
        AccountBuilder acbuilder = new AccountBuilder();
        acbuilder.name("sender" + number);
        Account sender = acbuilder.build();
        acbuilder.name("receiver" + number);
        Account receiver = acbuilder.build();
        TransactionBuilder txbuilder = new TransactionBuilder();
        return txbuilder.sender(sender).receiver(receiver).value(BigInteger.valueOf(number * 1000 + 1000)).build();
    }

    private BlockchainConfig getBlockchainConfig() {
        BlockchainConfig blockchainConfig = mock(BlockchainConfig.class);
        when(blockchainConfig.isRskip91()).thenReturn(true);
        when(blockchainConfig.isRskip90()).thenReturn(true);
        when(blockchainConfig.isRskip89()).thenReturn(true);
        return blockchainConfig;
    }

    private VM getSubject() {
        return new VM(vmConfig, precompiledContracts);
    }

    @Before
    public void setup() {
        vm = getSubject();
        invoke = new ProgramInvokeMockImpl();
        invoke.setGas(50*1000*1000); //
    }
}
