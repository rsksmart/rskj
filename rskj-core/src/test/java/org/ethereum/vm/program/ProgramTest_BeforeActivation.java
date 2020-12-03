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
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.vm.exception.PrecompiledContractException;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ProgramTest_BeforeActivation extends ProgramTest {

    @Override()
    @Test
    public void testCallToPrecompiledAddress_throwRuntimeException() throws PrecompiledContractException {
        try {
            super.testCallToPrecompiledAddress_throwRuntimeException();
            fail();
        } catch (RuntimeException e) {
            assertTrue(program.getStack().empty());
        }
    }

    @Override
    @Test
    public void testCallToPrecompiledAddress_throwPrecompiledConstractException() throws PrecompiledContractException {
        try {
            super.testCallToPrecompiledAddress_throwPrecompiledConstractException();
            fail();
        } catch (RuntimeException e) {
            assertTrue(program.getStack().empty());
        }
    }

    @Override
    protected ActivationConfig.ForBlock getBlockchainConfig() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(any())).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIPNEW)).thenReturn(false);
        return activations;
    }
}
