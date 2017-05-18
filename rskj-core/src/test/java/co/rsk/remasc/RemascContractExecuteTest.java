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

import co.rsk.config.RemascConfig;
import co.rsk.config.RemascConfigFactory;
import org.ethereum.config.BlockchainNetConfig;
import org.ethereum.config.SystemProperties;
import org.ethereum.config.blockchain.RegTestConfig;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.Program;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

public class RemascContractExecuteTest {

    private static BlockchainNetConfig blockchainNetConfigOriginal;
    private static RemascConfig remascConfig;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        blockchainNetConfigOriginal = SystemProperties.CONFIG.getBlockchainConfig();
        SystemProperties.CONFIG.setBlockchainConfig(new RegTestConfig());
        remascConfig = new RemascConfigFactory(RemascContract.REMASC_CONFIG).createRemascConfig("regtest");
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        SystemProperties.CONFIG.setBlockchainConfig(blockchainNetConfigOriginal);
    }

    @Test(expected = Program.OutOfGasException.class)
    public void executeWithFunctionSignatureLengthTooShort() throws Exception{
        RemascContract remasc = new RemascContract(PrecompiledContracts.REMASC_ADDR, remascConfig);

        remasc.execute(new byte[3]);
        fail("Expected OutOfGasException");
    }

    @Test(expected = Program.OutOfGasException.class)
    public void executeWithInexistentFunction() throws Exception{
        RemascContract remasc = new RemascContract(PrecompiledContracts.REMASC_ADDR, remascConfig);

        remasc.execute(new byte[4]);
        fail("Expected OutOfGasException");
    }

    @Test(expected = Program.OutOfGasException.class)
    public void executeWithDataLengthTooLong() throws Exception{
        RemascContract remasc = new RemascContract(PrecompiledContracts.REMASC_ADDR, remascConfig);

        remasc.execute(new byte[6]);
        fail("Expected OutOfGasException");
    }
}
