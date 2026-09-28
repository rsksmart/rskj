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
import co.rsk.config.VmConfig;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.types.bytes.Bytes;
import co.rsk.db.MutableTrieImpl;
import co.rsk.trie.Trie;
import co.rsk.vm.BytecodeCompiler;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.MutableRepository;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.VM;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.invoke.ProgramInvoke;
import org.ethereum.vm.program.invoke.ProgramInvokeMockImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RSKIP-545 interaction matrix: the delegation designator against RSKIP-544,
 * CREATE/CREATE2, SELFDESTRUCT, STATICCALL, precompile dispatch and RSKIP-546 fee validation.
 */
class Rskip545InteractionMatrixTest {

    private static final RskAddress AUTHORITY =
            new RskAddress("0x00000000000000000000000000000000000000aa");
    private static final RskAddress DELEGATE =
            new RskAddress("0x00000000000000000000000000000000000000bb");
    private static final RskAddress CALLER =
            new RskAddress("0x00000000000000000000000000000000000000c0");
    private static final RskAddress OBTAINER =
            new RskAddress("0x00000000000000000000000000000000000000d0");
    private static final byte[] EMPTY_RETURN_CODE = Hex.decode("60006000f3");

    private final TestSystemProperties config = new TestSystemProperties();
    private final ActivationConfig.ForBlock activations = ActivationConfigsForTest.all().forBlock(0);
    private final VmConfig vmConfig = config.getVmConfig();
    private final PrecompiledContracts precompiledContracts = new PrecompiledContracts(
            config, null, new BlockTxSignatureCache(new ReceivedTxSignatureCache()));
    private final BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());
    private final BytecodeCompiler compiler = new BytecodeCompiler();

    private MutableRepository repository;

    @BeforeEach
    void setupRepository() {
        repository = new MutableRepository(new MutableTrieImpl(null, new Trie()));
        saveAccount(AUTHORITY, DelegationCodeResolver.createDelegatedCode(DELEGATE));
        saveAccount(DELEGATE, EMPTY_RETURN_CODE);
        saveAccount(CALLER, ByteUtil.EMPTY_BYTE_ARRAY);
    }

    // =========================================================================
    // RSKIP-544 (reject new code starting with 0xEF) x the delegation designator
    // =========================================================================

    /** The ordering: the same 23 bytes are rejected at deployment and accepted through an authorization. */
    @Test
    void rskip544_rejectsDesignatorAtDeploymentButNotThroughAnAuthorization() {
        assertTrue(activations.isActive(ConsensusRule.RSKIP544), "fixture requires RSKIP-544 active");
        byte[] designator = DelegationCodeResolver.createDelegatedCode(DELEGATE);
        RskAddress created = createAddress(CALLER);

        Program program = run(createFrom(returnLiteral(designator), null), invokeFor(CALLER));

        assertEquals(DataWord.ZERO, program.getStack().peek(),
                "CREATE returning designator-shaped runtime code must fail under RSKIP-544");
        assertTrue(isEmptyCode(repository.getCode(created)));

        ECKey authorityKey = new ECKey();
        RskAddress authority = new RskAddress(authorityKey.getAddress());
        repository.createAccount(authority);
        new SetCodeAuthorizationTransactionExecutor().processAuthorizationTuple(
                repository,
                BigInteger.valueOf(Constants.REGTEST_CHAIN_ID),
                Rskip545TestSupport.createSignedAuthorization(
                        authorityKey, DELEGATE, BigInteger.ZERO, Constants.REGTEST_CHAIN_ID));

        assertArrayEquals(designator, repository.getCode(authority),
                "the authorization path installs exactly the bytes the deployment rule rejects");
    }

    @Test
    void rskip544_createRejectsDesignatorShapedRuntimeCode() {
        byte[] designator = DelegationCodeResolver.createDelegatedCode(DELEGATE);
        RskAddress created = createAddress(CALLER);

        Program program = run(createFrom(returnLiteral(designator), null), invokeFor(CALLER));

        assertEquals(DataWord.ZERO, program.getStack().peek(),
                "CREATE returning designator-shaped runtime code must fail under RSKIP-544");
        assertTrue(isEmptyCode(repository.getCode(created)),
                "RSKIP-544 must leave no designator-shaped code behind at the CREATE address");
    }

    @Test
    void rskip544_createAcceptsNonEfRuntimeCode() {
        RskAddress created = createAddress(CALLER);

        Program program = run(createFrom(returnLiteral(EMPTY_RETURN_CODE), null), invokeFor(CALLER));

        assertEquals(DataWord.valueOf(created.getBytes()), program.getStack().peek(),
                "control: the same CREATE shape succeeds when the runtime code does not start with 0xEF");
        assertArrayEquals(EMPTY_RETURN_CODE, repository.getCode(created));
    }

    // =========================================================================
    // CREATE / CREATE2 at an address that already carries a delegation
    // =========================================================================

    @Test
    void create2_atAddressCarryingDesignator_reportsFailure() {
        byte[] initCode = returnLiteral(EMPTY_RETURN_CODE);
        byte[] salt = new byte[32];
        RskAddress target = create2Address(CALLER, initCode, salt);
        saveAccount(target, DelegationCodeResolver.createDelegatedCode(DELEGATE));

        Program program = run(createFrom(initCode, salt), invokeFor(CALLER));

        assertEquals(DataWord.ZERO, program.getStack().peek(),
                "a delegation designator is non-empty code, so CREATE2 onto it is an address collision");
    }

    @Test
    void create2_atAddressCarryingDesignator_leavesDesignatorIntact() {
        byte[] initCode = returnLiteral(EMPTY_RETURN_CODE);
        byte[] salt = new byte[32];
        RskAddress target = create2Address(CALLER, initCode, salt);
        byte[] designator = DelegationCodeResolver.createDelegatedCode(DELEGATE);
        saveAccount(target, designator);

        run(createFrom(initCode, salt), invokeFor(CALLER));

        assertArrayEquals(designator, repository.getCode(target),
                "a failed CREATE2 must not overwrite an authority's delegation designator");
    }

    @Test
    void create2_atAddressWithoutDesignator_succeeds() {
        byte[] initCode = returnLiteral(EMPTY_RETURN_CODE);
        byte[] salt = new byte[32];
        RskAddress target = create2Address(CALLER, initCode, salt);

        Program program = run(createFrom(initCode, salt), invokeFor(CALLER));

        assertEquals(DataWord.valueOf(target.getBytes()), program.getStack().peek(),
                "control: the same CREATE2 succeeds when the target carries no designator");
    }

    // =========================================================================
    // SELFDESTRUCT
    // =========================================================================

    @Test
    void selfdestruct_inDelegatedExecution_deletesAuthorityNotDelegate() {
        repository.addBalance(AUTHORITY, Coin.valueOf(1_000));

        Program program = runDelegated(compiler.compile(push20(OBTAINER) + " SUICIDE"));

        assertNull(program.getResult().getException());
        Set<DataWord> deleted = program.getResult().getDeleteAccounts();
        assertTrue(containsAddress(deleted, AUTHORITY),
                "SELFDESTRUCT under delegated execution must target the authority account");
        assertFalse(containsAddress(deleted, DELEGATE),
                "the delegate target must not be deleted by the authority's SELFDESTRUCT");
    }

    @Test
    void selfdestruct_inDelegatedExecution_movesAuthorityBalanceOnly() {
        repository.addBalance(AUTHORITY, Coin.valueOf(1_000));
        repository.addBalance(DELEGATE, Coin.valueOf(7_000));

        runDelegated(compiler.compile(push20(OBTAINER) + " SUICIDE"));

        assertEquals(Coin.valueOf(1_000), repository.getBalance(OBTAINER),
                "SELFDESTRUCT transfers the authority's balance, the account the code runs in");
        assertEquals(Coin.valueOf(7_000), repository.getBalance(DELEGATE),
                "the delegate target keeps its own balance");
    }

    @Test
    void selfdestruct_ofDelegateTarget_codeStaysResolvableUntilFinalization() {
        byte[] victimCode = compiler.compile(push20(OBTAINER) + " SUICIDE");
        repository.saveCode(DELEGATE, victimCode);

        Program program = run(callTo(AUTHORITY), invokeFor(CALLER));

        assertEquals(DataWord.ONE, program.getStack().peek(),
                "calling the authority runs the delegate code that selfdestructs");
        assertArrayEquals(victimCode, resolveExecutionCode(AUTHORITY),
                "SELFDESTRUCT only marks the account; the designator still resolves for the rest of the transaction");
    }

    @Test
    void selfdestruct_ofDelegateTarget_afterDeletion_designatorResolvesToEmptyCode() {
        byte[] designator = repository.getCode(AUTHORITY);

        repository.delete(DELEGATE);

        assertArrayEquals(designator, repository.getCode(AUTHORITY),
                "deleting the delegate target must not touch the authority's designator");
        assertArrayEquals(ByteUtil.EMPTY_BYTE_ARRAY, resolveExecutionCode(AUTHORITY),
                "a designator pointing at a destroyed target resolves to empty code");
    }

    // =========================================================================
    // Static context
    // =========================================================================

    @Test
    void staticCall_toDelegatedAccount_resolvesAndRunsDelegateCode() {
        repository.saveCode(DELEGATE,
                compiler.compile("PUSH1 0x2a PUSH1 0x00 MSTORE PUSH1 0x20 PUSH1 0x00 RETURN"));

        Program program = run(staticCallTo(AUTHORITY), invokeFor(CALLER));

        assertEquals(DataWord.ONE, program.getStack().peek(),
                "STATICCALL must resolve the delegation designator exactly as CALL does");
        assertEquals(DataWord.valueOf(0x2a),
                DataWord.valueOf(Arrays.copyOfRange(program.getMemory(), 0, 32)),
                "the delegate's return value must reach the static caller");
    }

    @Test
    void staticCall_toDelegatedAccount_stateWriteInDelegateCodeHalts() {
        repository.saveCode(DELEGATE, compiler.compile("PUSH1 0x01 PUSH1 0x00 SSTORE"));

        Program program = run(staticCallTo(AUTHORITY), invokeFor(CALLER));

        assertEquals(DataWord.ZERO, program.getStack().peek(),
                "delegated code that writes storage must fail inside STATICCALL");
        DataWord stored = repository.getStorageValue(AUTHORITY, DataWord.ZERO);
        assertTrue(stored == null || stored.isZero(),
                "no authority storage may be written through a static frame");
    }

    @Test
    void staticCall_toDelegatedAccount_readOnlyDelegateCodeSucceeds() {
        repository.saveCode(DELEGATE, compiler.compile("PUSH1 0x00 SLOAD POP"));

        Program program = run(staticCallTo(AUTHORITY), invokeFor(CALLER));

        assertEquals(DataWord.ONE, program.getStack().peek(),
                "control: read-only delegated code is allowed in a static frame");
    }

    // =========================================================================
    // Precompiles: the internal CALL path. The top-level path is already covered by
    // Type4TransactionExecutorDelegationExecutionTests.
    // =========================================================================

    @Test
    void internalCall_toAccountDelegatedToPrecompile_resolvesToEmptyCodeEvenIfCodeIsStoredThere() {
        // Code stored at the precompile address makes the short-circuit observable: without it,
        // resolution would hand this bytecode to the VM instead of returning empty.
        saveAccount(PrecompiledContracts.IDENTITY_ADDR,
                compiler.compile("PUSH1 0x2a PUSH1 0x00 MSTORE PUSH1 0x20 PUSH1 0x00 RETURN"));
        repository.saveCode(AUTHORITY,
                DelegationCodeResolver.createDelegatedCode(PrecompiledContracts.IDENTITY_ADDR));

        Program program = run(callWithReturnData(AUTHORITY), invokeFor(CALLER));

        assertEquals(DataWord.ONE, program.getStack().peek(), "the call succeeds as an empty-code call");
        assertEquals(0, program.getReturnDataBufferSize().intValue(),
                "resolution short-circuits on a precompile target before any code is executed");
        assertArrayEquals(ByteUtil.EMPTY_BYTE_ARRAY, resolveExecutionCode(AUTHORITY));
    }

    @Test
    void internalCall_toPrecompileAddressItself_stillDispatchesThePrecompile() {
        Program program = run(callWithReturnData(PrecompiledContracts.IDENTITY_ADDR), invokeFor(CALLER));

        assertEquals(DataWord.ONE, program.getStack().peek());
        assertTrue(program.getReturnDataBufferSize().intValue() > 0,
                "control: precompile dispatch is unchanged when the destination is the precompile itself");
    }

    // =========================================================================
    // RSKIP-546 fee validation against Type 4 envelopes
    // =========================================================================

    @Test
    void rskip546_type4Envelope_rejectsPriorityFeeAboveMaxFee() {
        assertThrows(Exception.class, () -> type4Envelope(Coin.valueOf(101), Coin.valueOf(100)),
                "RSKIP-546 must reject a Type 4 envelope whose priority fee exceeds the max fee");
    }

    @Test
    void rskip546_type4Envelope_acceptsPriorityFeeEqualToMaxFee() {
        Transaction tx = type4Envelope(Coin.valueOf(100), Coin.valueOf(100));

        assertNotNull(tx);
        assertEquals(Coin.valueOf(100), tx.getMaxPriorityFeePerGas());
        assertEquals(Coin.valueOf(100), tx.getMaxFeePerGas());
    }

    @Test
    void rskip546_type4Envelope_acceptsPriorityFeeBelowMaxFee() {
        Transaction tx = type4Envelope(Coin.valueOf(10), Coin.valueOf(100));

        assertEquals(Coin.valueOf(10), tx.getMaxPriorityFeePerGas());
        assertEquals(Coin.valueOf(100), tx.getMaxFeePerGas());
    }

    // =========================================================================
    // Harness
    // =========================================================================

    private static Transaction type4Envelope(Coin maxPriorityFeePerGas, Coin maxFeePerGas) {
        return Rskip545TestSupport.unsignedType4(
                Rskip545TestSupport.DEFAULT_RECEIVER,
                maxPriorityFeePerGas,
                maxFeePerGas,
                new byte[0],
                Rskip545TestSupport.EMPTY_ACCESS_LIST);
    }

    private void saveAccount(RskAddress address, byte[] code) {
        repository.createAccount(address);
        repository.initializeStorage(address);
        repository.saveCode(address, code);
        if (DelegationCodeResolver.isDelegatedCode(code)) {
            repository.setDelegationAuthority(address);
        }
    }

    private ProgramInvokeMockImpl invokeFor(RskAddress owner) {
        ProgramInvokeMockImpl invoke = new ProgramInvokeMockImpl(true);
        invoke.setRepository(repository);
        invoke.setOwnerAddress(owner);
        invoke.setGas(5_000_000);
        return invoke;
    }

    private Program run(byte[] code, ProgramInvoke invoke) {
        VM vm = new VM(vmConfig, precompiledContracts);
        Program program = new Program(
                vmConfig,
                precompiledContracts,
                blockFactory,
                activations,
                code,
                invoke,
                enclosingTransaction(),
                new HashSet<>(),
                new BlockTxSignatureCache(new ReceivedTxSignatureCache()));
        vm.play(program);
        return program;
    }

    /** A non-null enclosing transaction: internal-transaction bookkeeping needs one on failure paths. */
    private Transaction enclosingTransaction() {
        return Transaction.builder()
                .nonce(BigInteger.ONE.toByteArray())
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(5_000_000))
                .receiveAddress(AUTHORITY)
                .chainId(config.getNetworkConstants().getChainId())
                .value(BigInteger.ZERO)
                .build();
    }

    /** Runs {@code implBytecode} the way delegated execution does: delegate code, authority context. */
    private Program runDelegated(byte[] implBytecode) {
        repository.saveCode(DELEGATE, implBytecode);
        byte[] resolved = resolveExecutionCode(AUTHORITY);
        assertArrayEquals(implBytecode, resolved, "fixture must mirror delegated execution code resolution");
        return run(resolved, invokeFor(AUTHORITY));
    }

    private byte[] resolveExecutionCode(RskAddress address) {
        return DelegationCodeResolver.getExecutionCode(repository, address, this::isPrecompile, activations);
    }

    private boolean isPrecompile(RskAddress address) {
        return precompiledContracts.getContractForAddress(
                activations, DataWord.valueOf(address.getBytes())) != null;
    }

    private static boolean isEmptyCode(byte[] code) {
        return code == null || code.length == 0;
    }

    private static boolean containsAddress(Set<DataWord> accounts, RskAddress address) {
        return accounts.contains(DataWord.valueOf(address.getBytes()));
    }

    private RskAddress createAddress(RskAddress sender) {
        return new RskAddress(HashUtil.calcNewAddr(
                sender.getBytes(), repository.getNonce(sender).toByteArray()));
    }

    private RskAddress create2Address(RskAddress sender, byte[] initCode, byte[] salt) {
        return new RskAddress(HashUtil.calcSaltAddr(sender, Bytes.of(initCode), salt));
    }

    /** Initcode (at most 32 bytes of payload) that returns {@code literal} as the deployed code. */
    private byte[] returnLiteral(byte[] literal) {
        String code = " PUSH" + literal.length + " 0x" + Hex.toHexString(literal)
                + " PUSH1 0x00 MSTORE"
                + " PUSH1 0x" + hex(literal.length)
                + " PUSH1 0x" + hex(32 - literal.length)
                + " RETURN";
        return compiler.compile(code.trim());
    }

    /** Stores {@code initCode} in memory, then runs CREATE, or CREATE2 when {@code salt} is given. */
    private byte[] createFrom(byte[] initCode, byte[] salt) {
        byte[] word = new byte[32];
        System.arraycopy(initCode, 0, word, 0, initCode.length);
        StringBuilder code = new StringBuilder(" PUSH32 0x" + Hex.toHexString(word) + " PUSH1 0x00 MSTORE");
        if (salt != null) {
            code.append(" PUSH32 0x").append(Hex.toHexString(salt));
        }
        code.append(" PUSH1 0x").append(hex(initCode.length)).append(" PUSH1 0x00 PUSH1 0x00");
        code.append(salt != null ? " CREATE2" : " CREATE");
        return compiler.compile(code.toString().trim());
    }

    private byte[] callTo(RskAddress destination) {
        return compiler.compile((" PUSH1 0x00 PUSH1 0x00 PUSH1 0x00 PUSH1 0x00 PUSH1 0x00 "
                + push20(destination) + " GAS CALL").trim());
    }

    /** CALL with one calldata byte and a 32-byte return window, so return data size is observable. */
    private byte[] callWithReturnData(RskAddress destination) {
        return compiler.compile((" PUSH1 0x2a PUSH1 0x00 MSTORE8"
                + " PUSH1 0x20 PUSH1 0x40 PUSH1 0x01 PUSH1 0x00 PUSH1 0x00 "
                + push20(destination) + " GAS CALL").trim());
    }

    private byte[] staticCallTo(RskAddress destination) {
        return compiler.compile((" PUSH1 0x20 PUSH1 0x00 PUSH1 0x00 PUSH1 0x00 "
                + push20(destination) + " GAS STATICCALL").trim());
    }

    private static String push20(RskAddress address) {
        return "PUSH20 0x" + Hex.toHexString(address.getBytes());
    }

    private static String hex(int value) {
        return String.format("%02x", value);
    }
}
