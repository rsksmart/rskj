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
package org.ethereum.core;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.db.MutableTrieImpl;
import co.rsk.trie.Trie;
import co.rsk.vm.BytecodeCompiler;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.db.MutableRepository;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.program.invoke.ProgramInvokeMockImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * VM-level tests for RSKIP-545 delegation indicator code-reading behavior:
 * {@code CODESIZE}/{@code CODECOPY} operate on executing code; {@code EXTCODESIZE}/
 * {@code EXTCODECOPY} on the authority return the 23-byte delegation indicator.
 */
class Rskip545DelegatedOpcodeTest {

    private static final RskAddress AUTHORITY =
            new RskAddress("0x00000000000000000000000000000000000000aa");
    private static final RskAddress DELEGATE =
            new RskAddress("0x00000000000000000000000000000000000000bb");

    private final TestSystemProperties config = new TestSystemProperties();
    private final ActivationConfig.ForBlock activations = ActivationConfigsForTest.all().forBlock(0);
    private final PrecompiledContractsHolder precompiled = new PrecompiledContractsHolder(config);
    private final BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());
    private final BytecodeCompiler compiler = new BytecodeCompiler();

    private ProgramInvokeMockImpl invoke;
    private MutableRepository repository;

    @BeforeEach
    void setupRepository() {
        repository = new MutableRepository(new MutableTrieImpl(null, new Trie()));
        repository.createAccount(AUTHORITY);
        repository.setupContract(AUTHORITY);
        repository.saveCode(AUTHORITY, DelegationCodeResolver.createDelegatedCode(DELEGATE));
        repository.createAccount(DELEGATE);
        repository.setupContract(DELEGATE);
        repository.saveCode(DELEGATE, Hex.decode("60006000f3"));

        invoke = new ProgramInvokeMockImpl(true);
        invoke.setRepository(repository);
        invoke.setOwnerAddress(AUTHORITY);
        invoke.setGas(1_000_000);
    }

    /**
     * Runs {@code implBytecode} as the authority's resolved execution code (same bytes
     * {@link org.ethereum.core.TransactionExecutor} loads via delegation resolution).
     */
    private org.ethereum.vm.program.Program runDelegatedExecution(byte[] implBytecode) {
        repository.saveCode(DELEGATE, implBytecode);
        byte[] resolved = resolveExecutionCode(AUTHORITY);
        assertArrayEquals(implBytecode, resolved, "fixture must mirror delegated execution code resolution");
        return precompiled.run(resolved, invoke, activations, blockFactory);
    }

    private byte[] resolveExecutionCode(RskAddress authority) {
        byte[] stored = repository.getCode(authority);
        if (!DelegationCodeResolver.isDelegatedCode(stored)) {
            return stored;
        }
        return repository.getCode(DelegationCodeResolver.extractDelegatedAddress(stored));
    }

    @Test
    void delegatedExecution_codesizeReturnsImplLength() {
        byte[] impl = compiler.compile("CODESIZE");

        var program = runDelegatedExecution(impl);
        assertEquals(1, program.getStack().size());
        assertEquals(impl.length, program.getStack().pop().intValue());
    }

    @Test
    void delegatedExecution_extcodesizeOnAuthorityReturns23() {
        byte[] impl = compiler.compile("ADDRESS EXTCODESIZE");

        var program = runDelegatedExecution(impl);
        assertEquals(1, program.getStack().size());
        assertEquals(23, program.getStack().pop().intValue());
    }

    @Test
    void delegatedExecution_codesizeAndExtcodesizeOnAuthorityDifferInSameFrame() {
        byte[] impl = compiler.compile("ADDRESS EXTCODESIZE CODESIZE");

        var program = runDelegatedExecution(impl);
        assertNull(program.getResult().getException());
        assertEquals(2, program.getStack().size());
        assertEquals(impl.length, program.getStack().pop().intValue(),
                "CODESIZE must reflect resolved delegate bytecode length");
        assertEquals(23, program.getStack().pop().intValue(),
                "EXTCODESIZE on authority must reflect the 23-byte delegation indicator");
    }

    @Test
    void delegatedExecution_extcodecopyCopiesIndicatorPrefix() {
        String code = " PUSH1 0x03 PUSH1 0x00 PUSH1 0x00"
                + " PUSH20 0X00000000000000000000000000000000000000AA EXTCODECOPY";
        byte[] impl = compiler.compile(code.trim());

        var program = runDelegatedExecution(impl);
        assertNull(program.getResult().getException());
        byte[] memory = program.getMemory();
        assertArrayEquals(DelegationCodeResolver.DELEGATION_PREFIX,
                Arrays.copyOfRange(memory, 0, 3),
                "EXTCODECOPY on authority must copy the delegation indicator bytes");
    }

    @Test
    void delegatedExecution_extcodecopyCopiesFull23ByteIndicator() {
        String code = " PUSH1 0x17 PUSH1 0x00 PUSH1 0x00"
                + " PUSH20 0X00000000000000000000000000000000000000AA EXTCODECOPY";
        byte[] impl = compiler.compile(code.trim());
        byte[] expectedIndicator = DelegationCodeResolver.createDelegatedCode(DELEGATE);

        var program = runDelegatedExecution(impl);
        assertNull(program.getResult().getException());
        assertArrayEquals(expectedIndicator, Arrays.copyOfRange(program.getMemory(), 0, 23),
                "EXTCODECOPY on authority must copy the full 0xef0100 || address indicator");
    }

    @Test
    void delegatedExecution_codecopyCopiesImplBytes() {
        byte[] impl = Hex.decode("60036000600039");

        var program = runDelegatedExecution(impl);
        assertNull(program.getResult().getException());
        byte[] memory = program.getMemory();
        assertArrayEquals(Arrays.copyOfRange(impl, 0, 3), Arrays.copyOfRange(memory, 0, 3),
                "CODECOPY must copy bytes from executing implementation code");
    }

    @Test
    void delegatedExecution_extcodesizeOnDelegateAddressReturnsImplLength() {
        byte[] delegateImpl = Hex.decode("60006000f3");
        String code = "PUSH20 0X00000000000000000000000000000000000000BB EXTCODESIZE";
        byte[] probe = compiler.compile(code.trim());

        repository.saveCode(DELEGATE, delegateImpl);
        var program = precompiled.run(probe, invoke, activations, blockFactory);
        assertNull(program.getResult().getException());
        assertEquals(1, program.getStack().size());
        assertEquals(delegateImpl.length, program.getStack().pop().intValue(),
                "EXTCODESIZE on the delegate contract must return its stored bytecode length, not 23");
    }

    @Test
    void delegatedExecution_addressReturnsAuthorityAccount() {
        byte[] impl = compiler.compile("ADDRESS");

        var program = runDelegatedExecution(impl);
        assertNull(program.getResult().getException());
        assertEquals(1, program.getStack().size());
        assertEquals(DataWord.valueOf(AUTHORITY.getBytes()), program.getStack().pop(),
                "ADDRESS must return the delegating authority, not the delegate contract");
    }

    @Test
    void delegatedExecution_storageWritePersistsOnAuthorityAccount() {
        byte[] impl = compiler.compile("PUSH1 0x42 PUSH1 0x00 SSTORE");

        var program = runDelegatedExecution(impl);
        assertNull(program.getResult().getException());
        assertEquals(DataWord.valueOf(0x42), repository.getStorageValue(AUTHORITY, DataWord.ZERO),
                "SSTORE from delegated execution must persist on the authority account");
        DataWord delegateSlot0 = repository.getStorageValue(DELEGATE, DataWord.ZERO);
        assertTrue(delegateSlot0 == null || delegateSlot0.isZero(),
                "Delegate account storage must remain unchanged");
    }

    @Test
    void delegatedExecution_extcodecopyOnDelegateAddressCopiesImplBytes() {
        byte[] delegateImpl = Hex.decode("60036000600039");
        String code = " PUSH1 0x03 PUSH1 0x00 PUSH1 0x20"
                + " PUSH20 0X00000000000000000000000000000000000000BB EXTCODECOPY";
        byte[] probe = compiler.compile(code.trim());

        repository.saveCode(DELEGATE, delegateImpl);
        var program = precompiled.run(probe, invoke, activations, blockFactory);
        assertNull(program.getResult().getException());
        assertArrayEquals(Arrays.copyOfRange(delegateImpl, 0, 3),
                Arrays.copyOfRange(program.getMemory(), 0x20, 0x23),
                "EXTCODECOPY on the delegate contract must copy its stored bytecode, not the indicator");
    }

    /** Small helper to avoid duplicating VM bootstrap in each test method. */
    private static final class PrecompiledContractsHolder {
        private final org.ethereum.vm.PrecompiledContracts contracts;
        private final co.rsk.config.VmConfig vmConfig;

        PrecompiledContractsHolder(TestSystemProperties config) {
            this.vmConfig = config.getVmConfig();
            this.contracts = new org.ethereum.vm.PrecompiledContracts(
                    config, null, new BlockTxSignatureCache(new ReceivedTxSignatureCache()));
        }

        org.ethereum.vm.program.Program run(
                byte[] code,
                ProgramInvokeMockImpl invoke,
                ActivationConfig.ForBlock activations,
                BlockFactory blockFactory
        ) {
            org.ethereum.vm.VM vm = new org.ethereum.vm.VM(vmConfig, contracts);
            var program = new org.ethereum.vm.program.Program(
                    vmConfig,
                    contracts,
                    blockFactory,
                    activations,
                    code,
                    invoke,
                    null,
                    new HashSet<>(),
                    new BlockTxSignatureCache(new ReceivedTxSignatureCache())
            );
            vm.play(program);
            return program;
        }
    }
}
