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

package co.rsk.vm;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.db.RepositorySnapshot;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import com.typesafe.config.ConfigValueFactory;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.vm.DataWord;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.FileNotFoundException;
import java.util.Iterator;

/**
 * Created by Nicolás Perez Santoro on 4/08/2020.
 */
public class ExtCodeHashDslTest {

    public static final byte[] HASH_OF_EMPTY_ARRAY = Keccak256Helper.keccak256(ExtCodeHashTest.EMPTY_BYTE_ARRAY);
    private World world;
    private WorldDslProcessor processor;

    @Before
    public void setup() {
        TestSystemProperties config = new TestSystemProperties(rawConfig ->
                rawConfig.withValue("blockchain.config.hardforkActivationHeights.iris300", ConfigValueFactory.fromAnyRef(5))
        );
        this.world = new World(config);
        this.processor = new WorldDslProcessor(world);

    }
    //in test-rskj.conf the activation block has been set to block height = 5
    //these tests depend on it
    //we want to test the behavior previous to the activation and after the activation
    //note that previous to the activation, extcodehash behaves differently in presence of cache or not

    @Test
    public void invokeEXTCodeHASHAddressDoesNotExist() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/extcodehash/extcodehash_address_does_not_exist.txt");

        processor.processCommands(parser);

        byte[] extcodehash = getExtCodeHashStateForBlockAndContract( "b02", "tx01", 0);
        Assert.assertNull(extcodehash);

        byte[] extcodehash2 = getExtCodeHashStateForBlockAndContract( "b05", "tx01", 0);
        Assert.assertNull(extcodehash2);
    }

    @Test
    public void invokeEXTCodeHASHAddressExistButNotContract() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/extcodehash/extcodehash_address_exists_but_not_contract.txt");

        processor.processCommands(parser);

        byte[] extcodehash = getExtCodeHashStateForBlockAndContract( "b02", "tx01", 0);
        Assert.assertArrayEquals(HASH_OF_EMPTY_ARRAY, extcodehash);

        byte[] extcodehash2 = getExtCodeHashStateForBlockAndContract("b05", "tx01", 0);
        Assert.assertArrayEquals(HASH_OF_EMPTY_ARRAY, extcodehash2);
    }


    @Test
    public void invokeEXTCodeHASHWithCacheEmptyDeploy() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/extcodehash/extcodehash_with_cache_empty_deploy.txt");

        processor.processCommands(parser);

        byte[] extcodehash = getExtCodeHashStateForBlockAndContract("b02", "tx01", 1);
        Assert.assertArrayEquals(HASH_OF_EMPTY_ARRAY, extcodehash);

        byte[] extcodehash2 = getExtCodeHashStateForBlockAndContract("b05", "tx03", 1);
        Assert.assertArrayEquals(HASH_OF_EMPTY_ARRAY, extcodehash2);
    }

    @Test
    public void invokeEXTCodeHASHWithoutCacheEmptyDeploy() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/extcodehash/extcodehash_without_cache_empty_deploy.txt");

        processor.processCommands(parser);

        byte[] extcodehash = getExtCodeHashStateForBlockAndContract("b03", "tx01", 1);
        Assert.assertNull(extcodehash);

        byte[] extcodehash2 = getExtCodeHashStateForBlockAndContract("b04", "tx01", 1);
        Assert.assertNull(extcodehash2);

        byte[] extcodehash3 = getExtCodeHashStateForBlockAndContract("b05", "tx01", 1);
        Assert.assertArrayEquals(HASH_OF_EMPTY_ARRAY, extcodehash3);
    }

    private byte[] getExtCodeHashStateForBlockAndContract(String targetBlock, String targetContract, int storageKey) {
        Transaction creationTransaction = world.getTransactionByName(targetContract);
        RskAddress contractAddress = creationTransaction.getContractAddress();

        RepositorySnapshot finalRepoState = world.getRepositoryLocator().findSnapshotAt(world.getBlockByName(targetBlock).getHeader()).get();

        if(finalRepoState.getStorageKeysCount(contractAddress) > storageKey) {
            Iterator<DataWord> storageKeys = finalRepoState.getStorageKeys(contractAddress);
            if(storageKey == 1) {
                storageKeys.next();
            }
            byte[] extcodehash = finalRepoState.getStorageBytes(contractAddress, storageKeys.next());
            return extcodehash;

        }
        else {
            return null;
        }
    }


}
