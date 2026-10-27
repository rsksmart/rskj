/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
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
package org.ethereum.core;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.db.RepositorySnapshot;
import co.rsk.test.World;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.BlockBuilder;
import com.typesafe.config.ConfigValueFactory;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.vm.DataWord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Postman Section 11: deploy SimpleStorage, Type 4 self-delegate, outer call setValue(42).
 */
class Rskip545RealWorldContractTest {

    private static final byte CHAIN_ID = 33;
    private static final byte[] COW_PK = HashUtil.keccak256("cow".getBytes());
    private static final ECKey COW = ECKey.fromPrivate(COW_PK);
    private static final byte[] SIMPLE_STORAGE_INIT =
            Hex.decode("603980600b6000396000f36004361060205760003560e01c806360fe47b114602557632a1afcd914602d575b600080fd5b600435600055005b60005460005260206000f3");
    private static final byte[] SET_VALUE_42 =
            Hex.decode("60fe47b1000000000000000000000000000000000000000000000000000000000000002a");

    private World world;
    private Account cow;
    private RskAddress simpleStorage;

    @BeforeEach
    void setup() {
        TestSystemProperties config = new TestSystemProperties(rawConfig ->
                rawConfig
                        .withValue("blockchain.config.consensusRules.rskip543", ConfigValueFactory.fromAnyRef(0))
                        .withValue("blockchain.config.consensusRules.rskip546", ConfigValueFactory.fromAnyRef(0))
                        .withValue("blockchain.config.consensusRules.rskip545", ConfigValueFactory.fromAnyRef(0))
        );
        world = new World(config);
        cow = new AccountBuilder(world).name("cow").balance(Coin.valueOf(10).multiply(BigInteger.valueOf(1_000_000_000_000_000_000L))).build();
        simpleStorage = new RskAddress(HashUtil.calcNewAddr(COW.getAddress(), BigInteger.ZERO.toByteArray()));
        assertEquals(new RskAddress(COW.getAddress()), cow.getAddress());
    }

    @Test
    void type4DelegateAndSetValueInSingleTransaction() throws Exception {
        Block parent = world.getBlockChain().getBestBlock();

        Transaction deploy = Transaction.builder()
                .type(TransactionType.TYPE_2)
                .chainId(CHAIN_ID)
                .nonce(BigInteger.ZERO)
                .gasLimit(BigInteger.valueOf(200_000))
                .maxPriorityFeePerGas(Coin.valueOf(1_000_000_000L))
                .maxFeePerGas(Coin.valueOf(2_000_000_000L))
                .data(SIMPLE_STORAGE_INIT)
                .value(Coin.ZERO)
                .build();
        deploy.sign(COW_PK);

        Block deployBlock = mine(parent, List.of(deploy));
        TransactionReceipt deployReceipt = receipt(deployBlock, deploy);
        assertArrayEquals(TransactionReceipt.SUCCESS_STATUS, deployReceipt.getStatus());
        RepositorySnapshot afterDeploy = world.getRepositoryLocator().snapshotAt(deployBlock.getHeader());
        assertTrue(afterDeploy.getCode(simpleStorage).length > 0, "SimpleStorage deployed at " + simpleStorage);

        SetCodeAuthorization auth = Rskip545TestSupport.createSignedAuthorization(
                COW, simpleStorage, BigInteger.valueOf(2), CHAIN_ID);

        Transaction type4 = Transaction.builder()
                .type(TransactionType.TYPE_4)
                .chainId(CHAIN_ID)
                .nonce(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(500_000))
                .maxPriorityFeePerGas(Coin.valueOf(1_000_000_000L))
                .maxFeePerGas(Coin.valueOf(2_000_000_000L))
                .receiveAddress(cow.getAddress())
                .data(SET_VALUE_42)
                .value(Coin.ZERO)
                .authorizationList(List.of(auth))
                .build();
        type4.sign(COW_PK);

        Block type4Block = mine(deployBlock, List.of(type4));
        TransactionReceipt type4Receipt = receipt(type4Block, type4);
        assertArrayEquals(TransactionReceipt.SUCCESS_STATUS, type4Receipt.getStatus(),
                "Type 4 delegate + setValue should succeed in one tx");

        RepositorySnapshot repo = world.getRepositoryLocator().snapshotAt(type4Block.getHeader());
        byte[] indicator = repo.getCode(cow.getAddress());
        assertTrue(DelegationCodeResolver.isDelegatedCode(indicator));
        assertEquals(simpleStorage, DelegationCodeResolver.extractDelegatedAddress(indicator));

        DataWord slot0 = repo.getStorageValue(cow.getAddress(), DataWord.ZERO);
        assertEquals(DataWord.valueOf(42), slot0,
                "setValue(42) must persist in cow storage after single Type 4 tx");
    }

    private Block mine(Block parent, List<Transaction> txs) {
        Block block = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore())
                .parent(parent)
                .transactions(txs)
                .build();
        ImportResult result = world.getBlockChain().tryToConnect(block);
        assertEquals(ImportResult.IMPORTED_BEST, result);
        return block;
    }

    private TransactionReceipt receipt(Block block, Transaction tx) {
        return world.getReceiptStore()
                .getInMainChain(tx.getHash().getBytes(), world.getBlockStore())
                .orElseThrow()
                .getReceipt();
    }
}
