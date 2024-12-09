package co.rsk.vm.opcode;

import co.rsk.config.TestSystemProperties;
import co.rsk.config.VmConfig;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.peg.RepositoryBtcBlockStoreWithCache;
import co.rsk.vm.BytecodeCompiler;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.*;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.VM;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.invoke.ProgramInvokeMockImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashSet;
import java.util.stream.Stream;

import static co.rsk.net.utils.TransactionUtils.createTransaction;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP445;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MCopyGasTest {

    private ActivationConfig.ForBlock activationConfig;

    private final ProgramInvokeMockImpl invoke = new ProgramInvokeMockImpl();
    private final BytecodeCompiler compiler = new BytecodeCompiler();
    private final TestSystemProperties config = new TestSystemProperties();
    private final VmConfig vmConfig = config.getVmConfig();
    private final SignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
    private final BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());
    private final Transaction transaction = createTransaction();
    private final PrecompiledContracts precompiledContracts = new PrecompiledContracts(
            config,
            new BridgeSupportFactory(
                    new RepositoryBtcBlockStoreWithCache.Factory(
                            config.getNetworkConstants().getBridgeConstants().getBtcParams()),
                    config.getNetworkConstants().getBridgeConstants(),
                    config.getActivationConfig(), signatureCache), signatureCache);

    @BeforeEach
    void setup() {
        activationConfig = mock(ActivationConfig.ForBlock.class);
        when(activationConfig.isActive(RSKIP445)).thenReturn(true);
    }

    @ParameterizedTest
    @MethodSource("provideParametersForMCOPYGasTest")
    void testMCopy_ShouldConsumeTheCorrectAmountOfGas(String[] initMemory, int dst, int src, int length, int expectedGasUsage) {
        // Given
        byte[] code = compiler.compile("MCOPY");
        VM vm = new VM(vmConfig, precompiledContracts);

        Program program = new Program(vmConfig, precompiledContracts, blockFactory, activationConfig, code, invoke, transaction, new HashSet<>(), new BlockTxSignatureCache(new ReceivedTxSignatureCache()));

        int address = 0;
        for (String entry : initMemory) {
            program.memorySave(DataWord.valueOf(address), DataWord.valueFromHex(entry));
            address += 32;
        }

        program.stackPush(DataWord.valueOf(length)); // Mind the stack order!!
        program.stackPush(DataWord.valueOf(src));
        program.stackPush(DataWord.valueOf(dst));

        // When
        try {
            while (!program.isStopped()) {
                vm.step(program);
            }
        } catch(Program.StackTooSmallException e) {
            Assertions.fail("Stack too small exception");
        }

        // Then
        Assertions.assertEquals(0, program.getStack().size());
        Assertions.assertEquals(expectedGasUsage, program.getResult().getGasUsed());
    }

    private static Stream<Arguments> provideParametersForMCOPYGasTest() {
        return Stream.of(
                Arguments.of(new String[]{ "0000000000000000000000000000000000000000000000000000000000000000", "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f" }, 0, 32, 32, 6),
                Arguments.of(new String[]{ "0101010101010101010101010101010101010101010101010101010101010101" }, 0, 0, 32, 6),
                Arguments.of(new String[]{ "0001020304050607080000000000000000000000000000000000000000000000" }, 0, 1, 8, 6),
                Arguments.of(new String[]{ "0001020304050607080000000000000000000000000000000000000000000000" }, 1, 0, 8, 6),
                Arguments.of(new String[]{ "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f", "202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f", "404142434445464748494a4b4c4d4e4f505152535455565758595a5b5c5d5e5f", "606162636465666768696a6b6c6d6e6f707172737475767778797a7b7c7d7e7f", "808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f", "a0a1a2a3a4a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbdbebf", "c0c1c2c3c4c5c6c7c8c9cacbcccdcecfd0d1d2d3d4d5d6d7d8d9dadbdcdddedf", "e0e1e2e3e4e5e6e7e8e9eaebecedeeeff0f1f2f3f4f5f6f7f8f9fafbfcfdfeff" }, 256, 256, 1, 9),
                Arguments.of(new String[]{ "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f", "202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f", "404142434445464748494a4b4c4d4e4f505152535455565758595a5b5c5d5e5f", "606162636465666768696a6b6c6d6e6f707172737475767778797a7b7c7d7e7f", "808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f", "a0a1a2a3a4a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbdbebf", "c0c1c2c3c4c5c6c7c8c9cacbcccdcecfd0d1d2d3d4d5d6d7d8d9dadbdcdddedf", "e0e1e2e3e4e5e6e7e8e9eaebecedeeeff0f1f2f3f4f5f6f7f8f9fafbfcfdfeff" }, 0, 256, 256, 27)
        );
    }

}