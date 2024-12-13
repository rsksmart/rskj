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

class MCopyInputTest {

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
    @MethodSource("provideParametersForOOGCases")
    void testMCopy_ShouldThrowOOGException(String[] initMemory, long dst, long src, long length) {
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

        // Then
        Program.OutOfGasException ex = Assertions.assertThrows(Program.OutOfGasException.class, () -> executeProgram(vm, program));
        Assertions.assertTrue(ex.getMessage().contains("Not enough gas for 'MCOPY' operation"));
    }

    private static Stream<Arguments> provideParametersForOOGCases() {
        return Stream.of(
                // Special Border Cases
                Arguments.of(new String[]{ "0000000000000000000000000000000000000000000000000000000000000000" }, 0, 0, -1),
                Arguments.of(new String[]{}, 0, 0, -(2 * (Long.MAX_VALUE / 3))),
                Arguments.of(new String[]{}, 0, 0, Integer.MAX_VALUE + 1L),
                // Max Memory Limits
                Arguments.of(new String[]{}, Program.MAX_MEMORY, 0, 1L),
                Arguments.of(new String[]{}, 0, Program.MAX_MEMORY, 1L),
                Arguments.of(new String[]{}, 0, 0, Program.MAX_MEMORY + 1),
                // Negative Length
                Arguments.of(new String[]{}, 0, 0, -1L)
        );
    }

    private static void executeProgram(VM vm, Program program) {
        try {
            while (!program.isStopped()) {
                vm.step(program);
            }
        } catch(Program.StackTooSmallException e) {
            Assertions.fail("Stack too small exception");
        }
    }

}