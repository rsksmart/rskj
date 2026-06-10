package co.rsk.vm;

import java.math.BigInteger;
import java.util.HashSet;

import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Account;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.VM;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.invoke.ProgramInvokeMockImpl;

import co.rsk.config.TestSystemProperties;
import co.rsk.config.VmConfig;
import co.rsk.core.RskAddress;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.peg.RepositoryBtcBlockStoreWithCache;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;

/**
 * Contains basic logic required to run tests against EVM smart contracts.
 *
 * The main focus here is raw EVM bytecode execution to test
 * how instructions and gas calculations works.
 */
public abstract class AbstractEvmTester {

    protected final BytecodeCompiler compiler = new BytecodeCompiler();

    protected final TestSystemProperties config = new TestSystemProperties();

    protected final SignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());

    protected final VmConfig vmConfig = config.getVmConfig();

    protected final BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());

    protected ActivationConfig activationConfig;

    protected ProgramInvokeMockImpl invoke;

    /**
     * Builds default TX and execute default smart contract within it's scope.
     */
    Program executeSmartContract() {
        return executeSmartContract(createTransaction(), invoke.getContractAddress());
    }

    /**
     * Execute smart contract that already loaded in storage by some address witin
     * some TX.
     */
    Program executeSmartContract(Transaction transaction, RskAddress smartContractAddress) {
        invoke.setOwnerAddress(smartContractAddress);
        byte[] smartContractBytes = invoke.getRepository().getCode(smartContractAddress);

        return executeSmartContract(transaction, smartContractBytes);
    }

    /**
     * Executes any arbitrary smart contract bytecode in a scope of a transaction.
     */
    Program executeSmartContract(Transaction transaction, byte[] smartContractBytes) {
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(
                        config.getNetworkConstants().getBridgeConstants().getBtcParams()),
                config.getNetworkConstants().getBridgeConstants(),
                config.getActivationConfig(),
                signatureCache);
        var precompiled = new PrecompiledContracts(config, bridgeSupportFactory, signatureCache);
        var vm = new VM(vmConfig, precompiled);

        ActivationConfig.ForBlock activation = activationConfig.forBlock(0);

        var program = new Program(
                vmConfig,
                precompiled,
                blockFactory,
                activation,
                smartContractBytes,
                invoke,
                transaction,
                new HashSet<>(),
                signatureCache);

        try {
            while (!program.isStopped()) {
                vm.step(program);
            }

            return program;
        } finally {
            invoke.getRepository().rootTxCompleted();
        }
    }

    static Transaction createTransaction() {
        AccountBuilder accountBuilder = new AccountBuilder();
        accountBuilder.name("sender");
        Account sender = accountBuilder.build();
        accountBuilder.name("receiver");
        Account receiver = accountBuilder.build();
        return new TransactionBuilder().sender(sender).receiver(receiver).value(BigInteger.valueOf(2000)).build();
    }

    void setSlotValue(RskAddress address, DataWord slot, DataWord value) {
        invoke.getRepository().addStorageRow(address, slot, value);
        invoke.getRepository().commit();
    }

    DataWord readSlotValue(RskAddress address, DataWord slot) {
        DataWord value = invoke.getRepository().getStorageValue(address, slot);
        return value == null ? DataWord.ZERO : value;
    }

}
