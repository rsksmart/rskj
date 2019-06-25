/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package org.ethereum.core.genesis;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import co.rsk.RskContext;
import co.rsk.crypto.Keccak256;
import org.ethereum.core.Genesis;
import org.ethereum.util.RskTestContext;
import org.junit.Test;

public class GenesisHashesTest {
    @Test
    public void mainnetHashTest() {
        RskContext rskContext = new RskTestContext(new String[0]);
        // this triggers changes in the Genesis through the BlockChainLoader
        rskContext.getBlockchain();
        Genesis genesis = rskContext.getGenesis();
        assertThat(
                genesis.getHash(),
                is(
                        new Keccak256(
                                "f88529d4ab262c0f4d042e9d8d3f2472848eaafe1a9b7213f57617eb40a9f9e0")));
    }

    @Test
    public void testnetHashTest() {
        RskContext rskContext = new RskTestContext(new String[] {"--testnet"});
        // this triggers changes in the Genesis through the BlockChainLoader
        rskContext.getBlockchain();
        Genesis genesis = rskContext.getGenesis();
        assertThat(
                genesis.getHash(),
                is(
                        new Keccak256(
                                "d72e1c76d7b4928acf9812fc3bb5bfddfd1f8d93e3a9a99894b3479a0190a9b0")));
    }
}
