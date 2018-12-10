package co.rsk.vm;

import co.rsk.config.TestSystemProperties;
import co.rsk.config.VmConfig;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import org.ethereum.config.BlockchainConfig;
import org.ethereum.core.Account;
import org.ethereum.core.Transaction;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.VM;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.invoke.ProgramInvokeMockImpl;
import org.junit.Before;
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


    @Test
    public void testMultiCalls() {
        // This code executes a CALL 256 times, to 256 different non-existing contracts
        program = getProgram(
                compile(" PUSH5 0x1000000000 "+
                        " PUSH1 0x00 "+ // counter
                        " JUMPDEST "+
                        " PUSH1 0x00" +
                        " PUSH1 0x00" +
                        " PUSH1 0x00" +
                        " PUSH1 0x00" +
                        " PUSH1 0x00" +
                        " DUP7 "+
                        " PUSH2 0x2000" +
                        " CALL "+
                        " POP "+ // call result
                        // top of the stack is loop iterator
                        " PUSH1 0x01"+
                        " ADD "+
                        // tos is loop iterator
                        " DUP1 "+
                        // tos: ITE ITE
                        " PUSH2 0x0100"+
                        " EQ "+
                        // tos: EQ-result ITE
                        " ISZERO "+
                        " PUSH1 0x08"+
                        " JUMPI "
                ), createTransaction(0));

        program.fullTrace();

        vm.steps(program, Long.MAX_VALUE);

        //
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
    }
}
