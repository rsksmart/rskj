/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
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
     * Builds default TX and execute default smart contract within its scope.
     */
    Program executeSmartContract() {
        return executeSmartContract(createTransaction(), invoke.getContractAddress());
    }

    /**
     * Execute smart contract that already loaded in storage by some address within
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
            invoke.getRepository().txFinalized();
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
