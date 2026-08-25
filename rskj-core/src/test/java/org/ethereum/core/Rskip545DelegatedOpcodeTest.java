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
import co.rsk.core.types.bytes.Bytes;
import co.rsk.db.MutableTrieImpl;
import co.rsk.trie.Trie;
import co.rsk.vm.BytecodeCompiler;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.db.BlockStoreDummy;
import org.ethereum.db.MutableRepository;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.program.invoke.ProgramInvoke;
import org.ethereum.vm.program.invoke.ProgramInvokeImpl;
import org.ethereum.vm.program.invoke.ProgramInvokeMockImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;

import static com.google.common.primitives.Bytes.concat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
    private static final RskAddress OTHER_DELEGATED =
            new RskAddress("0x00000000000000000000000000000000000000cc");
    private static final RskAddress OTHER_DELEGATE_TARGET =
            new RskAddress("0x00000000000000000000000000000000000000dd");
    private static final RskAddress NESTED_CALL_PROBE =
            new RskAddress("0x00000000000000000000000000000000000000ee");

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
     * Top-level invoke for self-sponsored Type 4 execution: {@code ADDRESS}, {@code ORIGIN},
     * and {@code CALLER} all equal the delegating authority (mirrors {@link org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl}).
     */
    private ProgramInvoke selfSponsoredTopLevelInvoke() {
        byte[] authority = AUTHORITY.getBytes();
        return new ProgramInvokeImpl(
                authority,
                authority,
                authority,
                repository.getBalance(AUTHORITY).getBytes(),
                ByteUtil.EMPTY_BYTE_ARRAY,
                new byte[]{(byte) 0x0f, (byte) 0x42, 0x40},
                ByteUtil.EMPTY_BYTE_ARRAY,
                Bytes.of(ByteUtil.EMPTY_BYTE_ARRAY),
                ByteUtil.EMPTY_BYTE_ARRAY,
                ByteUtil.EMPTY_BYTE_ARRAY,
                0L,
                0L,
                0,
                ByteUtil.EMPTY_BYTE_ARRAY,
                new byte[]{(byte) 0x0f, (byte) 0x42, 0x40},
                ByteUtil.EMPTY_BYTE_ARRAY,
                repository,
                new BlockStoreDummy()
        );
    }

    /**
     * Runs {@code implBytecode} as the authority's resolved execution code (same bytes
     * {@link org.ethereum.core.TransactionExecutor} loads via delegation resolution).
     */
    private org.ethereum.vm.program.Program runDelegatedExecution(byte[] implBytecode, ProgramInvoke programInvoke) {
        repository.saveCode(DELEGATE, implBytecode);
        byte[] resolved = resolveExecutionCode();
        assertArrayEquals(implBytecode, resolved, "fixture must mirror delegated execution code resolution");
        return precompiled.run(resolved, programInvoke, activations, blockFactory);
    }

    private org.ethereum.vm.program.Program runDelegatedExecution(byte[] implBytecode) {
        return runDelegatedExecution(implBytecode, invoke);
    }

    private byte[] resolveExecutionCode() {
        byte[] stored = repository.getCode(Rskip545DelegatedOpcodeTest.AUTHORITY);
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

    @Test
    void delegatedExecution_codesizeReturnsImplLength_forLargerBytecode() {
        byte[] impl = Hex.decode("6001600101" // PUSH1 1, PUSH1 1, ADD
                + "6002600202" // PUSH1 2, PUSH1 2, MUL
                + "6003600301" // PUSH1 3, PUSH1 3, ADD
                + "50505000"); // POP, POP,POP, STOP
        byte[] probe = concat(compiler.compile("CODESIZE"), impl);

        var program = runDelegatedExecution(probe);
        assertNull(program.getResult().getException());
        assertEquals(1, program.getStack().size());
        assertEquals(probe.length, program.getStack().pop().intValue(),
                "CODESIZE must return the null resolved delegate code length, not 23");
        assertNotEquals(23, probe.length,
                "Test fixture must use code longer than delegation indicator");
    }

    @Test
    void delegatedExecution_codecopyCopiesFullResolvedCode() {
        byte[] impl = Hex.decode("6001600101" // PUSH1 1, PUSH1 1, ADD
                + "6002600202" // PUSH1 2, PUSH1 2, MUL
                + "6003600301" // PUSH1 3, PUSH1 3, ADD
                + "6004600401" // PUSH1 4, PUSH1 4, ADD
                + "6005600501" // PUSH1 5, PUSH1 5, ADD
                + "6003600301" // PUSH1 3, PUSH1 3, ADD
                + "50505050505000"); // POP, POP, POP, POP, POP, STOP
        assertTrue(impl.length > 23, "Test fixture must be longer than delegation indicator");

        int totalLen = impl.length + 7;
        byte[] codecopySuffix = new byte[]{
                0x60, (byte) totalLen, // PUSH1 <totalLen>
                0x60, 0x00,            // PUSH1 0
                0x60, 0x00,            // PUSH1 0
                0x39                   // CODECOPY
        };
        byte[] probe = concat(codecopySuffix, impl);

        var program = runDelegatedExecution(probe);
        assertNull(program.getResult().getException());
        byte[] memory = Arrays.copyOfRange(program.getMemory(), 0, probe.length);
        assertArrayEquals(probe, memory,
                "CODECOPY must copy the full resolved delegate bytecode, not the 23-byte indicator");
    }

    @Test
    void delegatedExecution_codecopyZeroPadsBasedOnResolvedCodeLength() {
        byte[] impl = Hex.decode("6001600101" // PUSH1 1, PUSH1 1, ADD
                + "6002600202" // PUSH1 2, PUSH1 2, MUL
                + "6003600301" // PUSH1 3, PUSH1 3, ADD
                + "6004600401" // PUSH1 4, PUSH1 4, ADD
                + "6005600501" // PUSH1 5, PUSH1 5, ADD
                + "6003600301" // PUSH1 3, PUSH1 3, ADD
                + "50505050505000"); // POP, POP, POP, POP, POP, STOP
        int codeLen = impl.length + 7;
        int requestLen = codeLen + 10;
        byte[] probe = concat(new byte[] {
                0x60, (byte) requestLen, // PUSH1 <requestLen>
                0x60, 0x00,              // PUSH1 0
                0x60, 0x00,              // PUSH1 0
                0x39                     // CODECOPY
        }, impl);

        var program = runDelegatedExecution(probe);
        assertNull(program.getResult().getException());
        byte[] memory = program.getMemory();
        byte[] actualCode = Arrays.copyOfRange(memory, 0, probe.length);
        assertArrayEquals(probe, actualCode, "First N bytes must be the resolved code");
        byte[] padding = Arrays.copyOfRange(memory, probe.length, probe.length + 10);
        assertArrayEquals(new byte[10], padding, "CODECOPY must zero-pad resolved code length, not past 23");
    }

    @Test
    void delegatedExecution_extcodesizeOnThirdPartyDelegatedAccountReturns23() {
        byte[] impl = Hex.decode("6001600101505000");
        repository.createAccount(OTHER_DELEGATED);
        repository.setupContract(OTHER_DELEGATED);
        repository.saveCode(OTHER_DELEGATED, DelegationCodeResolver.createDelegatedCode(OTHER_DELEGATE_TARGET));
        repository.createAccount(OTHER_DELEGATE_TARGET);
        repository.setupContract(OTHER_DELEGATE_TARGET);
        repository.saveCode(OTHER_DELEGATE_TARGET, impl);

        String code = "PUSH20 0X00000000000000000000000000000000000000CC EXTCODESIZE";
        byte[] probe = compiler.compile(code.trim());

        var program = runDelegatedExecution(probe);
        assertNull(program.getResult().getException());
        assertEquals(1, program.getStack().size());
        assertEquals(23, program.getStack().pop().intValue(),
                "EXTCODESIZE on a third-party delegated account must return 23 (the delegation indicator), not the resolved code length");
    }

    @Test
    void delegatedExecution_extcodecopyOnThirdPartyDelegatedAccountCopiesIndicator() {
        byte[] impl = Hex.decode("6001600101505000");
        repository.createAccount(OTHER_DELEGATED);
        repository.setupContract(OTHER_DELEGATED);
        repository.saveCode(OTHER_DELEGATED, DelegationCodeResolver.createDelegatedCode(OTHER_DELEGATE_TARGET));
        repository.createAccount(OTHER_DELEGATE_TARGET);
        repository.setupContract(OTHER_DELEGATE_TARGET);
        repository.saveCode(OTHER_DELEGATE_TARGET, impl);

        String code = "PUSH1 0x17 PUSH1 0x00 PUSH1 0x00 "
                + "PUSH20 0X00000000000000000000000000000000000000CC EXTCODECOPY";
        byte[] probe = compiler.compile(code.trim());
        byte[] expectedIndicator = DelegationCodeResolver.createDelegatedCode(OTHER_DELEGATE_TARGET);

        var program = runDelegatedExecution(probe);
        assertNull(program.getResult().getException());
        assertArrayEquals(expectedIndicator, Arrays.copyOfRange(program.getMemory(), 0, 23),
             "EXTCODECOPY on a third-party delegated account must return the delegation indicator, not the resolved code");
    }

    @Test
    void delegatedExecution_extcodehashOnAuthorityReturnsHashOfDelegationIndicator() {
        String code = "ADDRESS EXTCODEHASH";
        byte[] probe = compiler.compile(code.trim());

        var program = runDelegatedExecution(probe);
        assertNull(program.getResult().getException());
        assertEquals(1, program.getStack().size());

        byte[] delegationCode = DelegationCodeResolver.createDelegatedCode(DELEGATE);
        byte[] expectedHash = Keccak256Helper.keccak256(delegationCode);
        assertArrayEquals(expectedHash, program.getStack().pop().getData(),
                "EXTCODEHASH on authority must hash the 23-byte delegation indicator, not the resolved code");
    }

    @Test
    void delegatedExecution_extcodehashOnThirdPartyDelegatedAccountReturnsHashOfIndicator() {
        byte[] impl = Hex.decode("6001600101505000");
        repository.createAccount(OTHER_DELEGATED);
        repository.setupContract(OTHER_DELEGATED);
        repository.saveCode(OTHER_DELEGATED, DelegationCodeResolver.createDelegatedCode(OTHER_DELEGATE_TARGET));
        repository.createAccount(OTHER_DELEGATE_TARGET);
        repository.setupContract(OTHER_DELEGATE_TARGET);
        repository.saveCode(OTHER_DELEGATE_TARGET, impl);

        String code = "PUSH20 0X00000000000000000000000000000000000000CC EXTCODEHASH";
        byte[] probe = compiler.compile(code.trim());

        var program = runDelegatedExecution(probe);
        assertNull(program.getResult().getException());
        assertEquals(1, program.getStack().size());

        byte[] indicatorCode = DelegationCodeResolver.createDelegatedCode(OTHER_DELEGATE_TARGET);
        byte[] expectedHash = Keccak256Helper.keccak256(indicatorCode);
        assertArrayEquals(expectedHash, program.getStack().pop().getData(),
                "EXTCODEHASH on a third-party delegated account must hash delegation indicator, not the resolved code");
    }

    @Test
    void delegatedExecution_originEqualsCaller_inSelfSponsoredTopFrame() {
        byte[] impl = compiler.compile("ORIGIN CALLER EQ");

        var program = runDelegatedExecution(impl, selfSponsoredTopLevelInvoke());
        assertNull(program.getResult().getException());
        assertEquals(1, program.getStack().size());
        assertEquals(1, program.getStack().pop().intValue(),
                "Self-sponsored delegated execution must see msg.sender == tx.origin in the top frame");
    }

    @Test
    void delegatedExecution_nestedCall_originEqualsCaller_inChildFrame() {
        // Probe stores tx.origin in slot 0 and msg.sender in slot 1.
        byte[] probeCode = compiler.compile("ORIGIN PUSH1 0x00 SSTORE CALLER PUSH1 0x01 SSTORE");
        repository.createAccount(NESTED_CALL_PROBE);
        repository.setupContract(NESTED_CALL_PROBE);
        repository.saveCode(NESTED_CALL_PROBE, probeCode);

        String callProbe = " PUSH1 0x00 PUSH1 0x00 PUSH1 0x00 PUSH1 0x00 PUSH1 0x00"
                + " PUSH20 0X00000000000000000000000000000000000000EE GAS CALL";
        byte[] impl = compiler.compile(callProbe.trim());

        var program = runDelegatedExecution(impl, selfSponsoredTopLevelInvoke());
        assertNull(program.getResult().getException());

        DataWord storedOrigin = repository.getStorageValue(NESTED_CALL_PROBE, DataWord.ZERO);
        DataWord storedCaller = repository.getStorageValue(NESTED_CALL_PROBE, DataWord.ONE);
        assertEquals(DataWord.valueOf(AUTHORITY.getBytes()), storedOrigin,
                "Nested CALL from delegated EOA must preserve tx.origin");
        assertEquals(DataWord.valueOf(AUTHORITY.getBytes()), storedCaller,
                "Nested CALL from delegated EOA must set msg.sender to the authority (breaking the pre-7702 frame invariant)");
        assertEquals(storedOrigin, storedCaller,
                "msg.sender == tx.origin must hold in nested frames when delegated EOA issues CALL");
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
                ProgramInvoke invoke,
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
