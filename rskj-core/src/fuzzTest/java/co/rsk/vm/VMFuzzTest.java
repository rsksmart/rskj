package co.rsk.vm;

import co.rsk.config.TestSystemProperties;
import co.rsk.config.VmConfig;
import co.rsk.pcc.NativeContract;
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
import java.io.ByteArrayOutputStream;
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

    private final long DEFAULT_TIMEOUT = 10;
    private final long DEFAULT_GAS = 6_800_000L;

    @FunctionalInterface
    interface BytecodeGenerator {
        byte[] generate(FuzzedDataProvider data, ActivationConfig.ForBlock activations);
    }

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
            executeCodeWithTimeout(code, activations, transaction, 15);
        } catch (TimeoutException e) {
            throw new FuzzerSecurityIssueHigh("VM execution timed out, indicating a potential DoS issue [input size = " + fuzzedInputSize + "]");
        } catch (InterruptedException | ExecutionException e) {
            Assertions.fail("Exception occurred during VM execution: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void fuzzVmWithGenerator(FuzzedDataProvider data, BytecodeGenerator generator) {
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.reed800().forBlock(0);
        byte[] fuzzedCode = generator.generate(data, activations);
        Transaction transaction = createTransaction();
        
        try {
            executeCodeWithTimeout(fuzzedCode, activations, transaction, DEFAULT_TIMEOUT);
        } catch (TimeoutException e) {
            throw new FuzzerSecurityIssueHigh("VM execution timed out during structured bytecode fuzzing. Code: " + ByteUtil.toHexString(fuzzedCode));
        } catch (InterruptedException | ExecutionException e) {
            throw new FuzzerSecurityIssueHigh("VM execution interrupted or execution exception during structured bytecode fuzzing. Code: " + ByteUtil.toHexString(fuzzedCode));
        }
    }

    @Tag("coRSKVMFuzzTestFuzzVmPrecompiledContracts")
    @FuzzTest
    void fuzzVmPrecompiledContracts(FuzzedDataProvider data) {
        fuzzVmWithGenerator(data, this::generatePrecompiledContractCallBytecode);
    }

    @Tag("coRSKVMFuzzTestFuzzVmMathOperations")
    @FuzzTest
    void fuzzVmMathOperations(FuzzedDataProvider data) {
        fuzzVmWithGenerator(data, (d, a) -> generateMathOperationBytecode(d));
    }

    @Tag("coRSKVMFuzzTestFuzzVmRandomValidBytecode")
    @FuzzTest
    void fuzzVmRandomValidBytecode(FuzzedDataProvider data) {
        fuzzVmWithGenerator(data, (d, a) -> generateRandomValidBytecode(d));
    }

    private byte[] generateRandomValidBytecode(FuzzedDataProvider data) {
        int codeLength = data.consumeInt(1, 512);
        ByteArrayOutputStream body = new ByteArrayOutputStream();
    
        while (body.size() < codeLength) {
            byte opcode = data.consumeByte();
            // Avoid terminal opcodes to ensure repetition
            if (opcode == OpCode.STOP.val() || opcode == OpCode.RETURN.val() || opcode == OpCode.REVERT.val() || opcode == OpCode.SUICIDE.val()) {
                opcode = OpCode.GAS.val();
            }
            body.write(opcode);
    
            // For PUSH opcodes, add the corresponding number of bytes
            if (opcode >= OpCode.PUSH1.val() && opcode <= OpCode.PUSH32.val()) {
                int pushBytes = opcode - OpCode.PUSH1.val() + 1;
                byte[] pushData = data.consumeBytes(pushBytes);
                if (pushData.length < pushBytes) {
                    byte[] paddedData = new byte[pushBytes];
                    System.arraycopy(pushData, 0, paddedData, 0, pushData.length);
                    body.write(paddedData, 0, paddedData.length);
                } else {
                    body.write(pushData, 0, pushData.length);
                }
            }
        }
    
        // Wrap body into a self-loop
        ByteArrayOutputStream wrapped = new ByteArrayOutputStream();
        int loopStart = wrapped.size();
        wrapped.write(OpCode.JUMPDEST.val());
        byte[] bodyBytes = body.toByteArray();
        wrapped.write(bodyBytes, 0, bodyBytes.length);
        wrapped.write(OpCode.PUSH3.val());
        wrapped.write((byte) ((loopStart >> 16) & 0xFF));
        wrapped.write((byte) ((loopStart >> 8) & 0xFF));
        wrapped.write((byte) (loopStart & 0xFF));
        wrapped.write(OpCode.JUMP.val());
    
        return wrapped.toByteArray();
    }

    private byte[] generateMathOperationBytecode(FuzzedDataProvider data) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
    
        // Two random values to the stack
        byte[] val1 = data.consumeBytes(data.consumeInt(1, 32));
        body.write(OpCode.PUSH32.val());
        body.write(new byte[32 - val1.length], 0, 32 - val1.length);
        body.write(val1, 0, val1.length);
    
        byte[] val2 = data.consumeBytes(data.consumeInt(1, 32));
        body.write(OpCode.PUSH32.val());
        body.write(new byte[32 - val2.length], 0, 32 - val2.length);
        body.write(val2, 0, val2.length);
    
        byte mathOpcode = data.pickValue(
                new byte[]{OpCode.ADD.val(), OpCode.MUL.val(), OpCode.SUB.val(), OpCode.DIV.val(), OpCode.SDIV.val(),
                        OpCode.MOD.val(), OpCode.SMOD.val(), OpCode.EXP.val(), OpCode.SIGNEXTEND.val()});
        body.write(mathOpcode);
    
        // Pop result
        body.write(OpCode.POP.val());
    
        // Wrap body into a self-loop
        ByteArrayOutputStream wrapped = new ByteArrayOutputStream();
        int loopStart = wrapped.size();
        wrapped.write(OpCode.JUMPDEST.val());
        byte[] bodyBytes = body.toByteArray();
        wrapped.write(bodyBytes, 0, bodyBytes.length);
        wrapped.write(OpCode.PUSH3.val());
        wrapped.write((byte) ((loopStart >> 16) & 0xFF));
        wrapped.write((byte) ((loopStart >> 8) & 0xFF));
        wrapped.write((byte) (loopStart & 0xFF));
        wrapped.write(OpCode.JUMP.val());
    
        return wrapped.toByteArray();
    }

    private byte[] generatePrecompiledContractCallBytecode(FuzzedDataProvider data, ActivationConfig.ForBlock activations) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        String[] candidates = new String[] {
                PrecompiledContracts.ECRECOVER_ADDR_STR,
                PrecompiledContracts.SHA256_ADDR_STR,
                PrecompiledContracts.RIPEMPD160_ADDR_STR,
                PrecompiledContracts.IDENTITY_ADDR_STR,
                PrecompiledContracts.BIG_INT_MODEXP_ADDR_STR,
                PrecompiledContracts.ALT_BN_128_ADD_ADDR_STR,
                PrecompiledContracts.ALT_BN_128_MUL_ADDR_STR,
                PrecompiledContracts.ALT_BN_128_PAIRING_ADDR_STR,
                PrecompiledContracts.BLAKE2F_ADDR_STR,
                PrecompiledContracts.BRIDGE_ADDR_STR,
                PrecompiledContracts.REMASC_ADDR_STR,
                PrecompiledContracts.HD_WALLET_UTILS_ADDR_STR,
                PrecompiledContracts.BLOCK_HEADER_ADDR_STR,
                PrecompiledContracts.ENVIRONMENT_ADDR_STR,
                PrecompiledContracts.SECP256K1_ADD_ADDR_STR,
                PrecompiledContracts.SECP256K1_MUL_ADDR_STR
        };

        String chosen = data.pickValue(candidates);
        boolean useValidMethod = data.consumeBoolean();
        byte[] validMethodSignature = null;
        if (useValidMethod) {
            DataWord chosenAddrDw = DataWord.valueFromHex(chosen);
            var contract = precompiledContracts.getContractForAddress(activations, chosenAddrDw);
            if (contract instanceof NativeContract) {
                var method = data.pickValue(((NativeContract) contract).getMethods());
                validMethodSignature = method.getFunction().encodeSignature();
            }
        }

        boolean useStaticCall = data.consumeBoolean();

        int inOff = 0;
        int inLen = data.consumeInt(0, 255);        
        int outLen = data.consumeInt(0, 255);
        int outOff = Math.min(255, inLen + 32); // Avoid overlap with input

        // Prepare input data in memory using CODECOPY from the end of code bytes
        byte[] inputData = emitInputDataPreambleWithCodesize(baos, data, inOff, inLen, validMethodSignature);

        // Loop start
        int loopStart = baos.size();
        baos.write(OpCode.JUMPDEST.val());

        // Push out_size, out_offset, in_size, in_offset
        baos.write(OpCode.PUSH1.val());
        baos.write((byte) (outLen & 0xFF));
        baos.write(OpCode.PUSH1.val());
        baos.write((byte) (outOff & 0xFF));
        baos.write(OpCode.PUSH1.val());
        baos.write((byte) (inLen & 0xFF));
        baos.write(OpCode.PUSH1.val());
        baos.write((byte) (inOff & 0xFF));

        if (!useStaticCall) {
            baos.write(OpCode.PUSH1.val());
            baos.write((byte) 0x00);
        }

        byte[] full20 = DatatypeConverter.parseHexBinary(chosen);

        baos.write(OpCode.PUSH20.val());
        baos.write(full20, 0, Math.min(20, full20.length));

        baos.write(OpCode.GAS.val());
        baos.write(useStaticCall ? OpCode.STATICCALL.val() : OpCode.CALL.val());

        // Pop result
        baos.write(OpCode.POP.val());

        // Jump back to loop start for infinite calling
        baos.write(OpCode.PUSH3.val());
        baos.write((byte) ((loopStart >> 16) & 0xFF));
        baos.write((byte) ((loopStart >> 8) & 0xFF));
        baos.write((byte) (loopStart & 0xFF));
        baos.write(OpCode.JUMP.val());

        // Embed input data at the end so CODECOPY can read it
        baos.write(inputData, 0, inputData.length);

        return baos.toByteArray();
    }

    private byte[] emitInputDataPreambleWithCodesize(ByteArrayOutputStream baos, FuzzedDataProvider data, int inOff, int inLen, byte[] validMethodSignature) {
        baos.write(OpCode.PUSH1.val());
        baos.write((byte) (inLen & 0xFF));
        baos.write(OpCode.DUP1.val());
        baos.write(OpCode.CODESIZE.val());
        // codeOffset = CODESIZE - inLen
        baos.write(OpCode.SUB.val());
        baos.write(OpCode.PUSH1.val());
        baos.write((byte) (inOff & 0xFF));
        baos.write(OpCode.CODECOPY.val());

        byte[] inputData;
        if (validMethodSignature != null && inLen >= validMethodSignature.length) {
            inputData = new byte[inLen];
            System.arraycopy(validMethodSignature, 0, inputData, 0, validMethodSignature.length);
            System.arraycopy(consumeExact(data, inLen - validMethodSignature.length), 0, inputData, validMethodSignature.length, inLen - validMethodSignature.length);
        } else {
            inputData = consumeExact(data, inLen);
        }
        return inputData;
    }

    private static byte[] consumeExact(FuzzedDataProvider data, int length) {
        if (length <= 0) {
            return new byte[0];
        }
        byte[] out = new byte[length];
        for (int i = 0; i < length; i++) {
            out[i] = data.consumeByte();
        }
        return out;
    }

    private void executeCodeWithTimeout(byte[] code, ActivationConfig.ForBlock activations, Transaction transaction, long timeout) throws TimeoutException, InterruptedException, ExecutionException {
        VM vm = new VM(vmConfig, precompiledContracts);
        invoke.setGas(DEFAULT_GAS);
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
        } catch (TimeoutException te) {
            future.cancel(true);
            throw te;
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    executor.awaitTermination(2, TimeUnit.SECONDS);
                }
            } catch (InterruptedException ie) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private Program executeCode(String code, int nsteps) {
        return executeCodeWithActivationConfig(compiler.compile(code), nsteps, mock(ActivationConfig.ForBlock.class));
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
