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

import co.rsk.RskContext;
import co.rsk.cli.RskCli;
import co.rsk.crypto.Keccak256;
import org.ethereum.core.Genesis;
import org.ethereum.util.RskTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class GenesisHashesTest {

    @TempDir
    public Path tempDir;

    @Test
    void mainnetHashTest() {
        RskContext rskContext = new RskTestContext(tempDir);
       // RskCli rskCli = new RskCli();
     //   rskCli.load(new String[]{ "--main" });
       // RskContext rskContext = new RskTestContext(rskCli);
        rskContext.getBlockchain(); // this triggers changes in the Genesis through the BlockChainLoader
        Genesis genesis = rskContext.getGenesis();
        assertThat(genesis.getHash(), is(new Keccak256("f88529d4ab262c0f4d042e9d8d3f2472848eaafe1a9b7213f57617eb40a9f9e0")));

        rskContext.close();
    }

    @Test
    void testnetHashTest() {
        RskCli rskCli = new RskCli();
        rskCli.load(new String[]{ "--testnet" });
        RskContext rskContext = new RskTestContext(tempDir, "--testnet" );
        rskContext.getBlockchain(); // this triggers changes in the Genesis through the BlockChainLoader
        Genesis genesis = rskContext.getGenesis();
        assertThat(genesis.getHash(), is(new Keccak256("cabb7fbe88cd6d922042a32ffc08ce8b1fbb37d650b9d4e7dbfe2a7469adfa42")));

        rskContext.close();
    }
}
