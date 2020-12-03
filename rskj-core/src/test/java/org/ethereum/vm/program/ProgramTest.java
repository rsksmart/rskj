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
import com.google.common.collect.Sets;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Repository;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.MessageCall;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.exception.PrecompiledContractException;
import org.ethereum.vm.program.invoke.ProgramInvoke;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ProgramTest {

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

    @Before
    public void setup() {
        final ActivationConfig.ForBlock activations = getBlockchainConfig();
        precompiledContract = spy(precompiledContracts.getContractForAddress(activations, PrecompiledContracts.ECRECOVER_ADDR_DW));
        gasCost = precompiledContract.getGasForData(DataWord.ONE.getData());

        when(repository.startTracking()).thenReturn(repository);
        when(repository.getBalance(any())).thenReturn(Coin.valueOf(20l));

        when(programInvoke.getOwnerAddress()).thenReturn(DataWord.ONE);
        when(programInvoke.getRepository()).thenReturn(repository);

        when(msg.getCodeAddress()).thenReturn(DataWord.ONE);
        when(msg.getType()).thenReturn(MessageCall.MsgType.CALL);
        when(msg.getEndowment()).thenReturn(DataWord.ONE);
        when(msg.getOutDataOffs()).thenReturn(DataWord.ONE);
        when(msg.getOutDataSize()).thenReturn(DataWord.ONE);
        when(msg.getInDataOffs()).thenReturn(DataWord.ONE);
        when(msg.getInDataSize()).thenReturn(DataWord.ONE);
        when(msg.getGas()).thenReturn(DataWord.valueOf(TOTAL_GAS));
        program = new Program(config.getVmConfig(), precompiledContracts, null, activations, null, programInvoke, null, Sets.newHashSet());
        program.getResult().spendGas(TOTAL_GAS);
    }

    @Test
    public void testCallToPrecompiledAddress_success() throws PrecompiledContractException {
        when(precompiledContract.execute(any())).thenReturn(new byte[]{1});

        program.callToPrecompiledAddress(msg, precompiledContract);

        assertStack(STACK_STATE_SUCCESS);

        assertEquals(gasCost, program.getResult().getGasUsed());
        assertEquals(gasCost, program.getResult().getGasUsed());
    }


    @Test
    public void testCallToPrecompiledAddress_throwRuntimeException() throws PrecompiledContractException {
        when(precompiledContract.execute(any())).thenThrow(new RuntimeException());

        program.callToPrecompiledAddress(msg, precompiledContract);

        assertStack(STACK_STATE_ERROR);
        assertEquals(gasCost, program.getResult().getGasUsed());
    }

    @Test
    public void testCallToPrecompiledAddress_throwPrecompiledConstractException() throws PrecompiledContractException {
        when(precompiledContract.execute(any())).thenThrow(new PrecompiledContractException("Revert exception"));

        program.callToPrecompiledAddress(msg, precompiledContract);

        assertStack(STACK_STATE_ERROR);
        assertEquals(gasCost, program.getResult().getGasUsed());
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
