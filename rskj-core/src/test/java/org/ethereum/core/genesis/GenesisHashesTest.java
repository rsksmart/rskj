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
import co.rsk.crypto.Keccak256;
import org.ethereum.core.Genesis;
import org.ethereum.util.RskTestContext;
import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class GenesisHashesTest {
    @Test
    public void mainnetHashTest() {
        RskContext rskContext = new RskTestContext(new String[0]); //string array size 0
        rskContext.getBlockchain(); // this triggers changes in the Genesis through the BlockChainLoader
        Genesis genesis = rskContext.getGenesis();
        //System.out.println(genesis);
        assertThat(genesis.getHash(), is(new Keccak256("f88529d4ab262c0f4d042e9d8d3f2472848eaafe1a9b7213f57617eb40a9f9e0")));
    }
    /**this matches up with resources/genesis/rsk-mainnet.json
    
    BlockData [ hash=f88529d4ab262c0f4d042e9d8d3f2472848eaafe1a9b7213f57617eb40a9f9e0
      parentHash=0000000000000000000000000000000000000000000000000000000000000000
      unclesHash=1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347
      coinbase=3333333333333333333333333333333333333333
      stateRoot=9fa70f12726ac738640a86754741bb3f5680520ccc7e6ae9d95ace566a67fe01
      txTrieHash=56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421
      receiptsTrieHash=56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421
      difficulty=1048576
      number=0
      gasLimit=67c280
      gasUsed=0
      timestamp=1514862000 (2018.01.02 03:00:00)
      extraData=486170707920426974636f696e20446179212030332f4a616e2f32303138202d2052534b20746563686e6f6c6f6779206174207468652073657276696365206f6620736f6369657479
      minGasPrice=183000000
    Uncles []
    Txs []
    ] 
     */

    /** 
     * #mish was initially failing with storage rent implementation. But works fine with node versioning. 
     * Genesis on Testnet uses node version 1. Storage rent is node version 2 
    */
    @Test
    public void testnetHashTest() {
        RskContext rskContext = new RskTestContext(new String[]{ "--testnet" });
        rskContext.getBlockchain(); // this triggers changes in the Genesis through the BlockChainLoader
        Genesis genesis = rskContext.getGenesis();
        //System.out.println(genesis);
        assertThat(genesis.getHash(), is(new Keccak256("cabb7fbe88cd6d922042a32ffc08ce8b1fbb37d650b9d4e7dbfe2a7469adfa42"))); // #mish failing test
    }

    /** matches with resources/genesis/orchid-testnet.json
    
    BlockData [ hash=644fbef4b7b65bbc771630c5f348a268a5444aca34c57ed5cc64bbf54e365168
      parentHash=0000000000000000000000000000000000000000000000000000000000000000
      unclesHash=1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347
      coinbase=3333333333333333333333333333333333333333
      
      // #mish before node versioning fix, this was failing. 
      stateRoot=a7d7dbe50fadd39d816c1f928a88ea5aa58bd68a75705d07fc0e12e6881e7a31 // ERROR
      
      txTrieHash=56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421
      receiptsTrieHash=56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421
      difficulty=1048576
      number=0
      gasLimit=4c4b40
      gasUsed=0
      timestamp=0 (1970.01.01 00:00:00)
      extraData=434d272841
      minGasPrice=0
    Uncles []
    Txs []
    ] 
    */
}