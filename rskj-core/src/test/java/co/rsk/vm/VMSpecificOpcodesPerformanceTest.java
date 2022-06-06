package co.rsk.vm;

import co.rsk.config.TestSystemProperties;
import co.rsk.config.VmConfig;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.helpers.PerformanceTestConstants;
import co.rsk.helpers.Stopwatch;
import co.rsk.peg.BridgeSerializationUtils;
import co.rsk.peg.BridgeUtils;
import co.rsk.peg.utils.ScriptBuilderWrapper;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import org.apache.commons.lang3.StringUtils;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Account;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.Transaction;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.VM;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.invoke.ProgramInvokeMockImpl;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.math.BigInteger;
import java.util.HashSet;

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
    private final BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());
    private final VmConfig vmConfig = config.getVmConfig();
    private final BridgeUtils bridgeUtils = BridgeUtils.getInstance();
    private final ScriptBuilderWrapper scriptBuilderWrapper = ScriptBuilderWrapper.getInstance();
    private final BridgeSerializationUtils bridgeSerializationUtils = BridgeSerializationUtils.getInstance(scriptBuilderWrapper);
    private final PrecompiledContracts precompiledContracts = new PrecompiledContracts(config, null, bridgeUtils, bridgeSerializationUtils);

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

        // This code executes a CALL loopCount times, to loopCount different non-existing contracts
        // The cost of CALL opcode to a non-existent contract is 25K
        program = getProgram(
                compile(" PUSH3 0x100000 " +
                        " PUSH1 0x00 " + // initialize counter
                        " JUMPDEST " +    // ***
                        " PUSH1 0x00" +   // Out data size for CALL
                        " PUSH1 0x00" +   // Out data start for CALL
                        " PUSH1 0x00" +   // In data size for CALL
                        " PUSH1 0x00" +   // In data start for CALL
                        " PUSH1 0x00" +   // Value sent in CALL
                        " DUP7 " +        // Destination address for CALL
                        " PUSH2 0x2000" + // Gas for CALL
                        " CALL " +        // Self-explanatory
                        " POP " +         // call result
                        // top of the stack is loop iterator
                        " PUSH1 0x01" +
                        " ADD " +        // Add one to the iterator
                        // tos is loop iterator
                        " DUP1 " +
                        // tos: ITE ITE
                        " PUSH2 0x" +getLoopCountStr()+ // push loopCount to compare
                        " EQ " +                        // Is it the end ?
                        // tos: EQ-result ITE
                        " ISZERO " +                    // negate
                        " PUSH1 0x06" +                 // offset to jump (see ***)
                        " JUMPI "                       // conditional jump
                ), createTransaction(0));

        program.fullTrace();
        Stopwatch stopWatch = new Stopwatch();
        stopWatch.setup();
        stopWatch.startMeasure();
        vm.steps(program, Long.MAX_VALUE);
        stopWatch.endMeasure("program execution");
        //
        long gasUsed =program.getResult().getGasUsed();
        long computedGasCostPerCALLLoop =gasUsed / loopCount; // number of loops
        Assert.assertEquals(computedGasCostPerCALLLoop ,gasCostPerCALLLoop);


        printResults(stopWatch,gasCostPerCALLLoop);
    }

    @Ignore
    @Test
    public void testCallsToNonExistingContracts() {
        // IMPORTANT NODE
        // in RSK calling a non-existent contract IMMEDIATELY CREATES IT.
        // This does not depend on the value transferred.
        // It will create it anyway.
        // In Ethereum, the contract is not created if the value transferred is zero.
        //
        int gasCostPerCALLLoop = 25769;

        // This code executes a CALL loopCount times, to loopCount different non-existing contracts
        // The cost of CALL opcode to a non-existent contract is 25K
        // the address that is being called (0x1000000000) does not exists
        program = getProgram(

                compile(" PUSH5 0x1000000000 " + // Address of contract to CALL
                        " PUSH1 0x00 " +  // counter
                        " JUMPDEST " +    // ***
                        " PUSH1 0x00" +   // Out data size for CALL
                        " PUSH1 0x00" +   // Out data start for CALL
                        " PUSH1 0x00" +   // In data size for CALL
                        " PUSH1 0x00" +   // In data start for CALL
                        " PUSH1 0x00" +   // Value sent in CALL
                        " DUP7 " +        // Destination address for CALL
                        " PUSH1 0x01" +   // ADD one to the contract address
                        " ADD " +         // ADD one to it
                        " SWAP7 " +       // save it back
                        " POP  " +        // dispose previous value
                        " DUP7 " +        // Bring back the incremented address
                        " PUSH2 0x2000" + // Gas for CALL
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
        Stopwatch stopWatch = new Stopwatch();
        stopWatch.setup();
        stopWatch.startMeasure();
        vm.steps(program, Long.MAX_VALUE);
        stopWatch.endMeasure("program execution");
        //
        long gasUsed =program.getResult().getGasUsed();
        long computedGasCostPerCALLLoop =gasUsed / loopCount; // number of loops
        Assert.assertEquals(computedGasCostPerCALLLoop ,gasCostPerCALLLoop);

        printResults(stopWatch,gasCostPerCALLLoop);
    }

    void printResults(Stopwatch stopWatch, int gasCostPerCALLLoop) {

        long CALLloopNanos = stopWatch.getDeltaTime_ns() / loopCount ;
        System.out.println("Time per CALL [uS]: " + CALLloopNanos / 1000 + " uS per CALL");
        int blockGasLimit = PerformanceTestConstants.blockGasLimit;
        int CALLloopsPerBlock = blockGasLimit / gasCostPerCALLLoop;
        long blockTime =  CALLloopsPerBlock * CALLloopNanos / 1000 / 1000;

        System.out.println("Block gas limit: " + blockGasLimit);
        System.out.println("CALL loops per block: " + CALLloopsPerBlock);
        System.out.println("Block process time [ms]: " +blockTime);
        String nsPerGasUnit = String.format("%.02f", CALLloopNanos *1.0/ gasCostPerCALLLoop);
        System.out.println("Time/gas for CALL lopp [ns]: " +nsPerGasUnit);
        // A block should take less than 400 msec to process
        Assert.assertTrue(blockTime<PerformanceTestConstants.maxBlockProcessingTimeMillis);
    }

    private Program getProgram(byte[] code, Transaction transaction) {
        return new Program(vmConfig, precompiledContracts, blockFactory, getBlockchainConfig(), code, invoke, transaction, new HashSet<>());
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

    private ActivationConfig.ForBlock getBlockchainConfig() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP91)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP90)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP89)).thenReturn(true);
        return activations;
    }

    private VM getSubject() {
        return new VM(vmConfig, precompiledContracts);
    }

    @Before
    public void setup() {
        vm = getSubject();
        invoke = new ProgramInvokeMockImpl();
        invoke.setGas(500*1000*1000); //
    }
}
