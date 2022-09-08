package co.rsk.vm;

import co.rsk.config.TestSystemProperties;
import co.rsk.config.VmConfig;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.peg.RepositoryBtcBlockStoreWithCache;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Account;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.VM;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.invoke.ProgramInvokeMockImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.HashSet;

import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP140;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class ExtCodeHashTest {
    public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
    private ActivationConfig.ForBlock activationConfig;
    private ProgramInvokeMockImpl invoke = new ProgramInvokeMockImpl();
    private BytecodeCompiler compiler = new BytecodeCompiler();
    private final TestSystemProperties config = new TestSystemProperties();
    private final VmConfig vmConfig = config.getVmConfig();
    private final PrecompiledContracts precompiledContracts = new PrecompiledContracts(
            config,
            new BridgeSupportFactory(
                    new RepositoryBtcBlockStoreWithCache.Factory(
                            config.getNetworkConstants().getBridgeConstants().getBtcParams()),
                    config.getNetworkConstants().getBridgeConstants(),
                    config.getActivationConfig()));
    private final BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());
    private final Transaction transaction = createTransaction();

    @BeforeEach
    public void setup() {
        activationConfig = mock(ActivationConfig.ForBlock.class);
        when(activationConfig.isActive(RSKIP140)).thenReturn(true);
    }

    @Test
    public void testDoEXTCODEHASHWithHardForkDeactivated() {
        when(activationConfig.isActive(RSKIP140)).thenReturn(false);
        Assertions.assertThrows(Program.IllegalOperationException.class, () -> executeExtCodeHash("0x471fd3ad3e9eeadeec4608b92d16ce6b500704cc", 0,
                null));
    }

    @Test
    public void testDoEXTCODEHASHToContractAndGetTheCodeHash() {
        byte[] resultCode = invoke.getRepository().getCode(invoke.getContractAddress());
        executeExtCodeHash("0x471fd3ad3e9eeadeec4608b92d16ce6b500704cc", 403,
                Keccak256Helper.keccak256(resultCode));
    }

    @Test
    public void testDoEXTCODEHASHToAccountAndGetEmptyHash() {
        executeExtCodeHash("0xcd2a3d9f938e13cd947ec05abc7fe734df8dd826", 403,
                Keccak256Helper.keccak256(EMPTY_BYTE_ARRAY));

    }

    @Test
    public void testDoEXTCODEHASHToNonExistentAccountAndGetZero() {
        executeExtCodeHash("0x1111111111222222222233333333334444444444", 403,
                ByteUtil.intToBytes(0));
    }

    @Test
    public void testDoEXTCODEHASHToPrecompiledContractAndGetEmptyHash() {
        executeExtCodeHash("0x"+precompiledContracts.BRIDGE_ADDR.toHexString(), 403,
                Keccak256Helper.keccak256(EMPTY_BYTE_ARRAY));
    }

    @Test
    public void testDoEXTCODEHASHWithoutArguments() {
        int gasExpected = 1000000;

        String stringCode = " EXTCODEHASH";
        byte[] code = compiler.compile(stringCode);
        VM vm = new VM(vmConfig, precompiledContracts);

        Program program = new Program(vmConfig, precompiledContracts, blockFactory, activationConfig, code, invoke, transaction, new HashSet<>());
        try {
            while (!program.isStopped()) {
                vm.step(program);
            }
        } catch(Program.StackTooSmallException e) {
             Assertions.assertEquals(0, program.getStack().size());
             Assertions.assertEquals(gasExpected, program.getResult().getGasUsed());
         }
    }

    private void executeExtCodeHash(String destAddress, int gasExpected, byte[] codeHashExpected) {
        String stringCode = " PUSH20 " + destAddress +
                " EXTCODEHASH";

        byte[] code = compiler.compile(stringCode);
        VM vm = new VM(vmConfig, precompiledContracts);

        Program program = new Program(vmConfig, precompiledContracts, blockFactory, activationConfig, code, invoke, transaction, new HashSet<>());

        while (!program.isStopped()) {
            vm.step(program);
        }

        Assertions.assertEquals(1, program.getStack().size());

        DataWord dataWordResult = program.stackPop();
        Assertions.assertEquals(DataWord.valueOf(codeHashExpected), dataWordResult);
        Assertions.assertEquals(gasExpected, program.getResult().getGasUsed());
    }


    private static Transaction createTransaction() {
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
