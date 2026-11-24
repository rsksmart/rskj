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
import co.rsk.core.RskAddress;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Code that only looks like a delegation designator must not be followed as a delegation, and a real designator
 * must not be followed while RSKIP-545 is off. A probe contract reads EXTCODESIZE and EXTCODEHASH of the seeded
 * account, then DELEGATECALLs and CALLCODEs it. The delegate is a counter (slot 0 += 1), so the probe's slot 0
 * counts how many times the delegate code ran in the probe's storage.
 */
class NearDelegationDesignatorCodeTest {

    private static final RskAddress PROBE = new RskAddress("0x0000000000000000000000000000000000000b0b");
    private static final RskAddress TARGET = new RskAddress("0x0000000000000000000000000000000000ab0de0");
    private static final RskAddress DELEGATE = new RskAddress("0x1111111111111111111111111111111111111111");
    // slot0 += 1
    private static final byte[] COUNTER_RUNTIME = Hex.decode("60005460010160005500");

    // Computed with ethers 6.17.0: concat(["0xef0100", DELEGATE]) and keccak256 of it
    private static final byte[] GOLDEN_DESIGNATOR = Hex.decode("ef01001111111111111111111111111111111111111111");
    private static final byte[] GOLDEN_DESIGNATOR_HASH =
            Hex.decode("dfe7c7677e3d245aaff2e3e94db902f5fb37475eb611959c71ee641508dcf49a");

    private static final DataWord SIZE_SLOT = DataWord.valueOf(0x10);
    private static final DataWord HASH_SLOT = DataWord.valueOf(0x11);
    private static final DataWord DELEGATECALL_OK_SLOT = DataWord.valueOf(0x12);
    private static final DataWord CALLCODE_OK_SLOT = DataWord.valueOf(0x13);

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

    static Stream<Arguments> nearDesignatorForms() {
        byte[] d = DELEGATE.getBytes();
        return Stream.of(
                Arguments.of("0xef0100 + 19 bytes", ByteUtil.merge(Hex.decode("ef0100"), Arrays.copyOf(d, 19))),
                Arguments.of("0xef0100 + 21 bytes", ByteUtil.merge(Hex.decode("ef0100"), d, new byte[]{0x11})),
                Arguments.of("0xef0101 + 20 bytes", ByteUtil.merge(Hex.decode("ef0101"), d)),
                Arguments.of("0xef0200 + 20 bytes", ByteUtil.merge(Hex.decode("ef0200"), d)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nearDesignatorForms")
    void nearDesignatorForm_isNotRecognisedAsDelegatedCode(String name, byte[] code) {
        assertFalse(DelegationCodeResolver.isDelegatedCode(code));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nearDesignatorForms")
    void nearDesignatorForm_withRskip545On_runsAsRawCodeAndNoDelegationIsFollowed(String name, byte[] code) {
        Repository repository = runProbe(code, ActivationConfigsForTest.all().forBlock(0));

        assertRawCodeSeen(repository, code);
    }

    @Test
    void designator_withRskip545Off_isSeenAsRaw23Bytes_andNoDelegationIsFollowed() {
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.allBut(ConsensusRule.RSKIP545).forBlock(0);
        assertFalse(activations.isActive(ConsensusRule.RSKIP545));

        Repository repository = runProbe(GOLDEN_DESIGNATOR, activations);

        assertRawCodeSeen(repository, GOLDEN_DESIGNATOR);
        assertArrayEquals(GOLDEN_DESIGNATOR_HASH, slot(repository, HASH_SLOT).getData());
    }

    @Test
    void designator_withRskip545On_isFollowedByDelegatecallAndCallcode() {
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.all().forBlock(0);
        assertTrue(activations.isActive(ConsensusRule.RSKIP545));

        Repository repository = runProbe(GOLDEN_DESIGNATOR, activations);

        assertEquals(DataWord.valueOf(23), slot(repository, SIZE_SLOT), "EXTCODESIZE sees the designator itself");
        assertArrayEquals(GOLDEN_DESIGNATOR_HASH, slot(repository, HASH_SLOT).getData(),
                "EXTCODEHASH is the hash of the designator itself");
        assertEquals(DataWord.ONE, slot(repository, DELEGATECALL_OK_SLOT));
        assertEquals(DataWord.ONE, slot(repository, CALLCODE_OK_SLOT));
        assertEquals(DataWord.valueOf(2), slot(repository, DataWord.ZERO),
                "the delegate code ran twice in the probe's storage");
    }

    @Test
    void ourDesignatorAndItsHash_matchTheIndependentGoldenVector() {
        byte[] ours = DelegationCodeResolver.createDelegatedCode(DELEGATE);

        assertArrayEquals(GOLDEN_DESIGNATOR, ours);
        assertArrayEquals(GOLDEN_DESIGNATOR_HASH, HashUtil.keccak256(ours));
        assertTrue(DelegationCodeResolver.isDelegatedCode(GOLDEN_DESIGNATOR));
        assertEquals(DELEGATE, DelegationCodeResolver.extractDelegatedAddress(GOLDEN_DESIGNATOR));
    }

    private static void assertRawCodeSeen(Repository repository, byte[] code) {
        assertEquals(DataWord.valueOf(code.length), slot(repository, SIZE_SLOT), "EXTCODESIZE is the raw length");
        assertArrayEquals(HashUtil.keccak256(code), slot(repository, HASH_SLOT).getData(),
                "EXTCODEHASH is the hash of the raw code");
        assertEquals(DataWord.ZERO, slot(repository, DELEGATECALL_OK_SLOT),
                "DELEGATECALL runs the raw code, whose first byte 0xef is invalid");
        assertEquals(DataWord.ZERO, slot(repository, CALLCODE_OK_SLOT),
                "CALLCODE runs the raw code, whose first byte 0xef is invalid");
        assertEquals(DataWord.ZERO, slot(repository, DataWord.ZERO), "the delegate code never ran");
    }

    private static DataWord slot(Repository repository, DataWord key) {
        DataWord value = repository.getStorageValue(PROBE, key);
        return value == null ? DataWord.ZERO : value;
    }

    private Repository runProbe(byte[] targetCode, ActivationConfig.ForBlock activations) {
        ProgramInvokeMockImpl invoke = new ProgramInvokeMockImpl();
        invoke.setOwnerAddress(PROBE);
        Repository repository = invoke.getRepository();
        repository.createAccount(PROBE);
        repository.createAccount(TARGET);
        repository.saveCode(TARGET, targetCode);
        // as a delegation write or a contract deployment does; without it EXTCODEHASH reports empty code
        repository.initializeStorage(TARGET);
        repository.createAccount(DELEGATE);
        repository.saveCode(DELEGATE, COUNTER_RUNTIME);
        repository.initializeStorage(DELEGATE);
        assertArrayEquals(targetCode, repository.getCode(TARGET));

        VM vm = new VM(vmConfig, precompiledContracts);
        Program program = new Program(vmConfig, precompiledContracts, blockFactory, activations, probeCode(), invoke,
                anyTransaction(), new HashSet<>(), signatureCache);
        while (!program.isStopped()) {
            vm.step(program);
        }
        assertNull(program.getResult().getException(), "the probe itself must finish");
        return repository;
    }

    /**
     * EXTCODESIZE(target) to slot 0x10, EXTCODEHASH(target) to slot 0x11,
     * DELEGATECALL(100000 gas, target, no args, no return) success to slot 0x12,
     * CALLCODE(100000 gas, target, value 0, no args, no return) success to slot 0x13, STOP.
     */
    private static byte[] probeCode() {
        byte[] pushTarget = ByteUtil.merge(new byte[]{0x73}, TARGET.getBytes());
        byte[] pushGas = Hex.decode("620186a0");
        return ByteUtil.merge(
                pushTarget, Hex.decode("3b601055"),
                pushTarget, Hex.decode("3f601155"),
                Hex.decode("6000600060006000"), pushTarget, pushGas, Hex.decode("f4601255"),
                Hex.decode("60006000600060006000"), pushTarget, pushGas, Hex.decode("f2601355"),
                new byte[]{0x00});
    }

    private static Transaction anyTransaction() {
        Account sender = new AccountBuilder().name("sender").build();
        Account receiver = new AccountBuilder().name("receiver").build();
        return new TransactionBuilder().sender(sender).receiver(receiver).value(BigInteger.ONE).build();
    }
}
