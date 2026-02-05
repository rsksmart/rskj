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
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.peg.RepositoryBtcBlockStoreWithCache;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.*;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.VM;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.Stack;
import org.ethereum.vm.program.invoke.ProgramInvokeMockImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.stream.Stream;

import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP125;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP453;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP544;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for RSKIP544/EIP-3541: Reject new contract code starting with the 0xEF byte
 */
class RSKIP544Test {

    private static final String TEST_ADDRESS = "0x0000000000000000000000000000000000001000";
    private static final int TEST_VALUE = 10;
    private static final String ZERO_ADDRESS_RESULT = "0000000000000000000000000000000000000000000000000000000000000000";
    private static final String DEFAULT_SALT = "0x0000000000000000000000000000000000000000000000000000000000000000";

    private ActivationConfig.ForBlock activationConfig;
    private final ProgramInvokeMockImpl invoke = new ProgramInvokeMockImpl();
    private final BytecodeCompiler compiler = new BytecodeCompiler();
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
    private final Transaction transaction = createTransaction();

    @BeforeEach
    void setup() {
        activationConfig = mock(ActivationConfig.ForBlock.class);
        when(activationConfig.isActive(RSKIP125)).thenReturn(true);
        when(activationConfig.isActive(RSKIP453)).thenReturn(true);
        when(activationConfig.isActive(RSKIP544)).thenReturn(true);
    }

    private void setupTestAccount() {
        RskAddress testAddress = new RskAddress(TEST_ADDRESS);
        invoke.setOwnerAddress(testAddress);
        invoke.getRepository().addBalance(testAddress, Coin.valueOf(TEST_VALUE + 1000));
    }

    private String buildCreateCode(String initcodeHex, int initcodeSize) {
        return "PUSH32 0x" + initcodeHex + " " +
                "PUSH1 0x00 MSTORE " +
                "PUSH1 0x" + String.format("%02x", initcodeSize) + " " +
                "PUSH1 0x00 " +
                "PUSH32 " + "0x" + DataWord.valueOf(TEST_VALUE) + " " +
                "CREATE";
    }

    private String buildCreate2Code(String initcodeHex, int initcodeSize, String salt) {
        return "PUSH32 0x" + initcodeHex + " " +
                "PUSH1 0x00 MSTORE " +
                "PUSH32 " + salt + " " +
                "PUSH1 0x" + String.format("%02x", initcodeSize) + " " +
                "PUSH1 0x00 " +
                "PUSH32 " + "0x" + DataWord.valueOf(TEST_VALUE) + " " +
                "CREATE2";
    }

    private void assertCreationFailed(Program program) {
        Stack stack = program.getStack();
        assertEquals(1, stack.size());
        String result = ByteUtil.toHexString(stack.peek().getData());
        assertEquals(ZERO_ADDRESS_RESULT, result);
    }

    private void assertCreationSucceeded(Program program) {
        Stack stack = program.getStack();
        assertEquals(1, stack.size());
        String result = ByteUtil.toHexString(stack.peek().getData());
        assertNotEquals(ZERO_ADDRESS_RESULT, result);
    }

    @Test
    void testCREATE_EmptyCodeStillAllowed() {
        setupTestAccount();

        String codeToExecute = 
                "PUSH32 0x0000000000000000000000000000000000000000000000000000000000000000 " +
                "PUSH1 0x00 MSTORE " +
                "PUSH1 0x00 " +
                "PUSH1 0x00 " +
                "PUSH32 " + "0x" + DataWord.valueOf(TEST_VALUE) + " " +
                "CREATE";

        Program program = executeCode(codeToExecute);
        assertCreationSucceeded(program);
    }

    @ParameterizedTest(name = "[{index}] {2}")
    @MethodSource("efPrefixPatternsForRejection")
    void testCREATE_RejectsAllEFPrefixPatterns(String initcodeHex, int initcodeSize) {
        setupTestAccount();

        String codeToExecute = buildCreateCode(initcodeHex, initcodeSize);
        Program program = executeCode(codeToExecute);
        assertCreationFailed(program);
    }

    @ParameterizedTest(name = "[{index}] {2}")
    @MethodSource("efPrefixPatternsForRejection")
    void testCREATE2_RejectsAllEFPrefixPatterns(String initcodeHex, int initcodeSize) {
        setupTestAccount();

        String codeToExecute = buildCreate2Code(initcodeHex, initcodeSize, DEFAULT_SALT);
        Program program = executeCode(codeToExecute);
        assertCreationFailed(program);
    }

    @ParameterizedTest(name = "[{index}] {2}")
    @MethodSource("allowedCodePatterns")
    void testCREATE_AllowsNonEFPatterns(String initcodeHex, int initcodeSize) {
        setupTestAccount();

        String codeToExecute = buildCreateCode(initcodeHex, initcodeSize);
        Program program = executeCode(codeToExecute);
        assertCreationSucceeded(program);
    }

    @ParameterizedTest(name = "[{index}] {2}")
    @MethodSource("allowedCodePatterns")
    void testCREATE2_AllowsNonEFPatterns(String initcodeHex, int initcodeSize) {
        setupTestAccount();

        String codeToExecute = buildCreate2Code(initcodeHex, initcodeSize, DEFAULT_SALT);
        Program program = executeCode(codeToExecute);
        assertCreationSucceeded(program);
    }

    @ParameterizedTest(name = "[{index}] {1}")
    @MethodSource("differentSaltValues")
    void testCREATE2_DifferentSalts_AllRejectEF(String saltHex) {
        setupTestAccount();

        String initcodeHex = "60ef60005360016000f300000000000000000000000000000000000000000000";
        int initcodeSize = 0x0a;

        String codeToExecute = buildCreate2Code(initcodeHex, initcodeSize, "0x" + saltHex);
        Program program = executeCode(codeToExecute);
        assertCreationFailed(program);
    }

    @ParameterizedTest(name = "[{index}] RSKIP544 active={2}, shouldSucceed={3}")
    @MethodSource("activationBoundaryScenarios")
    void testActivationBoundary_Parameterized(String initcodeHex, int initcodeSize,
                                               boolean rskip544Active, boolean shouldSucceed) {
        when(activationConfig.isActive(RSKIP544)).thenReturn(rskip544Active);
        setupTestAccount();

        String codeToExecute = buildCreateCode(initcodeHex, initcodeSize);
        Program program = executeCode(codeToExecute);
        
        if (shouldSucceed) {
            assertCreationSucceeded(program);
        } else {
            assertCreationFailed(program);
        }
    }

    static Stream<Arguments> efPrefixPatternsForRejection() {
        return Stream.of(
                // Single EF byte
                Arguments.of(
                        "60ef60005360016000f300000000000000000000000000000000000000000000",
                        0x0a
                ),
                // EF00 (2 bytes)
                Arguments.of(
                        "61ef006000526002601ef3000000000000000000000000000000000000000000",
                        0x0b
                ),
                // EF0000 (3 bytes)
                Arguments.of(
                        "60ef60005360036000f300000000000000000000000000000000000000000000",
                        0x0a
                ),
                // EF followed by 31 zeros
                Arguments.of(
                        "60ef60005360206000f300000000000000000000000000000000000000000000",
                        0x0a
                ),
                // EFFF
                Arguments.of(
                        "61efff6000526002601ef3000000000000000000000000000000000000000000",
                        0x0b
                ),
                // EF01
                Arguments.of(
                        "61ef016000526002601ef3000000000000000000000000000000000000000000",
                        0x0b
                )
        );
    }

    static Stream<Arguments> allowedCodePatterns() {
        return Stream.of(
                // 0xFE byte (INVALID opcode)
                Arguments.of(
                        "60fe60005360016000f300000000000000000000000000000000000000000000",
                        0x0a
                ),
                // 0x60 byte (PUSH1 opcode)
                Arguments.of(
                        "606060005360016000f300000000000000000000000000000000000000000000",
                        0x0a
                ),
                // 0x00 byte (STOP opcode)
                Arguments.of(
                        "600060005360016000f300000000000000000000000000000000000000000000",
                        0x0a
                ),
                // Code with EF in middle (0x60EF)
                Arguments.of(
                        "606060005360ef60015360026000f3000000000000000000000000000000000000",
                        0x0e
                ),
                // 0xEE byte (one before EF)
                Arguments.of(
                        "60ee60005360016000f300000000000000000000000000000000000000000000",
                        0x0a
                ),
                // 0xF0 byte (CREATE opcode)
                Arguments.of(
                        "60f060005360016000f300000000000000000000000000000000000000000000",
                        0x0a
                )
        );
    }

    static Stream<Arguments> differentSaltValues() {
        return Stream.of(
                Arguments.of(
                        "0000000000000000000000000000000000000000000000000000000000000000",
                        "salt = 0"
                ),
                Arguments.of(
                        "0000000000000000000000000000000000000000000000000000000000000001",
                        "salt = 1"
                ),
                Arguments.of(
                        "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                        "salt = max (all 0xFF)"
                ),
                Arguments.of(
                        "123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0",
                        "salt = random value"
                ),
                Arguments.of(
                        "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef",
                        "salt = 0xDEADBEEF repeated"
                )
        );
    }

    static Stream<Arguments> activationBoundaryScenarios() {
        return Stream.of(                
                // EF code before activation - should succeed
                Arguments.of(
                        "60ef60005360016000f300000000000000000000000000000000000000000000",
                        0x0a,
                        false,
                        true
                ),
                // EF code after activation - should fail
                Arguments.of(
                        "60ef60005360016000f300000000000000000000000000000000000000000000",
                        0x0a,
                        true,
                        false
                ),
                // EF00 before activation - should succeed
                Arguments.of(
                        "61ef006000526002601ef3000000000000000000000000000000000000000000",
                        0x0b,
                        false,
                        true
                ),
                // EF00 after activation - should fail
                Arguments.of(
                        "61ef006000526002601ef3000000000000000000000000000000000000000000",
                        0x0b,
                        true,
                        false
                ),
                // FE code before activation - should succeed
                Arguments.of(
                        "60fe60005360016000f300000000000000000000000000000000000000000000",
                        0x0a,
                        false,
                        true
                ),
                // FE code after activation - should succeed
                Arguments.of(
                        "60fe60005360016000f300000000000000000000000000000000000000000000",
                        0x0a,
                        true,
                        true
                )
        );
    }

    private static Transaction createTransaction() {
        AccountBuilder acbuilder = new AccountBuilder();
        acbuilder.name("sender");
        Account sender = acbuilder.build();
        acbuilder.name("receiver");
        Account receiver = acbuilder.build();
        TransactionBuilder txbuilder = new TransactionBuilder();
        return txbuilder.sender(sender).receiver(receiver).value(BigInteger.valueOf(2000)).build();
    }

    private Program executeCode(String stringCode) {
        byte[] code = compiler.compile(stringCode);
        VM vm = new VM(vmConfig, precompiledContracts);

        Program program = new Program(vmConfig, precompiledContracts, blockFactory, activationConfig, code, invoke, transaction, new HashSet<>(), new BlockTxSignatureCache(new ReceivedTxSignatureCache()));

        while (!program.isStopped()) {
            vm.step(program);
        }

        return program;
    }
}
