/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.remasc;

import static org.junit.Assert.fail;

import co.rsk.config.RemascConfig;
import co.rsk.config.RemascConfigFactory;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.Program;
import org.junit.Before;
import org.junit.Test;

public class RemascContractExecuteTest {

    private static RemascConfig remascConfig =
            new RemascConfigFactory(RemascContract.REMASC_CONFIG).createRemascConfig("regtest");

    private RemascContract remasc;

    @Before
    public void setUp() throws Exception {
        remasc =
                new RemascContract(
                        PrecompiledContracts.REMASC_ADDR,
                        remascConfig,
                        Constants.regtest(),
                        ActivationConfigsForTest.all());
    }

    @Test(expected = Program.OutOfGasException.class)
    public void executeWithFunctionSignatureLengthTooShort() throws Exception {
        remasc.execute(new byte[3]);
        fail("Expected OutOfGasException");
    }

    @Test(expected = Program.OutOfGasException.class)
    public void executeWithInexistentFunction() throws Exception {
        remasc.execute(new byte[4]);
        fail("Expected OutOfGasException");
    }

    @Test(expected = Program.OutOfGasException.class)
    public void executeWithDataLengthTooLong() throws Exception {
        remasc.execute(new byte[6]);
        fail("Expected OutOfGasException");
    }
}
