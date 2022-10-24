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
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.exception.VMException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

class RemascContractExecuteTest {

    private static RemascConfig remascConfig = new RemascConfigFactory(RemascContract.REMASC_CONFIG).createRemascConfig("regtest");

    private RemascContract remasc;

    @BeforeEach
    void setUp() throws Exception {
        remasc = new RemascContract(
                PrecompiledContracts.REMASC_ADDR,
                remascConfig,
                Constants.regtest(),
                ActivationConfigsForTest.all()
        );
    }

    @Test
    void executeWithFunctionSignatureLengthTooShort(){
        Assertions.assertThrows(VMException.class, () -> remasc.execute(new byte[3]));
    }

    @Test
    void executeWithInexistentFunction(){
        Assertions.assertThrows(VMException.class, () -> remasc.execute(new byte[4]));
    }

    @Test
    void executeWithDataLengthTooLong() {
        Assertions.assertThrows(VMException.class, () -> remasc.execute(new byte[6]));
    }
}
