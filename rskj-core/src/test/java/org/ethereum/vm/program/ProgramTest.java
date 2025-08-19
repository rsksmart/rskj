package org.ethereum.vm.program;
/*
 * This file is part of RskJ
 * Copyright (C) 2020 RSK Labs Ltd.
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

import static org.ethereum.vm.PrecompiledContracts.NO_LIMIT_ON_MAX_INPUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;

import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.MessageCall;
import org.ethereum.vm.PrecompiledContractArgs;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.exception.VMException;
import org.ethereum.vm.program.invoke.ProgramInvoke;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import com.google.common.collect.Sets;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.peg.Bridge;

@ExtendWith(MockitoExtension.class)
class ProgramTest {

    static final int TOTAL_GAS = 10000;
    protected static final int STACK_STATE_SUCCESS = 1;
    protected static final int STACK_STATE_ERROR = 0;

    private final TestSystemProperties config = new TestSystemProperties();
    private final PrecompiledContracts precompiledContracts = new PrecompiledContracts(config, null,
            new BlockTxSignatureCache(new ReceivedTxSignatureCache()));

    private final ProgramInvoke programInvoke = mock(ProgramInvoke.class);
    private final MessageCall msg = mock(MessageCall.class);
    private final Repository repository = mock(Repository.class);

    private PrecompiledContracts.PrecompiledContract precompiledContract;
    protected long gasCost;
    protected Program program;

    private final ActivationConfig.ForBlock activations = getBlockchainConfig();

    @BeforeEach
    void setup() {
        precompiledContract = spy(
                precompiledContracts.getContractForAddress(activations, PrecompiledContracts.ECRECOVER_ADDR_DW));
        gasCost = precompiledContract.getGasForData(DataWord.ONE.getData());

        when(repository.startTracking()).thenReturn(repository);
        when(repository.getBalance(any())).thenReturn(Coin.valueOf(20l));

        when(programInvoke.getOwnerAddress()).thenReturn(DataWord.ONE);
        when(programInvoke.getRepository()).thenReturn(repository);
        when(programInvoke.getPrevHash()).thenReturn(DataWord.ONE);
        when(programInvoke.getCoinbase()).thenReturn(DataWord.ONE);
        when(programInvoke.getDifficulty()).thenReturn(DataWord.ONE);
        when(programInvoke.getNumber()).thenReturn(DataWord.ONE);
        when(programInvoke.getGaslimit()).thenReturn(DataWord.ONE);
        when(programInvoke.getTimestamp()).thenReturn(DataWord.ONE);

        when(msg.getCodeAddress()).thenReturn(DataWord.ONE);
        when(msg.getType()).thenReturn(MessageCall.MsgType.CALL);
        when(msg.getEndowment()).thenReturn(DataWord.ONE);
        when(msg.getOutDataOffs()).thenReturn(DataWord.ONE);
        when(msg.getOutDataSize()).thenReturn(DataWord.ONE);
        when(msg.getInDataOffs()).thenReturn(DataWord.ONE);
        when(msg.getInDataSize()).thenReturn(DataWord.ONE);
        when(msg.getGas()).thenReturn(DataWord.valueOf(TOTAL_GAS));

        Transaction transaction = Transaction
                .builder()
                .nonce(BigInteger.ONE.toByteArray())
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(21000))
                .destination(PrecompiledContracts.BRIDGE_ADDR)
                .chainId(config.getNetworkConstants().getChainId())
                .value(BigInteger.TEN)
                .build();
        when(repository.getNonce(any(RskAddress.class))).thenReturn(BigInteger.ONE);

        BlockFactory blockFactory = new BlockFactory(ActivationConfigsForTest.all());

        program = new Program(
                config.getVmConfig(),
                precompiledContracts,
                blockFactory,
                activations,
                null,
                programInvoke,
                transaction,
                Sets.newHashSet(),
                new BlockTxSignatureCache(new ReceivedTxSignatureCache()));
        program.getResult().spendGas(TOTAL_GAS);
    }

    @Test
    void testCallToPrecompiledAddress_success() throws VMException {
        when(precompiledContract.execute(any())).thenReturn(new byte[] { 1 });

        program.callToPrecompiledAddress(msg, precompiledContract);

        assertStack(STACK_STATE_SUCCESS);

        assertEquals(gasCost, program.getResult().getGasUsed());
        assertEquals(gasCost, program.getResult().getGasUsed());
    }

    @Test
    void testCallToPrecompiledAddress_throwRuntimeException() throws VMException {
        when(precompiledContract.execute(any())).thenThrow(new RuntimeException());
        Assertions.assertThrows(RuntimeException.class,
                () -> program.callToPrecompiledAddress(msg, precompiledContract));
    }

    @Test
    void testCallToPrecompiledAddress_throwPrecompiledConstractException() throws VMException {
        when(precompiledContract.execute(any())).thenThrow(new VMException("Revert exception"));

        program.callToPrecompiledAddress(msg, precompiledContract);

        assertStack(STACK_STATE_ERROR);
        assertEquals(gasCost, program.getResult().getGasUsed());
    }

    @Test
    void testCallToPrecompiledAddress_with_logs_success() throws VMException {
        Bridge bridge = mock(Bridge.class);
        when(bridge.execute(any())).thenReturn(new byte[] { 1 });

        program.callToPrecompiledAddress(msg, bridge);

        ArgumentCaptor<PrecompiledContractArgs> argsCaptor = ArgumentCaptor.forClass(PrecompiledContractArgs.class);

        verify(bridge, atLeastOnce()).init(argsCaptor.capture());

        assertNotNull(argsCaptor.getValue().getTransaction());
        assertNotNull(argsCaptor.getValue().getExecutionBlock());
        assertNotNull(argsCaptor.getValue().getRepository());
        assertNull(argsCaptor.getValue().getBlockStore());
        assertNull(argsCaptor.getValue().getReceiptStore());
        assertNotNull(argsCaptor.getValue().getLogs());
        assertNotNull(argsCaptor.getValue().getProgramInvoke());
    }

    @Test
    void testMaxInputTruncationForFixedSizeContracts() throws VMException {
        // given
        // Create a mock contract with fixed maxInput
        PrecompiledContracts.PrecompiledContract fixedSizeContract = mock(
                PrecompiledContracts.PrecompiledContract.class);
        when(fixedSizeContract.getMaxInput()).thenReturn(128); // Like AltBN128Addition
        when(fixedSizeContract.execute(any())).thenReturn(new byte[] { 1 });

        // Create message with large input data
        MessageCall largeMsg = mock(MessageCall.class);
        when(largeMsg.getCodeAddress()).thenReturn(DataWord.ONE);
        when(largeMsg.getType()).thenReturn(MessageCall.MsgType.CALL);
        when(largeMsg.getEndowment()).thenReturn(DataWord.ONE);
        when(largeMsg.getOutDataOffs()).thenReturn(DataWord.ONE);
        when(largeMsg.getOutDataSize()).thenReturn(DataWord.ONE);
        when(largeMsg.getInDataOffs()).thenReturn(DataWord.ONE);
        when(largeMsg.getInDataSize()).thenReturn(DataWord.valueOf(256)); // 256 bytes input
        when(largeMsg.getGas()).thenReturn(DataWord.valueOf(TOTAL_GAS));

        // Mock memory to return large data
        byte[] largeData = new byte[256];
        for (int i = 0; i < 256; i++) {
            largeData[i] = (byte) (i % 256);
        }

        // when
        program.callToPrecompiledAddress(largeMsg, fixedSizeContract);

        // then
        verify(fixedSizeContract).execute(argThat(data -> data.length == 128));

        assertStack(STACK_STATE_SUCCESS);
    }

    @Test
    void testMaxInputNoTruncationForUnlimitedContracts() throws VMException {
        // given
        // Create a mock contract with unlimited maxInput
        PrecompiledContracts.PrecompiledContract unlimitedContract = mock(
                PrecompiledContracts.PrecompiledContract.class);
        when(unlimitedContract.getMaxInput()).thenReturn(NO_LIMIT_ON_MAX_INPUT);
        when(unlimitedContract.execute(any())).thenReturn(new byte[] { 1 });

        // Create message with large input data
        MessageCall largeMsg = mock(MessageCall.class);
        when(largeMsg.getCodeAddress()).thenReturn(DataWord.ONE);
        when(largeMsg.getType()).thenReturn(MessageCall.MsgType.CALL);
        when(largeMsg.getEndowment()).thenReturn(DataWord.ONE);
        when(largeMsg.getOutDataOffs()).thenReturn(DataWord.ONE);
        when(largeMsg.getOutDataSize()).thenReturn(DataWord.ONE);
        when(largeMsg.getInDataOffs()).thenReturn(DataWord.ONE);
        when(largeMsg.getInDataSize()).thenReturn(DataWord.valueOf(1000)); // 1000 bytes input
        when(largeMsg.getGas()).thenReturn(DataWord.valueOf(TOTAL_GAS));

        // when
        program.callToPrecompiledAddress(largeMsg, unlimitedContract);

        // then
        verify(unlimitedContract).execute(argThat(data -> data.length == 1000));

        assertStack(STACK_STATE_SUCCESS);
    }

    @Test
    void testProgramMaxInputTruncationLogicForLimitedContracts() throws VMException {
        // given
        // Create a mock contract with fixed maxInput
        PrecompiledContracts.PrecompiledContract fixedSizeContract = mock(
                PrecompiledContracts.PrecompiledContract.class);
        when(fixedSizeContract.getMaxInput()).thenReturn(128); // Like AltBN128Addition
        when(fixedSizeContract.execute(any())).thenReturn(new byte[] { 1 });

        // Create message with large input data
        MessageCall largeMsg = mock(MessageCall.class);
        when(largeMsg.getCodeAddress()).thenReturn(DataWord.ONE);
        when(largeMsg.getType()).thenReturn(MessageCall.MsgType.CALL);
        when(largeMsg.getEndowment()).thenReturn(DataWord.ONE);
        when(largeMsg.getOutDataOffs()).thenReturn(DataWord.ONE);
        when(largeMsg.getOutDataOffs()).thenReturn(DataWord.ONE);
        when(largeMsg.getOutDataSize()).thenReturn(DataWord.ONE);
        when(largeMsg.getInDataOffs()).thenReturn(DataWord.ONE);
        when(largeMsg.getInDataSize()).thenReturn(DataWord.valueOf(256)); // 256 bytes input
        when(largeMsg.getGas()).thenReturn(DataWord.valueOf(TOTAL_GAS));

        // Mock the memory chunk method to return data of any size
        Program spyProgram = spy(program);
        doAnswer(invocation -> {
            int size = invocation.getArgument(1);
            return generateTestData(size);
        }).when(spyProgram).memoryChunk(anyInt(), anyInt());

        // when
        spyProgram.callToPrecompiledAddress(largeMsg, fixedSizeContract);

        // then
        verify(fixedSizeContract).execute(argThat(data -> data.length == 128));
        assertStack(STACK_STATE_SUCCESS);
    }

    @Test
    void testProgramMaxInputNoTruncationLogicForUnlimitedContracts() throws VMException {
        // given

        // Create a mock contract with unlimited maxInput
        PrecompiledContracts.PrecompiledContract unlimitedContract = mock(
                PrecompiledContracts.PrecompiledContract.class);
        when(unlimitedContract.getMaxInput()).thenReturn(NO_LIMIT_ON_MAX_INPUT);
        when(unlimitedContract.execute(any())).thenReturn(new byte[] { 1 });

        // Create message with large input data
        MessageCall largeMsg = mock(MessageCall.class);
        when(largeMsg.getCodeAddress()).thenReturn(DataWord.ONE);
        when(largeMsg.getType()).thenReturn(MessageCall.MsgType.CALL);
        when(largeMsg.getEndowment()).thenReturn(DataWord.ONE);
        when(largeMsg.getOutDataOffs()).thenReturn(DataWord.ONE);
        when(largeMsg.getOutDataOffs()).thenReturn(DataWord.ONE);
        when(largeMsg.getOutDataSize()).thenReturn(DataWord.ONE);
        when(largeMsg.getInDataOffs()).thenReturn(DataWord.ONE);
        when(largeMsg.getInDataSize()).thenReturn(DataWord.valueOf(1000)); // 1000 bytes input
        when(largeMsg.getGas()).thenReturn(DataWord.valueOf(TOTAL_GAS));

        // Mock the memory chunk method to return data of any size
        Program spyProgram = spy(program);
        doAnswer(invocation -> {
            int size = invocation.getArgument(1);
            byte[] data = new byte[size];
            for (int i = 0; i < size; i++) {
                data[i] = (byte) (i % 256);
            }
            return data;
        }).when(spyProgram).memoryChunk(anyInt(), anyInt());

        // when
        spyProgram.callToPrecompiledAddress(largeMsg, unlimitedContract);

        // then
        verify(unlimitedContract).execute(argThat(data -> data.length == 1000));

        assertStack(STACK_STATE_SUCCESS);
    }

    @Test
    void testProgramMaxInputEdgeCaseZeroWillBeUnlimited() throws VMException {
        // given

        // Create a mock contract with maxInput = 0
        PrecompiledContracts.PrecompiledContract zeroMaxContract = mock(
                PrecompiledContracts.PrecompiledContract.class);
        when(zeroMaxContract.getMaxInput()).thenReturn(0); // Edge case
        when(zeroMaxContract.execute(any())).thenReturn(new byte[] { 1 });

        // Create message with large input data
        MessageCall largeMsg = mock(MessageCall.class);
        when(largeMsg.getCodeAddress()).thenReturn(DataWord.ONE);
        when(largeMsg.getType()).thenReturn(MessageCall.MsgType.CALL);
        when(largeMsg.getEndowment()).thenReturn(DataWord.ONE);
        when(largeMsg.getOutDataOffs()).thenReturn(DataWord.ONE);
        when(largeMsg.getOutDataOffs()).thenReturn(DataWord.ONE);
        when(largeMsg.getOutDataSize()).thenReturn(DataWord.ONE);
        when(largeMsg.getInDataOffs()).thenReturn(DataWord.ONE);
        when(largeMsg.getInDataSize()).thenReturn(DataWord.valueOf(500)); // 500 bytes input
        when(largeMsg.getGas()).thenReturn(DataWord.valueOf(TOTAL_GAS));

        // Mock the memory chunk method to return data of any size
        Program spyProgram = spy(program);
        doAnswer(invocation -> {
            int size = invocation.getArgument(1);
            return generateTestData(size);
        }).when(spyProgram).memoryChunk(anyInt(), anyInt());

        // when
        spyProgram.callToPrecompiledAddress(largeMsg, zeroMaxContract);

        // then
        verify(zeroMaxContract).execute(argThat(data -> data.length == 500));
        assertStack(STACK_STATE_SUCCESS);
    }

    @Test
    void testMaxInputWithExactSize() throws VMException {
        // given
        // Create a mock contract with exact maxInput
        PrecompiledContracts.PrecompiledContract exactSizeContract = mock(
                PrecompiledContracts.PrecompiledContract.class);
        when(exactSizeContract.getMaxInput()).thenReturn(128); // Like ECADD
        when(exactSizeContract.execute(any())).thenReturn(new byte[] { 1 });

        // Create message with exact input data size
        MessageCall exactMsg = mock(MessageCall.class);
        when(exactMsg.getCodeAddress()).thenReturn(DataWord.ONE);
        when(exactMsg.getType()).thenReturn(MessageCall.MsgType.CALL);
        when(exactMsg.getEndowment()).thenReturn(DataWord.ONE);
        when(exactMsg.getOutDataOffs()).thenReturn(DataWord.ONE);
        when(exactMsg.getOutDataSize()).thenReturn(DataWord.ONE);
        when(exactMsg.getInDataOffs()).thenReturn(DataWord.ONE);
        when(exactMsg.getInDataSize()).thenReturn(DataWord.valueOf(128)); // Exactly 128 bytes
        when(exactMsg.getGas()).thenReturn(DataWord.valueOf(TOTAL_GAS));

        // when
        program.callToPrecompiledAddress(exactMsg, exactSizeContract);

        // then
        verify(exactSizeContract).execute(argThat(data -> data.length == 128));

        assertStack(STACK_STATE_SUCCESS);
    }

    /*********************************
     * ---------- UTILS ------------ *
     *********************************/
    private void assertStack(int expected) {
        assertFalse(program.getStack().empty());
        assertEquals(1, program.getStack().size());
        assertEquals(DataWord.valueOf(expected), program.getStack().pop());
    }

    protected ActivationConfig.ForBlock getBlockchainConfig() {
        ActivationConfig.ForBlock localActivations = mock(ActivationConfig.ForBlock.class);
        when(localActivations.isActive(any())).thenReturn(true);
        return localActivations;
    }

    private byte[] generateTestData(int size) {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte) (i % 256);
        }
        return data;
    }
}
