package co.rsk.vm;

import co.rsk.config.TestSystemProperties;
import co.rsk.config.VmConfig;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.*;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.*;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.Stack;
import org.ethereum.vm.program.invoke.ProgramInvokeMockImpl;
import javax.xml.bind.DatatypeConverter;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.concurrent.*;

import static org.mockito.Mockito.mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;

import com.code_intelligence.jazzer.api.FuzzerSecurityIssueHigh;
import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;

import org.junit.jupiter.api.Tag;

class VMFuzzTest {
    private final TestSystemProperties config = new TestSystemProperties();
    private final PrecompiledContracts precompiledContracts = new PrecompiledContracts(config, null, new BlockTxSignatureCache(new ReceivedTxSignatureCache()));
    private final BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());
    private VmConfig vmConfig = config.getVmConfig();
    private ProgramInvokeMockImpl invoke;
    private BytecodeCompiler compiler;
    // see what makes more sense here
    private final long DEFAULT_TIMEOUT = 10;

    @BeforeEach
    void setup() {
        invoke = new ProgramInvokeMockImpl();
        compiler = new BytecodeCompiler();
    }

    // JAZZER_FUZZ=1 ./gradlew fuzzTest --tests co.rsk.vm.VMFuzzTest --info
    @Disabled("This fuzz target is disabled by default")
    @FuzzTest
    void testJazzerCanFindValue(FuzzedDataProvider data) {
        String val = ByteUtil.toHexString(new byte[] { data.consumeByte() });
        Program program = executeCode("PUSH1 0x" + val, 1);
        Stack stack = program.getStack();

        Assertions.assertNotEquals(DataWord.valueOf(0xab), stack.peek());
    }
    @Disabled("This fuzz target is disabled by default")
    @FuzzTest
    void testJazzerCanCatchDosIssue(FuzzedDataProvider data) {
        byte[] code = DatatypeConverter.parseHexBinary("5b6000600062108000600063010000095afa50600056");
        Transaction transaction = createTransaction();
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.arrowhead600().forBlock(0);

        try {
            executeCodeWithTimeout(code, activations, transaction, DEFAULT_TIMEOUT);
            Assertions.fail("VM execution completed within the timeout, but a DoS issue was expected.");
        } catch (TimeoutException e) {
            throw new FuzzerSecurityIssueHigh("VM execution timed out, indicating a potential DoS issue.");
        } catch (InterruptedException | ExecutionException e) {
            Assertions.fail("Exception occurred during VM execution: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Tag("coRSKVMFuzzTestNativeContractInputSize")
    @FuzzTest
    void fuzzNativeContractInputSize(FuzzedDataProvider data) {
        // Fuzz the input size (3 bytes, up to 16MB)
        int fuzzedInputSize = data.consumeInt(0, 0xFFFFFF);

        String fuzzedInputSizeHex = String.format("%06X", fuzzedInputSize);
        String bytecodeHex = "5b6000600062" + fuzzedInputSizeHex + "600063010000095afa50600056";
        byte[] code = DatatypeConverter.parseHexBinary(bytecodeHex);
        /* <FUZZING TEMPLATE>
         * 5b                  JUMPDEST
         * 6000                PUSH1 0x00 // OUTPUT SIZE
         * 6000                PUSH1 0x00 // OUTPUT INDEX
         * 62108000            PUSH3 FUZZ_RANGE[0, 0xFFFFFF] // INPUT SIZE
         * 6000                PUSH1 0x00 // INPUT INDEX
         * 63010000            PUSH4 0x01000009 // CONTRACT ADDR (HD_WALLET_UTILS_ADDR)
         * 5a                  GAS
         * fa                  STATICCALL
         * 50                  POP
         * 6000                PUSH1 0x00
         * 56                  JUMP
         */
        Transaction transaction = createTransaction();
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.arrowhead600().forBlock(0);

        try {
            executeCodeWithTimeout(code, activations, transaction, DEFAULT_TIMEOUT);
        } catch (TimeoutException e) {
            throw new FuzzerSecurityIssueHigh("VM execution timed out, indicating a potential DoS issue [input size = " + fuzzedInputSize + "]");
        } catch (InterruptedException | ExecutionException e) {
            Assertions.fail("Exception occurred during VM execution: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void executeCodeWithTimeout(byte[] code, ActivationConfig.ForBlock activations, Transaction transaction, long timeout) throws TimeoutException, InterruptedException, ExecutionException {
        VM vm = new VM(vmConfig, precompiledContracts);
        invoke.setGas(6800000);
        Program program = new Program(vmConfig, precompiledContracts, blockFactory, activations, code, invoke, transaction, new HashSet<>(), new BlockTxSignatureCache(new ReceivedTxSignatureCache()));
        ExecutorService executor = Executors.newSingleThreadExecutor();

        Callable<Void> vmTask = () -> {
            try {
                while (!program.isStopped()) {
                    vm.step(program);
                }
            } catch (RuntimeException e) {
                program.setRuntimeFailure(e);
            }
            return null;
        };

        Future<Void> future = executor.submit(vmTask);

        try {
            future.get(timeout, TimeUnit.SECONDS);
        } finally {
            executor.shutdown();
        }
    }

    private Program executeCode(String code, int nsteps) {
        return executeCodeWithActivationConfig(compiler.compile(code), nsteps, mock(ActivationConfig.ForBlock.class));
    }

    private void testCode(byte[] code, int nsteps, String expected) {
        Program program = executeCodeWithActivationConfig(code, nsteps, mock(ActivationConfig.ForBlock.class));

        assertEquals(expected, ByteUtil.toHexString(program.getStack().peek().getData()).toUpperCase());
    }

    private Program executeCodeWithActivationConfig(String code, int nsteps, ActivationConfig.ForBlock activations) {
        return executeCodeWithActivationConfig(compiler.compile(code), nsteps, activations);
    }

    private Program executeCodeWithActivationConfig(byte[] code, int nsteps, ActivationConfig.ForBlock activations) {
        VM vm = new VM(vmConfig, precompiledContracts);
        Program program = new Program(vmConfig, precompiledContracts, blockFactory, activations, code, invoke, null, new HashSet<>(), new BlockTxSignatureCache(new ReceivedTxSignatureCache()));

        for (int k = 0; k < nsteps; k++) {
            vm.step(program);
        }

        return program;
    }

    private Transaction createTransaction() {
        int number = 0;
        AccountBuilder acbuilder = new AccountBuilder();
        acbuilder.name("sender" + number);
        Account sender = acbuilder.build();
        acbuilder.name("receiver" + number);
        Account receiver = acbuilder.build();
        TransactionBuilder txbuilder = new TransactionBuilder();
        return txbuilder.sender(sender).receiver(receiver).value(BigInteger.valueOf(1000)).build();
    }
}