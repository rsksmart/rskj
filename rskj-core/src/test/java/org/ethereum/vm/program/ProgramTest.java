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

import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.peg.Bridge;
import com.google.common.collect.Sets;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.MessageCall;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.exception.VMException;
import org.ethereum.vm.program.invoke.ProgramInvoke;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProgramTest {

    static final int TOTAL_GAS = 10000;
    protected static final int STACK_STATE_SUCCESS = 1;
    protected static final int STACK_STATE_ERROR = 0;

    private final TestSystemProperties config = new TestSystemProperties();
    private final PrecompiledContracts precompiledContracts = new PrecompiledContracts(config, null);

    private final ProgramInvoke programInvoke = mock(ProgramInvoke.class);
    private final MessageCall msg = mock(MessageCall.class);
    private final Repository repository = mock(Repository.class);

    private PrecompiledContracts.PrecompiledContract precompiledContract;
    protected long gasCost;
    protected Program program;

    @BeforeEach
    void setup() {
        final ActivationConfig.ForBlock activations = getBlockchainConfig();
        precompiledContract = spy(precompiledContracts.getContractForAddress(activations, PrecompiledContracts.ECRECOVER_ADDR_DW));
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
            Sets.newHashSet()
        );
        program.getResult().spendGas(TOTAL_GAS);
    }

    @Test
    void testCallToPrecompiledAddress_success() throws VMException {
        when(precompiledContract.execute(any())).thenReturn(new byte[]{1});

        program.callToPrecompiledAddress(msg, precompiledContract);

        assertStack(STACK_STATE_SUCCESS);

        assertEquals(gasCost, program.getResult().getGasUsed());
        assertEquals(gasCost, program.getResult().getGasUsed());
    }


    @Test
    void testCallToPrecompiledAddress_throwRuntimeException() throws VMException {
        when(precompiledContract.execute(any())).thenThrow(new RuntimeException());
        Assertions.assertThrows(RuntimeException.class, () -> program.callToPrecompiledAddress(msg, precompiledContract));
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
        when(bridge.execute(any())).thenReturn(new byte[]{1});

        program.callToPrecompiledAddress(msg, bridge);

        verify(bridge, atLeastOnce()).init(
            any(Transaction.class),
            any(Block.class),
            any(Repository.class),
            isNull(),
            isNull(),
            anyList()
        );
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
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(any())).thenReturn(true);
        return activations;
    }

}
