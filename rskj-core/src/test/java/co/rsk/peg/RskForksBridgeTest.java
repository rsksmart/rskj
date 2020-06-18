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

package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.core.TransactionExecutorFactory;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.db.RepositoryLocator;
import co.rsk.test.World;
import co.rsk.test.builders.BlockBuilder;
import co.rsk.trie.TrieStore;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.db.BlockStore;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.ProgramResult;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RskForksBridgeTest {
    private static ECKey fedECPrivateKey = ECKey.fromPrivate(
            BridgeRegTestConstants.REGTEST_FEDERATION_PRIVATE_KEYS.get(0).getPrivKey()
    );

    private RepositoryLocator repositoryLocator;
    private TrieStore trieStore;
    private Repository repository;
    //private ECKey keyHoldingRSKs;
    private ECKey whitelistManipulationKey;
    private Genesis genesis;
    private BlockChainImpl blockChain;
    private Block blockBase;
    private World world;
    private BlockStore blockStore;
    private BridgeSupportFactory bridgeSupportFactory;

    @Before
    public void before() {
        world = new World();
        blockChain = world.getBlockChain();
        blockStore = world.getBlockStore();
        repositoryLocator = world.getRepositoryLocator();
        trieStore = world.getTrieStore();
        repository = world.getRepository();
        bridgeSupportFactory = world.getBridgeSupportFactory();

        whitelistManipulationKey = ECKey.fromPrivate(Hex.decode("3890187a3071327cee08467ba1b44ed4c13adb2da0d5ffcc0563c371fa88259c"));

        genesis = (Genesis)blockChain.getBestBlock();
        //keyHoldingRSKs = new ECKey();
        co.rsk.core.Coin balance = new co.rsk.core.Coin(new BigInteger("10000000000000000000"));
        repository.addBalance(new RskAddress(fedECPrivateKey.getAddress()), balance);

        co.rsk.core.Coin bridgeBalance = co.rsk.core.Coin .fromBitcoin(BridgeRegTestConstants.getInstance().getMaxRbtc());
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, bridgeBalance);
        genesis.setStateRoot(repository.getRoot());
        genesis.flushRLP();

        blockStore.saveBlock(genesis, genesis.getCumulativeDifficulty(), true);

        Transaction whitelistAddressTx = buildWhitelistTx();
        Transaction receiveHeadersTx = buildReceiveHeadersTx();
        Transaction registerBtctransactionTx = buildRegisterBtcTransactionTx();

        blockBase = buildBlock(genesis, whitelistAddressTx, receiveHeadersTx, registerBtctransactionTx);
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(blockBase));
    }

    @Test
    public void testNoFork() throws Exception {
        Transaction releaseTx = buildReleaseTx();
        Block blockB1 = buildBlock(blockBase, releaseTx);
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(blockB1));
        repository = repositoryLocator.startTrackingAt(blockB1.getHeader());
        assertReleaseTransactionState(ReleaseTransactionState.WAITING_FOR_SELECTION);

        Block blockB2 = buildBlock(blockB1);
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(blockB2));
        repository = repositoryLocator.startTrackingAt(blockB2.getHeader());
        assertReleaseTransactionState(ReleaseTransactionState.WAITING_FOR_SELECTION);

        Transaction updateCollectionsTx = buildUpdateCollectionsTx();
        Block blockB3 = buildBlock(blockB2, updateCollectionsTx);
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(blockB3));
        repository = repositoryLocator.startTrackingAt(blockB3.getHeader());
        assertReleaseTransactionState(ReleaseTransactionState.WAITING_FOR_CONFIRMATIONS);
    }

    @Test
    public void testLosingForkBuiltFirst() throws Exception {
        Transaction releaseTx = buildReleaseTx();

        Block blockA1 = buildBlock(blockBase, 4l);
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(blockA1));
        assertReleaseTransactionState(ReleaseTransactionState.NO_TX);

        Block blockA2 = buildBlock(blockA1, 6l, releaseTx);
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(blockA2));
        repository = repositoryLocator.startTrackingAt(blockA2.getHeader());
        assertReleaseTransactionState(ReleaseTransactionState.WAITING_FOR_SELECTION);

        Block blockB1 = buildBlock(blockBase, 1l, releaseTx);
        Assert.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(blockB1));
        assertReleaseTransactionState(ReleaseTransactionState.WAITING_FOR_SELECTION);

        Block blockB2 = buildBlock(blockB1, 2l);
        Assert.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(blockB2));
        assertReleaseTransactionState(ReleaseTransactionState.WAITING_FOR_SELECTION);

        Transaction updateCollectionsTx = buildUpdateCollectionsTx();
        Block blockB3 = buildBlock(blockB2, 10l, updateCollectionsTx);
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(blockB3));
        repository = repositoryLocator.startTrackingAt(blockB3.getHeader());
        assertReleaseTransactionState(ReleaseTransactionState.WAITING_FOR_CONFIRMATIONS);
    }


    @Test
    public void testWinningForkBuiltFirst() throws Exception {
        Transaction releaseTx = buildReleaseTx();

        Block blockB1 = buildBlock(blockBase, releaseTx);
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(blockB1));
        repository = repositoryLocator.startTrackingAt(blockB1.getHeader());
        assertReleaseTransactionState(ReleaseTransactionState.WAITING_FOR_SELECTION);

        Block blockB2 = buildBlock(blockB1,6l);
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(blockB2));
        repository = repositoryLocator.startTrackingAt(blockB2.getHeader());
        assertReleaseTransactionState(ReleaseTransactionState.WAITING_FOR_SELECTION);

        Block blockA1 = buildBlock(blockBase,1);
        Assert.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(blockA1));
        assertReleaseTransactionState(ReleaseTransactionState.WAITING_FOR_SELECTION);

        Block blockA2 = buildBlock(blockA1,1, releaseTx);
        Assert.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(blockA2));
        assertReleaseTransactionState(ReleaseTransactionState.WAITING_FOR_SELECTION);

        Transaction updateCollectionsTx = buildUpdateCollectionsTx();
        Block blockB3 = buildBlock(blockB2,12, updateCollectionsTx);
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(blockB3));
        repository = repositoryLocator.startTrackingAt(blockB3.getHeader());
        assertReleaseTransactionState(ReleaseTransactionState.WAITING_FOR_CONFIRMATIONS);
    }

    @Test
    public void testReleaseTxJustInLoosingFork() throws Exception {
        Transaction releaseTx = buildReleaseTx();

        Block blockA1 = buildBlock(blockBase, releaseTx);
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(blockA1));
        repository = repositoryLocator.startTrackingAt(blockA1.getHeader());
        assertReleaseTransactionState(ReleaseTransactionState.WAITING_FOR_SELECTION);

        Block blockA2 = buildBlock(blockA1,3);
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(blockA2));
        repository = repositoryLocator.startTrackingAt(blockA2.getHeader());
        assertReleaseTransactionState(ReleaseTransactionState.WAITING_FOR_SELECTION);

        Block blockB1 = buildBlock(blockBase,2);
        Assert.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(blockB1));
        assertReleaseTransactionState(ReleaseTransactionState.WAITING_FOR_SELECTION);

        Block blockB2 = buildBlock(blockB1,1);
        Assert.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(blockB2));
        assertReleaseTransactionState(ReleaseTransactionState.WAITING_FOR_SELECTION);

        Transaction updateCollectionsTx = buildUpdateCollectionsTx();
        Block blockB3 = buildBlock(blockB2,6l, updateCollectionsTx);
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(blockB3));
        repository = repositoryLocator.startTrackingAt(blockB3.getHeader());
        assertReleaseTransactionState(ReleaseTransactionState.NO_TX);
    }

    @Test
    public void testReleaseTxJustInWinningFork() throws Exception {
        Transaction releaseTx = buildReleaseTx();

        Block blockA1 = buildBlock(blockBase, 4l);
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(blockA1));
        repository = repositoryLocator.startTrackingAt(blockA1.getHeader());
        assertReleaseTransactionState(ReleaseTransactionState.NO_TX);

        Block blockA2 = buildBlock(blockA1, 5l);
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(blockA2));
        repository = repositoryLocator.startTrackingAt(blockA2.getHeader());
        assertReleaseTransactionState(ReleaseTransactionState.NO_TX);

        Block blockB1 = buildBlock(blockBase, 1l, releaseTx);
        Assert.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(blockB1));
        assertReleaseTransactionState(ReleaseTransactionState.NO_TX);

        Block blockB2 = buildBlock(blockB1, 1l);
        Assert.assertEquals(ImportResult.IMPORTED_NOT_BEST, blockChain.tryToConnect(blockB2));
        assertReleaseTransactionState(ReleaseTransactionState.NO_TX);

        Transaction updateCollectionsTx = buildUpdateCollectionsTx();
        Block blockB3 = buildBlock(blockB2, 10l, updateCollectionsTx);
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(blockB3));
        repository = repositoryLocator.startTrackingAt(blockB3.getHeader());
        assertReleaseTransactionState(ReleaseTransactionState.WAITING_FOR_CONFIRMATIONS);
    }

    private Block buildBlock(Block parent, long difficulty) {
        BlockBuilder blockBuilder = new BlockBuilder(blockChain, bridgeSupportFactory, blockStore).trieStore(trieStore).difficulty(difficulty).parent(parent);
        return blockBuilder.build();
    }

    private Block buildBlock(Block parent, Transaction ... txs) {
        return buildBlock(parent, parent.getDifficulty().asBigInteger().longValue(), txs);
    }

    private Block buildBlock(Block parent, long difficulty, Transaction ... txs) {
        List<Transaction> txList = Arrays.asList(txs);
        BlockBuilder blockBuilder = new BlockBuilder(blockChain, bridgeSupportFactory, blockStore).trieStore(trieStore).difficulty(difficulty).parent(parent).transactions(txList).uncles(new ArrayList<>());
        return blockBuilder.build();
    }

    private Transaction buildWhitelistTx() {
        long nonce = 0;
        long value = 0;
        BigInteger gasPrice = BigInteger.valueOf(0);
        BigInteger gasLimit = BigInteger.valueOf(1000000);
        Transaction rskTx = CallTransaction.createCallTransaction(nonce, gasPrice.longValue(),
                gasLimit.longValue(), PrecompiledContracts.BRIDGE_ADDR, value,
                Bridge.ADD_ONE_OFF_LOCK_WHITELIST_ADDRESS, Constants.REGTEST_CHAIN_ID, new Object[]{ "mhxk5q8QdGFoaP4SJ3DPtXjrbxAgxjNm3C", BigInteger.valueOf(Coin.COIN.multiply(4).value) });
        rskTx.sign(whitelistManipulationKey.getPrivKeyBytes());
        return rskTx;
    }


    private Transaction buildReceiveHeadersTx() {
        String[] serializedHeaders = new String[] {
                "0300000006226e46111a0b59caaf126043eb5bbf28c34f3a5e332a1fc7b2b73cf188910fcb7ce5d0da9e660ea5e3032e281ac765ff4b13a944da0140b9bdca24e24f5090d861f156ffff7f2000000000",
                "030000006983fa12616141cb901b95149e0143863fb5d4044ca3017c84c43ba7be3f526cb5a2bccd28736bdfed3692fc905a7f2b44c09af87f1c4f9d7b22105573689d22d961f156ffff7f2000000000",
                "03000000a9941216a156561e4cd04e030a45da5b9630ddfa42172983b6f9da3f88033b191f7ea963a0f22a270a3574c9ed734f45b5b6d466131e6e870dafcdba8dddf52bd961f156ffff7f2003000000",
                "03000000ce0d1473fe9bf73fc1757959bc8d9dbf8f5ade233def622cae78447a3a289945820e6fed0387d362c98439dd5d68ee4f6fd9bebe6c1b97f6c9136c4818baa689da61f156ffff7f2001000000",
                "030000008216549fbc5cfc04dacd371971958e6deef912a5a30ad70c67d3093153ca7855ba3b9c07d88687abae9fe34c73bddc14dfb55cba454a8c7c359b774a158400dada61f156ffff7f2001000000",
                "0300000094da76181e31cf085bd69ac70b120e258222ca5cf9b9d5841ea3d716708ef63b19d74e636f3dac921a64ef794ccabe36374f609a3b8faab4368f30d50a7f5825da61f156ffff7f2001000000",
                "03000000b7c6355d5a897f674426dc760c0f81cf96bd408080043f35afd757c1fa9b247f4a42d428a651d88522419491e7b6f3a055440daa7096bc50ac841da3c56a0ff7da61f156ffff7f2000000000",
                "0300000086ed0432e702046eb86928a31350d49c336e5f78a084aaa76e7a43623145af167d0d854494d7316867e2acc8b83a373ab488798e013e21867a02542d0bc0d65cdb61f156ffff7f2001000000",
                "03000000e14d679c6acf8686d8bd3a7869815f47553857263bd665a5e7afa9b97c0a8b36065a91ceb292932dc89f1437f94f263b4dcc849653380c85e37983cbe9e2917bdb61f156ffff7f2001000000",
                "030000009100ccf9a455dbeef55e2f8fc6cd41a1ec24cba64a89fb05b7f32fa6ecfaa87bdba893c6d0404c477520022db6b1812385a587fff74a93b2cdd65792b985573ddb61f156ffff7f2001000000",
                "030000000a5e8be74f5ae29cad4683377f653cc233e7511e103dfa6d5cf3329c753b0d46a498ac41bade08b809e096302be853c9271606aada49453629915960eac3e68edb61f156ffff7f2000000000",
                "03000000b15247c02a8f7c707390b3afba2a9d634a82fec25c148da8698a01e8853aed6aa3aa8b85093f35afb74f6870e4bce8aa86301fb2e2b7b5cd308591c2e9c83450db61f156ffff7f2001000000",
                "03000000325bbb7d915ef7cfaede9ab8db1c3354cd20ab8ae08a60f9742a04584feeca3eb35a0a5e570bc4b35dad26a3a3640194eb7545df266a2d9056b8bffc3c9f995ddb61f156ffff7f2000000000",
                "03000000fa393c1a27a1ee1c2d8011f3d260e67527ec3c7c23bb1c39964d7dd3471a4b19d03fb162dd64a151c087f3bdd03d53dcc48b4b7fdaf249414c488de53fdcc8a1dc61f156ffff7f2001000000",
                "030000000010f91e6edce419aa5e4fa27745d0729b461f60ab90f19d2cb378e0c7460f04105692cb1b07fb6d12da26ad5ad340d3643f8b82501c0cace385de0e562c3b7bdc61f156ffff7f2005000000",
                "0300000048615dc57d9e9bc664d4ae05175a8ea86685311491f5697055e18e5a00f54541ea9aed89fd9d764bf71c6b66e697c751da87317f184efae08fda79a73351dab5dc61f156ffff7f2002000000",
                "03000000d5a06c28021993d21a497ec6d6473901679fd3b4e6e99eeba9a6790a678b501b16a39524f5ad49046fe45b29d56e832508ea3f8f51a6f5804c17d89d38949dc3dc61f156ffff7f2000000000",
                "03000000b903ccbb3cf953314af1772a8275fac82e81aa7dafca1f807a0f5a26d8f0896534319fc6586bc50c42cd938bb1f7c523a30df44d790b774d3f336384084216a5dc61f156ffff7f2000000000",
                "03000000f464af7712a449b927bba9bb373507f3aac3ecee79a00d91c9d89b76df98be1f05709404718dd138c79cc0159a84a14388449bb657526d58159e07f7df02f2fbdc61f156ffff7f2004000000",
                "030000009a95a4772f4821a8561082f14a18591ff8b10512dc9ebb78e57a28877b0e22705893d4e79d714af298b231935cebb47e2ee480c74cded044966568bb529e8a95dd61f156ffff7f2005000000",
                "0300000029f92704f9455140f0ceabf1e772de09b37dea7b5a9f2f68bb68745db29b3547d218f0755c0fb782ab4d1e62903ddcf5c7c90335600587f1666f11fe4fb9b97edd61f156ffff7f2000000000",
                "03000000bc49732f16595d8e4165b6d56cead6e447b2e165987f98f9605c184467a6c04ab3e7598636b66ccdd6650ab9f45a066d430f73572dabe033eba155a3b70d866cdd61f156ffff7f2002000000",
                "03000000e50297a6a0e50d32b4d17479bffed65a504c416e3c2aa984e9b72408c8077017176f58d1f847fceb6fcf7041fcb21c5d54ea5c1ed39aa1294f36c2a409cccd72dd61f156ffff7f2002000000",
                "03000000cc624db3560c88e23e870712bbc692a4fe68baf91d87a3b33bd2d8cb2a46584211854ef542e4a234eef6ca89fa938934ba500d9cc79523b9944ed5975ef875c8dd61f156ffff7f2001000000",
                "03000000bb353711a51bbfb909987164f1a29ef23becbed3ecd5c5398050a16fae6c9f398a7bfd2e664e4aaa31afce39f92e6624eea497be4e61b8f39da17c5aec0299b1dd61f156ffff7f2001000000",
                "030000001cd16897dfb4edff3d6cb6991de07791af0c1f622e7f9ddf219190cedf12c6752403b6c3ba9314597cc685f441e01f5ff8f767a8a3c68272c9300cf227506358de61f156ffff7f2003000000",
                "030000009f22f0784b8cfebde833ad5f946b8cd6e1afed4f1effe2263870a4877fc4740ef9892510cfd1aea4b1a8b7ebe3656e3ccfc6d08af0b3fbfdaeb13484fc25c241de61f156ffff7f2000000000",
                "030000002f994d46eb87a3f1f5ca1480cc5672a7d4b4d112c515fae8bd6e035bead83c64a60a25bee417bc21e6338f7361d51b5740c1973d849dafa97d29a13fe2cd6971de61f156ffff7f2002000000",
                "03000000f4bdb805a278b9d31112aa0129b7f137d3902ed9f6cc4fa5d42695cbb99aac3a1f38e42f64fd67a4a7865f2f138a6a0389d2d4aefab72ee9221a3f5241746930de61f156ffff7f2000000000",
                "030000000c56330f6830a7e6daf768bd8201a4ddf2d32b5eb64b1bb4903382468031cf68f862f539a796e91f5606225b18b508d4f469fde68158a0a62e84bc1753ded511de61f156ffff7f2000000000",
                "03000000720c3cd300cede7e23f3f070b1e8c81bc7cf6dd0ab7424ad28082fcdb8fa0f4be8d5822aa8cb4cf6ee46ac86ed484ca0f870bdc7acce26e1b69df877a66bb31fde61f156ffff7f2002000000",
                "03000000c8970ef6b254ced2a19a2bd24b57350adb4ef8c2ab24811d85e240ec909bfe042b8a1e61bee737988fa1c9dbd4857ed54c6ebfbbe242dc937590613e63643228df61f156ffff7f2001000000",
                "03000000916d0cbb4d611277966289e42b6310323fd684300120ea7b8d8ca40e490de838b09ceca193791a6b622d10a1f04ecb34764feb90420433013148d63a80670e0adf61f156ffff7f2003000000",
                "030000005efe665770cc6f3fc616c50ceb2a1f16c46fe8d51ba1a7d60232aeb8b5e4923ee5e9bdb2d123671965f1d447cb47d4f87f6a3729f8d4dd2cdd756f9924927bc0df61f156ffff7f2001000000",
                "030000006913e87d9e42e8f95fc59434b7ca4c1b6ea45a3bade94f8933978ce598f16939dd4aae07274044d6a781a141ff286742dd098228f8cc5fe4265ddac2665e7801df61f156ffff7f2001000000",
                "030000005e3de640bed30c4b17c87f40af0c7e56a5ea27a020cf30ab237a5d4f0659f13e623b7ba0b501dedc3ed46bacebbe171d7cc49d91484a0c86f5da30e3ec781652df61f156ffff7f2000000000",
                "03000000740799c0dd4ba29261ffae24b1c3f61d1e994eda85db08ff355f618a8ee23535a95585518a4ac5c6f4e3ec94ba81cf1299c1060e5ab85466ce3ddca4c0bdac6cdf61f156ffff7f2000000000",
                "030000009ff7108cdbbe0fd07830470f728abb09645b8e38b3e68a847918b3154184e67771ae4174c9ab87f6cb51f1f6fa20fe441fe9d7969e1971d57cc74e6afb5792a6e061f156ffff7f2000000000",
                "0300000020985ccb20f4185f03d29e124e05628a616d84de7b1aa6e935e3d584c4d65435b2075f586656e4aa190115d8447685df88d778b0af7bcc2e29907e812dd41495e061f156ffff7f2001000000",
                "030000009bba64eaf3c338d5da3c1e8ee71eb19dedb662805610f5e9e66f68cc0b55741c99b7aea464577f38dcfec08d7e6989ee3473111b61c4289ae9d2a1d2381cafdbe061f156ffff7f2000000000",
                "030000006bd9fdba0dbf887807adb545275d4d4bd13a9767045d3978e18d3030a21909581305f5c6a069254f26e6f7ac0c21362de91219a77b5352715a3c2783a604f22de061f156ffff7f2000000000",
                "03000000b28075935a7e4d3869c2fbfa43f379d1a859199bf154e642d355a19d696daf6778f1449437b35987e303b98c39b861f5494ebcbf12a1a816bc0c32d1404999c7e061f156ffff7f2000000000",
                "0300000015922987f6a1702f928441b75c1755e12a639df0890a90a34f935521af8f5d79f5abae367a6cc7ad2547a4c9f12472bfac75fc66fbc2e60f2afafb243491bf7de061f156ffff7f2000000000",
                "03000000a2961fc0743a2334f4375b0eb1af4fafb3c146a94308f57c7cb0028f66c8ef501cfb7499085ac2c629ff56bb68fc9f90d025d4c426bc1b08c11b671ff4ee59a8e161f156ffff7f2000000000",
                "030000008bf19eb51bcc1cf920629698a11341b3b4667208be497f293a2876f544b4e93cdda896d045f6efdc31e1ed67f182dc8881aaac1ba1e18f28ae0a252cfcee7aafe161f156ffff7f2000000000",
                "030000005770dbe8ec2c0193667ec33314c6ed88b5b7cf05675869c433646e3deb14db4d09c30d062c010fcddf3b11cc6c019ee229b9ee4d19bd6dd5dd5b4ece4cbebf5de161f156ffff7f2001000000",
                "0300000055122dcdef0149e9b5a2b739d67f3bf8888544ecc9210b6bccd4e5c2418078096d271cdfa762c29263fd034e001858e60c9eb4162328869274f6b0493921507be161f156ffff7f2002000000",
                "030000003561d6c4bf300fdc35b7f53d76ce05d879e1280530b4b74b9301232969870837d3e193f8e3681b0577af04393d6bdc5c4abf6a5b325c189190a6ce513b749432e161f156ffff7f2000000000",
                "0300000029bb4b5a3f1ac60e67d33cca35b1ca4de9cdfe8e44ee16217cf1d0e88f963c2c61ff94701a6e55d3f6c6a74a9b18bf957e93e6652338ad6a172c2e9bf5d725bbe161f156ffff7f2000000000",
                "03000000834a37edfe6058a9e2d9b6855b2265575d29c2d54834223df21ddba4a12e0161a8b0cfd51d8dff22ad7e98724c8a30ed1099fe0a443f80986fd594980a9a67b4e261f156ffff7f2001000000",
                "0300000033555fbfcf92710a004d981ca4daefab2aa8429ebb7727695b24d0da5d86d35348737f902adba445e509bfc5f7970168257ea1ba40e5fd5a2ccf3f279ab75e24e261f156ffff7f2001000000",
                "03000000f6dde4a18fc2eaeab763a6871afed737899d4fbbde5cf8aac00fa75e789c364bc91ab95ec296c588ad4e7c969aca147c898ff99b411698099f56e492153550aae261f156ffff7f2005000000",
                "03000000c75a71292718b5f642c209e5896f34c24445306e3914c86ab244986369260f04ff7685a55a32d59853ee7cd8dc7dc7ac2089d8e2608d101eb1b8c3536a61d943e261f156ffff7f2000000000",
                "030000008baf7ff6c15c2b1c3f4a49e8c642742d43fb0a616cd29defac871c7af134e91f03d891498d8d0b6c4e046ee87e139c064a6058d95fc10f3eb6e1cca3c72677fde261f156ffff7f2001000000",
                "030000000ef81a6569e9ff3665825037d8095f44c1a0071df55ebd6fbc6c0fd8048a941f905ea697327d8c1ef16cf0b7bdf1cf1fd0991fb07638a9a3d0a5c41b439c56aae261f156ffff7f2000000000",
                "03000000a643364b583abc90f990b50b6684a6247ea8314339e4ab1a9b85c221568e6841f028330abd27c125e3ac6783f979eea5fc088ce2d45f9b6c30b6f560d30703cde361f156ffff7f2000000000",
                "0300000085c97fbcacdda2ad2e5c1ad6a5394cfbacb5e59370857065af5019f8ca5fd53e137e4076fdd366d56efc72c0ef6ebc7b5f0c87195909aa41a932860e2403e941e361f156ffff7f2000000000",
                "030000005f2bbd28cd276358f90c9194143f03420d23a8395f73b008e307f2cb9e0f510c743ce32c2bb4136320d24dc05e8f3203144e716d77997764d9df10dc41c1cfade361f156ffff7f2000000000",
                "03000000283b8d73e5615f9d003d8f7cd4d4756b9877d53c72508760f42e4cf258edd354fe6282d7b2254336996a1a6861c4462edf10fbfd900eacba49128d000d53713fe361f156ffff7f2001000000",
                "030000002d67fe89a7530b528ff559b6e16619e44c8ae358ac8477de5e1240c947494f516472fb3b326af801dfdadbd4b3faa9081cb8c87192792ab8441cea38b52e45a7e361f156ffff7f2001000000",
                "030000001e755fb57818048af6eb245ff63eea08b890b7bb8d768d9f057f597da140851e29af51c14cc91ed1e2aab02a00cbae329507dd6222aadc0c5bf98afee444ad10e361f156ffff7f2000000000",
                "03000000fc56cd564fb6cf5ab71b716cfd12b38f15867be1f7cb3b794c593931e0dac052552ed2706b5dc6cd092e48406ef36c461020e5298eb7eb88657315f0ea826c3de461f156ffff7f2000000000",
                "03000000aacbea027e5ef75d45a9f01ac94ace69130edc5535fc44bbeddfc443473d8850c2d82c1301f7d98fc71da2014d44d215615e5f0108460b84f90d282856247208e461f156ffff7f2001000000",
                "03000000db8f1406d36d7c5a6f97696d17729bd02c7dd229460c12a9153d41892898cc15c57da78b08c2ea44f9ead26367c8729b82ede70efd8df57c4fe26afc57f2e244e461f156ffff7f2004000000",
                "03000000cf69b673f02e1754bb1b8f7bb7999c29e14086232290d64168f2cf7d29234d15e3bd2642d0c3f135313dedae7c5c885760cc9eeda2863b857030bbc8bc1794eae461f156ffff7f2001000000",
                "030000000a7e31749d9a0339a06176d0a907377c8e88b297ba5530f85cddbcb093bd6521e797339faac58c68627bed49a0acff8bc8e3900761b55c55c7a405445f03af19e461f156ffff7f2000000000",
                "0300000095702c2f1e2a58c973d87c9a386380c070c84661b5197f43e3076fc5184c964dad3d235291a63d2285b4373ef371186aff632bfaad93a84577f77fdbd7168bdbe461f156ffff7f2000000000",
                "03000000fde2839aa5b9722c022bc50711ef279c72b196c4c49192011252acd95ad0fc53c7a8a873e6e34c24b560651df0fde28b8badc321eec36476fad0f3f5681b5ad3e561f156ffff7f2000000000",
                "03000000a30511c2dc62a76434d71a64369eded402ffbddad991359d19eea7ae0a3bf24ef74025ff3d09be4c0acc1f87dd5979d4dd06c3fbec241df8b1b47a2f08ae991be561f156ffff7f2000000000",
                "030000005720df08252a2670c2a591508f17169b1166efe19359bae311ec587b7940951924ea3ec1863ba4626bf72745dd8cbf6417bb5117bd2c348b7ebbcbc5516a6a19e561f156ffff7f2004000000",
                "030000008c3d681a0bac34d63c358904c12a5fdeefe37a8726a705a4046d88266c05c035e39b951e079aca2179a8a3fbe2553811ecfce8ace08ffa0ce1d8cc68222ce9a9e561f156ffff7f2000000000",
                "03000000f87a82f25beca2fbc41682b537f934ce9689a7115a2bc5229d5b8fb0adae62124fe8b36fb917143c5686d007f21f96797f5a6789300d1630c6e172f40aa1c5ece561f156ffff7f2004000000",
                "030000004031d7cbd26e5885c969a5f713fcd51ac0a214ebbb1d2788cb2b5d06fa4bd934c8765816ad790a8f784c62df6552ff77035302988bb7aafea1f790617ad70114e561f156ffff7f2004000000",
                "030000001b3591fb0714e0689fe1ae220ec248eb238bba93c8d45d3377e1ef86eff1ee2f3aed50a11dd6dbacf315e37b9c7b126733ea351ecc58966dfb48c77a59c29e86e661f156ffff7f2000000000",
                "0300000048d1edb5b98b1044e6ab64b72cca10d7044408ed5fa160d7f9c4315d79318e4c8e7fa8265ad1638dbfc2c77ca5d6f2ead8166d7c4a025e2ea694ad1572147250e661f156ffff7f2000000000",
                "03000000afbe4f9172a99e449dc5595af8aa9e0875ec9f6f070552830aa68521c3648317f22d753efeed9be4519c3c0e2d00949805d4186577a3b52d32875f6e769c00f3e661f156ffff7f2005000000",
                "030000003d8800c809f29cb27d6e83502d0182467098f7eaaf87cf7d498d1b2c2464f01c070f7cff008b49723810ddd8fadf85def6f17c1e4ae0d7052c1a468819e0f94be661f156ffff7f2002000000",
                "03000000fe102d2ed856adaeb4086d42446b85aaffe0f640fd4f952645cd3e7f4cbc1c380fda433e903b010ee9ee7ed516dea0b08e58277b76206f5eaf6b087d7bb4f125e661f156ffff7f2000000000",
                "030000004b87a356ad96d005015b19d08003a3cda73d74f5f5cb949c4ddd5c2f34b0ce7c75d38a7676926e2f8f9d0c4a4e5c7e71555ec639f34fe4c3e592b14f1bc47686e661f156ffff7f2003000000",
                "03000000eab711a6bb42daac8f53fa01007cd4d9f720006efea01d4c1f77e48ee370190fddab4ccb565c66431a213211012330475ee6288d866a81c803e178d0b770aac6e761f156ffff7f2004000000",
                "030000008020e160933eaa5b526a0b43a819005bf2e24dbb57df1a61b185e20444005b239ef6731b139a52ac943e1c2b5173784db7a46503eaebd7906feefc61d7520551e761f156ffff7f2000000000",
                "030000008dbc15d88d429ec618851a15a59012397e49237b89dc6902fd81e1778ba39324ebd7115448add3c77cbef63ecbf2a2e0f534d126341963b7e392f4ada6d34e78e761f156ffff7f2001000000",
                "03000000d63f85da15bb593f688c9e42b3d3fd0f23c0e5647e2b0f8adf3f30a65eec190eca1f644c8b212c6897bcb5680168aa4d04b123dc0f5e606e766f95ef895e7bcce761f156ffff7f2000000000",
                "03000000ac65c7fcb681766cebbf2949642dea30294f7c94d50f8f7e7b743d381e40af3f39c73938e8766c2c5fccc792f05c925e236d9236f8b358fdacca541f9ccf5297e761f156ffff7f2000000000",
                "030000001560736f26b51e6a54f4357310e608e57bbfc83a96e6b19e5cc65cd0bd913e026f630cbede7e445ed541fe7048cf03ec32cbe6975a004140291fd918ba6dd801e761f156ffff7f2000000000",
                "0300000097a3be6bc75c4b6fe75faeb7f9f3a3af69431c2f16ee78c9710626489e5f66667a5f09d0f873b016f42cbea077d0b8c76ba7209ce9d1761ffaa0f5e9f3e83c32e861f156ffff7f2000000000",
                "030000007e1dea9c9cdb12255eab594d66d1b0d6cf3f73a03708d753ed4a3b4d668c32552f5067e4223ad99c7952ae623d3285c4c7ef4fef256ae386f58ab4829d9d382fe861f156ffff7f2000000000",
                "03000000c8ba6f81643f090450547177f8da3a2a2573c8d4501b9108bfb6a0d0da5ac06a9ce84824ca498908063a616c06016e3c5137b422bdc836404dadfa60713c8e05e861f156ffff7f2001000000",
                "03000000a9f6d6e1401a6cb42eb550c8e5c71620d6aee274d65ab2148ed5de48c8beb0100493ced263ad7974ddd1f961e8e05009729daf8d2f7287f8d413e55891a0f75ae861f156ffff7f2000000000",
                "03000000534514afffc2a96d1e58c9cfff2d44afb858f9e38d9fffd3a82578dc2332fb343c2e3741ed601b65ea082e6655076338627d0c750471becd17a673333145834ce861f156ffff7f2001000000",
                "030000009289c07fe4ba952cf0f74c084f39b4e9c6c1cbcea754831f28431a3c26fa992742957295a7af51dab6e524d298833e21fe41f7f602a15ca1b1a9d616c0f6a048e861f156ffff7f2000000000",
                "03000000682f6fca764e974555905474331e8d1fc235a35b451b00002ba7e27bf95aa109bf4b0ec27039b2f548469f9f6a226a77ff3aecc1fc664ef630e640c380bcdea1e961f156ffff7f2000000000",
                "0300000098a13d7992dcabb37c8d1cf413afe353f33822c023f2f12e1a92b06e779d7457af282bb3c1562d95334c2f85a4764024f1081f7d827fc9dde72369bc31693963e961f156ffff7f2001000000",
                "030000002401610cc0fb9cfa958352eb40c843edf7cdd1b583770e5b24bc0d5998f85d16118610a5e768b89edbfd40ab692c85c16cbed5d63a34186872acaa2f8a67045ae961f156ffff7f2000000000",
                "03000000567b370bf6f8e2447ff2e4c9b15dbf10903667bfa71d2d163499a143ed2edb49ab07533834c02f20b71b648d9ff0674fb5dfa6ccfa4ec211419250c34764b5e0e961f156ffff7f2005000000",
                "03000000c19f7d03df17f22c1c48b534566f505fda079eae702873590c32dfdf891524165f9df19fff0c8f5aae9d774becf0e00bd7c62fcdf3e293fc73919e451e5c5fcbe961f156ffff7f2001000000",
                "03000000b1cc00e761fe05686719ab8b53dcacbaf5f97466239d4736a7dc39b1368fff10b3aad51a1da461aaaf84b798af59cde5850ff673fd64cae7c0f9c2eea7a9dadbe961f156ffff7f2002000000",
                "03000000513426f4889b25ced858a069528b216fb5c74890511afb7fb0a2871e2bedaa43eb54a3a235615db22626d08c87323e0d69a2a478d3559c1264a1c7914ff66c37ea61f156ffff7f2000000000",
                "030000000e9b242926a496b99cdb8af31a898c8405e84f11a339ad61e3d4faf8d6e8452eb3029802e551efccedbcf6e90e5b30d8dd32d3b05e21d4d4b28fa851c9dedf30ea61f156ffff7f2000000000",
                "0300000021b111ad1b3220da6345c8125f011396f7a69850fda42a265011ce7325cec80f544abfe2d374a9679ba1e100ac15b4e42e161971214dd0ec3beb840f8e3105e8ea61f156ffff7f2001000000",
                "0300000091095421cd5f52882ecc4a0eaa7852c516ff61f61f3564930ef9e0a30bf09f43097904724f511ae5aabed07be334606fce2cb2e9e4499c16ee3ce801aa6d4e76ea61f156ffff7f2000000000",
                "03000000f3fd1d2c4aee44224cc216461298412638c938599e89c04e99d9be6e30047d3c08aaf345e92fea415c4642c7f63c961d21a04674f8e9ae639d37d17893c221f9d87ff156ffff7f2001000000",
                "030000002a1cf2e8ce85cd3869e6eff4c3c3b4e12c122705609cc3a453400fb13ddc2d25aa3328a6575bac6f520eb83e07c0a74a343563de6e5bd77f25cf5cfa1978e3f2d87ff156ffff7f2000000000",
                "03000000d2f9774aea4a3465058afb84dfc4364124b38f7760b4605c7ec8dacd2afa8975a18e77b1b63489d7d2f00a1778548dc253befcf518dd03638c4fa35e69ba3a78d87ff156ffff7f2002000000",
                "030000000c954700dbb37777fe7579495e9c4d322a74ae1c1a5aff88dc075ddbf9c4092fd8d99e5d8d92fc4db825aa41c8ffbf69f6ee551fce9a2024e236c7539f5bfc31d87ff156ffff7f2001000000",
                "03000000643857f7811cf808072b1f703a391f9b56b964f1fd10674b032cd6d315397e7e3ded8c53ab47c046aba8b209a9501c7aee8e618a98c5aec84f645857066985d8d87ff156ffff7f2001000000",
                "03000000121396c8b40a78b7a446d201f8646033af57c410c72b7bc5f10968b5a90c2051c08ebb3661408aad199851d9418685102dc5a4d57a6a3e9b1c5e4950dc00df85d87ff156ffff7f2000000000",
                "03000000db178ebf0173822cf3f1b53e72bf2f25feea9f33063e8b0d403be3fe23a74c7a875bfdf013be196f7d4d9c818891cd77a119d9ffc8b18d2d6c4054db2dfc651411aff156ffff7f2001000000",
                "03000000f979f676d5f876651021bb44d7d27e6bddac42448518827fe90035bbc9128d3dbc4563a6745a9a07474c4c353df7bbd587857a777bf56051b2363ab015d15ef132c6f156ffff7f2001000000",
                "03000000bf03ac3aa5359892f8e2b8e133e9264b5c2903afea69d182fa17d919971e5d1ce4063a2efb1c4772df46ec7a5f234d89872e88c8a27a2ae2a061b245fe502fe58b77d857ffff7f2001000000"
        };
        Object[] headerArray = Arrays.stream(serializedHeaders).map(h -> Hex.decode(h)).toArray();
        long nonce = 0;
        long value = 0;
        BigInteger gasPrice = BigInteger.valueOf(0);
        BigInteger gasLimit = BigInteger.valueOf(1000000);
        Transaction rskTx = CallTransaction.createCallTransaction(nonce, gasPrice.longValue(),
                gasLimit.longValue(), PrecompiledContracts.BRIDGE_ADDR, value,
                Bridge.RECEIVE_HEADERS, Constants.REGTEST_CHAIN_ID, new Object[]{headerArray});
        rskTx.sign(fedECPrivateKey.getPrivKeyBytes());
        return rskTx;
    }

    private Transaction buildRegisterBtcTransactionTx() {
        String txSerializedEncoded = "0100000001d63a3ceef306c8ffa7cffe1d639491361bdc343f6a2727232771c433730807e7010000006a47304402200ec9f11f92e8d9ff4281685ed88e047a429956f469d2f5070ac754d1c75b18de022001904bf6701954b06871b40bff8709532d65d817073318ebdd0d2ecc032e12ae012103bb5b8063fc6eab12f86c28c865f3aa4337e46e9b8cadad53b167be3682f3e4d2feffffff020084d7170000000017a914896ed9f3446d51b5510f7f0b6ef81b2bde55140e87a041c323000000001976a91473fd38bb63a8fece454677e38e2b8590f8d8b94f88ac5b000000";
        byte[] txSerialized = Hex.decode(txSerializedEncoded);
        int blockHeight = 102;
        String pmtSerializedEncoded = "030000000279e7c0da739df8a00f12c0bff55e5438f530aa5859ff9874258cd7bad3fe709746aff897e6a851faa80120d6ae99db30883699ac0428fc7192d6c3fec0ca6409010d";
        byte[] pmtSerialized = Hex.decode(pmtSerializedEncoded);

        long nonce = 1;
        long value = 0;
        BigInteger gasPrice = BigInteger.valueOf(0);
        BigInteger gasLimit = BigInteger.valueOf(100000);
        Transaction rskTx = CallTransaction.createCallTransaction(nonce, gasPrice.longValue(),
                gasLimit.longValue(), PrecompiledContracts.BRIDGE_ADDR, value,
                Bridge.REGISTER_BTC_TRANSACTION, Constants.REGTEST_CHAIN_ID, txSerialized, blockHeight, pmtSerialized);
        rskTx.sign(fedECPrivateKey.getPrivKeyBytes());
        return rskTx;

    }


    private Transaction buildReleaseTx() throws AddressFormatException {
        String btcAddressString = "mhoDGMzHHDq2ZD6cFrKV9USnMfpxEtLwGm";
        Address btcAddress = Address.fromBase58(RegTestParams.get(), btcAddressString);
        long nonce = 2;
        long value = 1000000000000000000l;
        BigInteger gasPrice = BigInteger.valueOf(0);
        BigInteger gasLimit = BigInteger.valueOf(100000);
        Transaction rskTx = CallTransaction.createCallTransaction(nonce, gasPrice.longValue(),
                gasLimit.longValue(), PrecompiledContracts.BRIDGE_ADDR, value,
                Bridge.RELEASE_BTC, Constants.REGTEST_CHAIN_ID);
        rskTx.sign(fedECPrivateKey.getPrivKeyBytes());
        return rskTx;
    }

    private Transaction buildUpdateCollectionsTx() {
        long nonce = 3;
        long value = 0;
        BigInteger gasPrice = BigInteger.valueOf(0);
        BigInteger gasLimit = BigInteger.valueOf(100000);
        Transaction rskTx = CallTransaction.createCallTransaction(nonce, gasPrice.longValue(),
                gasLimit.longValue(), PrecompiledContracts.BRIDGE_ADDR, value,
                Bridge.UPDATE_COLLECTIONS, Constants.REGTEST_CHAIN_ID);
        rskTx.sign(fedECPrivateKey.getPrivKeyBytes());
        return rskTx;
    }


    private void assertReleaseTransactionState(ReleaseTransactionState state) throws IOException, ClassNotFoundException {
        BridgeState stateForDebugging = callGetStateForDebuggingTx();
        if (ReleaseTransactionState.WAITING_FOR_SELECTION.equals(state)) {
            Assert.assertEquals(1, stateForDebugging.getReleaseRequestQueue().getEntries().size());
            Assert.assertEquals(0, stateForDebugging.getReleaseTransactionSet().getEntries().size());
            Assert.assertEquals(0, stateForDebugging.getRskTxsWaitingForSignatures().size());
        } else if (ReleaseTransactionState.WAITING_FOR_SIGNATURES.equals(state)) {
            Assert.assertEquals(0, stateForDebugging.getReleaseRequestQueue().getEntries().size());
            Assert.assertEquals(0, stateForDebugging.getReleaseTransactionSet().getEntries().size());
            Assert.assertEquals(1, stateForDebugging.getRskTxsWaitingForSignatures().size());
        } else if (ReleaseTransactionState.WAITING_FOR_CONFIRMATIONS.equals(state)) {
            Assert.assertEquals(0, stateForDebugging.getReleaseRequestQueue().getEntries().size());
            Assert.assertEquals(1, stateForDebugging.getReleaseTransactionSet().getEntries().size());
            Assert.assertEquals(0, stateForDebugging.getRskTxsWaitingForSignatures().size());
        } else if (ReleaseTransactionState.NO_TX.equals(state)) {
            Assert.assertEquals(0, stateForDebugging.getReleaseRequestQueue().getEntries().size());
            Assert.assertEquals(0, stateForDebugging.getReleaseTransactionSet().getEntries().size());
            Assert.assertEquals(0, stateForDebugging.getRskTxsWaitingForSignatures().size());
        }
    }

    private enum ReleaseTransactionState {
        NO_TX, WAITING_FOR_SIGNATURES, WAITING_FOR_SELECTION, WAITING_FOR_CONFIRMATIONS
    }

    private BridgeState callGetStateForDebuggingTx() throws IOException {
        TestSystemProperties beforeBambooProperties = new TestSystemProperties();
        Transaction rskTx = CallTransaction.createRawTransaction(0,
                Long.MAX_VALUE,
                Long.MAX_VALUE,
                PrecompiledContracts.BRIDGE_ADDR,
                0,
                Bridge.GET_STATE_FOR_DEBUGGING.encode(new Object[]{}), beforeBambooProperties.getNetworkConstants().getChainId());
        rskTx.sign(BigInteger.ONE.toByteArray());

        TransactionExecutorFactory transactionExecutorFactory = new TransactionExecutorFactory(
                beforeBambooProperties,
                blockStore,
                null,
                new BlockFactory(beforeBambooProperties.getActivationConfig()),
                new ProgramInvokeFactoryImpl(),
                new PrecompiledContracts(beforeBambooProperties, world.getBridgeSupportFactory()),
                world.getBlockTxSignatureCache()
        );
        Repository track = repository.startTracking();
        TransactionExecutor executor = transactionExecutorFactory
                .newInstance(rskTx, 0, blockChain.getBestBlock().getCoinbase(), track, blockChain.getBestBlock(), 0)
                .setLocalCall(true);

        executor.executeTransaction();

        ProgramResult res = executor.getResult();

        Object[] result = Bridge.GET_STATE_FOR_DEBUGGING.decodeResult(res.getHReturn());

        ActivationConfig.ForBlock activations = beforeBambooProperties.getActivationConfig().forBlock(blockChain.getBestBlock().getNumber());
        return BridgeState.create(beforeBambooProperties.getNetworkConstants().getBridgeConstants(), (byte[])result[0], activations);
    }




}
