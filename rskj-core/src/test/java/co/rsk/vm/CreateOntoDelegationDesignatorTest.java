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

import co.rsk.config.TestSystemProperties;
import co.rsk.config.VmConfig;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.types.bytes.Bytes;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.peg.RepositoryBtcBlockStoreWithCache;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Account;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.DelegationCodeResolver;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.Repository;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.VM;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.invoke.ProgramInvokeMockImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CREATE and CREATE2 whose target address already holds a delegation designator (0xef0100 || address) and
 * nothing else: nonce 0, no balance, no storage. The designator is non-empty code, so the create must collide:
 * the opcode pushes 0, the gas given to the create is lost, and the designator stays as it was.
 * The designator is seeded directly, because no key is known for a CREATE or CREATE2 address.
 */
class CreateOntoDelegationDesignatorTest {

    private static final RskAddress CREATOR = new RskAddress("0x0000000000000000000000000000000000c0ffee");
    private static final RskAddress DELEGATE = new RskAddress("0x1111111111111111111111111111111111111111");
    private static final byte[] DESIGNATOR = DelegationCodeResolver.createDelegatedCode(DELEGATE);
    private static final byte[] SALT = new byte[32];
    private static final long ENDOWMENT = 10;
    private static final Coin CREATOR_BALANCE = Coin.valueOf(1_000);
    // runtime code that a successful create would store: a single STOP
    private static final byte[] RUNTIME = new byte[]{0x00};
    // PUSH1 1, PUSH1 12, PUSH1 0, CODECOPY, PUSH1 1, PUSH1 0, RETURN, then the runtime
    private static final byte[] INIT_CODE = ByteUtil.merge(Hex.decode("6001600c60003960016000f3"), RUNTIME);

    private final TestSystemProperties config = new TestSystemProperties();
    private final VmConfig vmConfig = config.getVmConfig();
    private final SignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
    private final PrecompiledContracts precompiledContracts = new PrecompiledContracts(
            config,
            new BridgeSupportFactory(
                    new RepositoryBtcBlockStoreWithCache.Factory(
                            config.getNetworkConstants().getBridgeConstants().getBtcParams()),
                    config.getNetworkConstants().getBridgeConstants(),
                    config.getActivationConfig(), signatureCache),
            signatureCache);
    private final BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());
    private final ActivationConfig.ForBlock activations = ActivationConfigsForTest.all().forBlock(0);

    private ProgramInvokeMockImpl invoke;
    private Repository repository;

    @BeforeEach
    void setup() {
        assertTrue(activations.isActive(ConsensusRule.RSKIP545));
        assertTrue(activations.isActive(ConsensusRule.RSKIP125));
        invoke = new ProgramInvokeMockImpl();
        invoke.setOwnerAddress(CREATOR);
        repository = invoke.getRepository();
        repository.addBalance(CREATOR, CREATOR_BALANCE);
    }

    static Stream<Arguments> createOpcodesAndAuthorityFlag() {
        return Stream.of(
                Arguments.of(true, true),
                Arguments.of(true, false),
                Arguments.of(false, true),
                Arguments.of(false, false));
    }

    @ParameterizedTest(name = "create2={0}, authorityFlag={1}")
    @MethodSource("createOpcodesAndAuthorityFlag")
    void createOntoAddressHoldingOnlyADesignator_collides_andKeepsTheDesignator(boolean create2, boolean authorityFlag) {
        RskAddress target = create2 ? create2Address() : createAddress();
        seedDesignator(target, authorityFlag);

        Program program = run(create2);

        assertEquals(1, program.getStack().size());
        assertEquals(DataWord.ZERO, program.getStack().peek(), "a collision must push 0");
        assertEquals(invoke.getGas(), program.getResult().getGasUsed(),
                "the gas handed to the colliding create is not refunded");

        assertArrayEquals(DESIGNATOR, repository.getCode(target), "the designator must stay");
        assertEquals(authorityFlag, repository.hasDelegationAuthority(target));
        assertEquals(BigInteger.ZERO, repository.getNonce(target));
        assertEquals(Coin.ZERO, repository.getBalance(target), "the endowment must not move");
        assertEquals(CREATOR_BALANCE, repository.getBalance(CREATOR));
        assertEquals(BigInteger.ONE, repository.getNonce(CREATOR), "the creator's nonce is still used up");
    }

    @Test
    void createOntoCleanAddress_deploysTheRuntime() {
        assertCleanCreateSucceeds(false, createAddress());
    }

    @Test
    void create2OntoCleanAddress_deploysTheRuntime() {
        assertCleanCreateSucceeds(true, create2Address());
    }

    @Test
    void designatorUsedAsSeed_isRecognisedAsDelegatedCode() {
        assertEquals(23, DESIGNATOR.length);
        assertTrue(DelegationCodeResolver.isDelegatedCode(DESIGNATOR));
        assertEquals(DELEGATE, DelegationCodeResolver.extractDelegatedAddress(DESIGNATOR));
        assertNotEquals(createAddress(), create2Address(), "CREATE and CREATE2 cases must hit different addresses");
    }

    private void assertCleanCreateSucceeds(boolean create2, RskAddress expected) {
        Program program = run(create2);

        assertEquals(1, program.getStack().size());
        assertEquals(DataWord.valueOf(expected.getBytes()), program.getStack().peek());
        assertTrue(program.getResult().getGasUsed() < invoke.getGas(), "unused create gas is refunded");
        assertArrayEquals(RUNTIME, repository.getCode(expected));
        assertFalse(repository.hasDelegationAuthority(expected));
        assertEquals(BigInteger.ONE, repository.getNonce(expected));
        assertEquals(Coin.valueOf(ENDOWMENT), repository.getBalance(expected));
        assertEquals(BigInteger.ONE, repository.getNonce(CREATOR));
    }

    private void seedDesignator(RskAddress target, boolean authorityFlag) {
        repository.createAccount(target);
        repository.saveCode(target, DESIGNATOR);
        // as SetCodeAuthorizationTransactionExecutor.writeDelegation does
        repository.initializeStorage(target);
        if (authorityFlag) {
            repository.setDelegationAuthority(target);
        }
        assertArrayEquals(DESIGNATOR, repository.getCode(target));
        assertEquals(BigInteger.ZERO, repository.getNonce(target));
    }

    private RskAddress createAddress() {
        return new RskAddress(HashUtil.calcNewAddr(CREATOR.getBytes(), BigInteger.ZERO.toByteArray()));
    }

    private RskAddress create2Address() {
        return new RskAddress(HashUtil.calcSaltAddr(CREATOR, Bytes.of(INIT_CODE), SALT));
    }

    /**
     * PUSH32 init code (left aligned), PUSH1 0, MSTORE, then for CREATE2 PUSH32 salt, then PUSH1 size,
     * PUSH1 0 (offset), PUSH1 endowment, CREATE or CREATE2, STOP.
     */
    private Program run(boolean create2) {
        byte[] word = new byte[32];
        System.arraycopy(INIT_CODE, 0, word, 0, INIT_CODE.length);
        byte[] code = ByteUtil.merge(
                new byte[]{0x7f}, word, Hex.decode("600052"),
                create2 ? ByteUtil.merge(new byte[]{0x7f}, SALT) : new byte[0],
                new byte[]{0x60, (byte) INIT_CODE.length, 0x60, 0x00, 0x60, (byte) ENDOWMENT},
                new byte[]{create2 ? (byte) 0xf5 : (byte) 0xf0, 0x00});

        VM vm = new VM(vmConfig, precompiledContracts);
        Program program = new Program(vmConfig, precompiledContracts, blockFactory, activations, code, invoke,
                anyTransaction(), new HashSet<>(), signatureCache);
        while (!program.isStopped()) {
            vm.step(program);
        }
        return program;
    }

    private static Transaction anyTransaction() {
        Account sender = new AccountBuilder().name("sender").build();
        Account receiver = new AccountBuilder().name("receiver").build();
        return new TransactionBuilder().sender(sender).receiver(receiver).value(BigInteger.ONE).build();
    }
}
