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

package co.rsk.blockchain.utils;

import co.rsk.core.bc.BlockChainImpl;
import co.rsk.mine.MinimumGasPriceCalculator;
import co.rsk.peg.PegTestUtils;
import co.rsk.peg.simples.SimpleBlock;
import co.rsk.peg.simples.SimpleRskTransaction;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieImpl;
import org.apache.commons.collections4.CollectionUtils;
import org.ethereum.core.*;
import org.ethereum.core.genesis.InitialAddressState;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.BIUtil;
import org.ethereum.util.RLP;
import org.spongycastle.pqc.math.linearalgebra.ByteUtils;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.ethereum.core.Genesis.getZeroHash;
import static org.ethereum.crypto.HashUtil.EMPTY_TRIE_HASH;
import static org.ethereum.util.ByteUtil.wrap;

/**
 * Created by ajlopez on 5/10/2016.
 */
public class BlockGenerator {
    private static final BlockGenerator INSTANCE = new BlockGenerator();

    private static final byte[] EMPTY_LIST_HASH = HashUtil.sha3(RLP.encodeList());

    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    // from bcValidBlockTest.json
    private static final String genesisRLP = "f901fcf901f7a00000000000000000000000000000000000000000000000000000000000000000a01dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347948888f1f195afa192cfee860698584c030f4c9db1a07dba07d6b448a186e9612e5f737d1c909dce473e53199901a302c00646d523c1a056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421a056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421b90100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000008302000080832fefd8808454c98c8142a056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421880102030405060708c0c0";

    private static final String[] blockRlps = {
            genesisRLP,
            "f902b4f902afa0abff92b32e43e9f34eda3fa7fe5359cb06871b172226a829daa9af22d1fac2cea01dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347949efa02278cc63dc612c174976f11037d382f8b67a0c5d6ad68162cb8f04ef7afc8bf74558a19bceaf334b0497bd8d3c86c24de9f9da056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421a0a8f57ab26ee2c88d15f6bd20f052dbc76a1f4a0b55d214a88f4b8201b8840736b901000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000083020000018407fe000080845730e70e808080b85004000000f08613eff431f121c059541e38594a190d6b0e46d2cb2bb52dc6b692020000003b8cacca5ed46c229a777c7c55ddfefed2b78ccb4ce6df07ecb1e36bde29f4741ee73057ffff7f20005a40aca701000000013b8cacca5ed46c229a777c7c55ddfefed2b78ccb4ce6df07ecb1e36bde29f4740101b86100000000000000801f506f4152ccdb46a5e162488b24647e750c88073c56530d2154475fdc8c4cb4ac00000000000000002b6a524f4f5453544f434b3a1ff8eae11ecb36803b0fd23bd31490bd1054e078852a0f975428871c42bef40900000000800ac0c0",
            "f902b4f902afa0c15c503127c6c70f53666806a336ab7600c5d7aa86bf2bd9149acb48b3353ffba01dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347949efa02278cc63dc612c174976f11037d382f8b67a0feac8b5b3fdaad27bc3a289646b6c44464c4b89ce30d1649cfe39e2b26323526a056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421a0e2bdde9667f20119e55bc4d60dd80c14fca25caf985b8523e20c649b1113b06eb901000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000083020000028407fc008080845730e726808080b85004000000f08613eff431f121c059541e38594a190d6b0e46d2cb2bb52dc6b69202000000899e557890240f889575a7dbb3249e6a9cb3df8933ffd29fd88b0a559229b11f28e73057ffff7f20804a9c66a70100000001899e557890240f889575a7dbb3249e6a9cb3df8933ffd29fd88b0a559229b11f0101b861000000000000008034d70d272689bd240d2c25c5d15b5bb7242c6979011ec2167faad2b90c079c2fac00000000000000002b6a524f4f5453544f434b3acb21587449c2d39b94bf06c2b81ea74d83ad1b4de7d425cfce01253796b2d85a00000000800ac0c0",
            "f9031ff902b3a0c5ff6a7292616ea38273cbde89520a24b9e117cab75d0439313fbe539b351ebca01dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347949efa02278cc63dc612c174976f11037d382f8b67a0b36f7def24114af7bc148c1b911e5a75fc7e7a8b6f05f9dd8aebe5204aafb41da00254dfb821f03ebf1660588345960c9528e214998f6ce5c996136410554d4a5fa0f0c28d912ccc2e025463f937def58f171f89f274367cdb6b13aaf604b202da0db901000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000083020040038407fa017f825208845730e72f808080b85004000000f08613eff431f121c059541e38594a190d6b0e46d2cb2bb52dc6b69202000000148469a712391f2d0fd4394b39a2f8fa878f37b63268a26f6b1403a9c45a2cfc30e73057ffff7f20800911a8a70100000001148469a712391f2d0fd4394b39a2f8fa878f37b63268a26f6b1403a9c45a2cfc0101b861000000000000008031377ae680b963a4968b540f3a6aa00651fa1ac60c07d39f5c3ef715759096c2ac00000000000000002b6a524f4f5453544f434b3ac25da85d2292da795489ab2eb6b534eb3def0cd6e29f1ddbe09f26d74dca325a000000008252080af866f864800182520894e42d40b27a5f18685520f0f22aea086181ed61508502540be400801ca05aaaf420781ee3ca97df2543809edb52e41c95d2cfbda16d8fc6772abeae221ca032e8c5f3ed3de052c55b57f5ea3d7c882dabb8c4f758f990579236af7c2fe4e6c0",
            "f902b4f902afa02224b34b6bd4a0b4a4dc2b9aa67d86dd401a4c180d519022f85e500ff82f7637a01dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347949efa02278cc63dc612c174976f11037d382f8b67a0bbef991ea1691a0ee9eeebeda57dde7fe3f80f589f7abd47bf16d8cd04b964c3a056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421a0249b2f538c284002a2068409d2cbdcba3a74072cea3dcaf1bcef415e86acbaf6b901000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000083020080048407f802fe80845730e732808080b85004000000f08613eff431f121c059541e38594a190d6b0e46d2cb2bb52dc6b69202000000e8d9789a4509cb1f3d775d267b7f2fa7b5291f56717dc962dc319e9d706c84ee33e73057ffff7f2080012abca70100000001e8d9789a4509cb1f3d775d267b7f2fa7b5291f56717dc962dc319e9d706c84ee0101b861000000000000008010a3283751f55cfc66e4f5e3691330ef9fe39b86852b98e0fef969ab2bfb16cbac00000000000000002b6a524f4f5453544f434b3a772c6a4f889a9cf4bc7ec380c9014dbf0f8cb529155282a4ea78ccb18f18fd7600000000800ac0c0"
    };

    /**
     * @deprecated
     * Using this singleton instance is a bad idea because {@link #count} will be shared by all tests.
     * This dependency makes tests flaky and prevents us from running tests in parallel or unordered.
     */
    public static BlockGenerator getInstance() {
        return INSTANCE;
    }


    private int count = 0;

    public Genesis getGenesisBlock() {
        return new Genesis(Hex.decode(genesisRLP));
    }

    private Block getNewGenesisBlock(long initialGasLimit, Map<byte[], BigInteger> preMineMap, byte difficultyByte) {

        byte[] nonce       = new byte[]{0};
        byte[] difficulty  = new byte[]{difficultyByte};
        byte[] mixHash     = new byte[]{0};

        /* Unimportant address. Because there is no subsidy
        ECKey ecKey;
        byte[] address;
        SecureRandom rand =new InsecureRandom(0);
        ecKey = new ECKey(rand);
        address = ecKey.getAddress();
        */
        byte[] coinbase    = Hex.decode("e94aef644e428941ee0a3741f28d80255fddba7f");

        long   timestamp         = 0; // predictable timeStamp

        byte[] parentHash  = EMPTY_BYTE_ARRAY;
        byte[] extraData   = EMPTY_BYTE_ARRAY;

        long   gasLimit         = initialGasLimit;

        byte[] bitcoinMergedMiningHeader = null;
        byte[] bitcoinMergedMiningMerkleProof = null;
        byte[] bitcoinMergedMiningCoinbaseTransaction = null;

        Genesis genesis = new Genesis(parentHash, EMPTY_LIST_HASH, coinbase, getZeroHash(),
                difficulty, 0, gasLimit, 0, timestamp, extraData,
                mixHash, nonce, bitcoinMergedMiningHeader, bitcoinMergedMiningMerkleProof,
                bitcoinMergedMiningCoinbaseTransaction, BigInteger.valueOf(100L).toByteArray());
        if (preMineMap!=null) {
            Map<ByteArrayWrapper, InitialAddressState> preMineMap2 = generatePreMine(preMineMap);
            genesis.setPremine(preMineMap2);

            byte[] rootHash = generateRootHash(preMineMap2);
            genesis.setStateRoot(rootHash);

        }
        return genesis;
    }

    private byte[] generateRootHash(Map<ByteArrayWrapper, InitialAddressState> premine){
        Trie state = new TrieImpl(null, true);

        for (ByteArrayWrapper key : premine.keySet())
            state = state.put(key.getData(), premine.get(key).getAccountState().getEncoded());

        return state.getHash();
    }

    private Map<ByteArrayWrapper, InitialAddressState> generatePreMine(Map<byte[], BigInteger> alloc){
        Map<ByteArrayWrapper, InitialAddressState> premine = new HashMap<>();
        for (byte[] key : alloc.keySet()){
            AccountState acctState = new AccountState(BigInteger.valueOf(0), alloc.get(key));
            premine.put(wrap(key), new InitialAddressState(acctState, null));
        }

        return premine;
    }

    public Block getBlock(int number) {
        return new Block(Hex.decode(blockRlps[number]));
    }

    public Block createChildBlock(Block parent) {
        return createChildBlock(parent, 0);
    }

    public Block createChildBlock(Block parent, long fees, List<BlockHeader> uncles, byte[] difficulty) {
        List<Transaction> txs = new ArrayList<>();
        byte[] unclesListHash = HashUtil.sha3(BlockHeader.getUnclesEncodedEx(uncles));

        return new Block(
                parent.getHash(), // parent hash
                unclesListHash, // uncle hash
                parent.getCoinbase(),
                ByteUtils.clone(new Bloom().getData()),
                difficulty, // difficulty
                parent.getNumber() + 1,
                parent.getGasLimit(),
                parent.getGasUsed(),
                parent.getTimestamp() + ++count,
                EMPTY_BYTE_ARRAY,   // extraData
                EMPTY_BYTE_ARRAY,   // mixHash
                BigInteger.ZERO.toByteArray(),  // provisory nonce
                EMPTY_TRIE_HASH,   // receipts root
                BlockChainImpl.calcTxTrie(txs),  // transaction root
                ByteUtils.clone(parent.getStateRoot()), //EMPTY_TRIE_HASH,   // state root
                txs,       // transaction list
                uncles,        // uncle list
                null,
                fees
        );
//        return createChildBlock(parent, 0);
    }

    public Block createChildBlock(Block parent, List<Transaction> txs, byte[] stateRoot) {
        return createChildBlock(parent, txs, stateRoot, parent.getCoinbase());
    }

    public Block createChildBlock(Block parent, List<Transaction> txs, byte[] stateRoot, byte[] coinbase) {
        Bloom logBloom = new Bloom();

        if (txs==null)
            txs = new ArrayList<>();

        return new Block(
                parent.getHash(), // parent hash
                EMPTY_LIST_HASH, // uncle hash
                coinbase, // coinbase
                logBloom.getData(), // logs bloom
                parent.getDifficulty(), // difficulty
                parent.getNumber() + 1,
                parent.getGasLimit(),
                parent.getGasUsed(),
                parent.getTimestamp() + ++count,
                EMPTY_BYTE_ARRAY,   // extraData
                EMPTY_BYTE_ARRAY,   // mixHash
                BigInteger.ZERO.toByteArray(),  // provisory nonce
                EMPTY_TRIE_HASH,   // receipts root
                EMPTY_TRIE_HASH,
                BlockChainImpl.calcTxTrie(txs),  // transaction root
                stateRoot, //EMPTY_TRIE_HASH,   // state root
                txs,       // transaction list
                null,        // uncle list
                null,
                0L
        );
    }

    public Block createChildBlock(Block parent, int ntxs) {
        return createChildBlock(parent, ntxs, BIUtil.toBI(parent.getDifficulty()).longValue());
    }

    public Block createChildBlock(Block parent, int ntxs, long difficulty) {
        List<Transaction> txs = new ArrayList<>();

        for (int ntx = 0; ntx < ntxs; ntx++)
            txs.add(new SimpleRskTransaction(null));

        List<BlockHeader> uncles = new ArrayList<>();

        return createChildBlock(parent, txs, uncles, difficulty, null);
    }

    public Block createChildBlock(Block parent, List<Transaction> txs) {
        return createChildBlock(parent, txs, new ArrayList<>(), BIUtil.toBI(parent.getDifficulty()).longValue(), null);
    }

    public Block createChildBlock(Block parent, List<Transaction> txs, List<BlockHeader> uncles,
                                  long difficulty, BigInteger minGasPrice) {
        return createChildBlock(parent, txs, uncles, difficulty, minGasPrice, parent.getGasLimit());
    }

    public Block createChildBlock(Block parent, List<Transaction> txs, List<BlockHeader> uncles,
                                  long difficulty, BigInteger minGasPrice, byte[] gasLimit) {
        if (txs == null)
            txs = new ArrayList<>();
        if (uncles == null)
            uncles = new ArrayList<>();

        Bloom logBloom = new Bloom();
        byte[] bidiff = BigInteger.valueOf(difficulty).toByteArray();
        byte[] unclesListHash = HashUtil.sha3(BlockHeader.getUnclesEncodedEx(uncles));

        BlockHeader newHeader = new BlockHeader(parent.getHash(),
                unclesListHash,
                parent.getCoinbase(),
                ByteUtils.clone(new Bloom().getData()),
                new byte[]{1},
                parent.getNumber()+1,
                gasLimit,
                0,
                parent.getTimestamp() + ++count,
                new byte[]{},
                new byte[]{},
                new byte[]{},
                new byte[]{},
                (minGasPrice != null) ? minGasPrice.toByteArray() : null,
                CollectionUtils.size(uncles)
        );

        if (difficulty == 0)
            newHeader.setDifficulty(newHeader.calcDifficulty(parent.getHeader()).toByteArray());
        else
            newHeader.setDifficulty(BigInteger.valueOf(difficulty).toByteArray());

        newHeader.setTransactionsRoot(Block.getTxTrie(txs).getHash());

        newHeader.setStateRoot(ByteUtils.clone(parent.getStateRoot()));

        Block newBlock = new Block(newHeader, txs, uncles);

        return newBlock;
    }

    public Block createBlock(int number, int ntxs) {
        Bloom logBloom = new Bloom();
        Block parent = getGenesisBlock();

        List<Transaction> txs = new ArrayList<>();

        for (int ntx = 0; ntx < ntxs; ntx++)
            txs.add(new SimpleRskTransaction(null));

        byte[] parentMGP = (parent.getMinimumGasPrice() != null) ? parent.getMinimumGasPrice() : BigInteger.valueOf(10L).toByteArray();
        BigInteger minimumGasPrice = new MinimumGasPriceCalculator().calculate(new BigInteger(1, parentMGP)
                , BigInteger.valueOf(100L));


        return new Block(
                parent.getHash(), // parent hash
                EMPTY_LIST_HASH, // uncle hash
                parent.getCoinbase(), // coinbase
                logBloom.getData(), // logs bloom
                parent.getDifficulty(), // difficulty
                number,
                parent.getGasLimit(),
                parent.getGasUsed(),
                parent.getTimestamp() + ++count,
                EMPTY_BYTE_ARRAY,   // extraData
                EMPTY_BYTE_ARRAY,   // mixHash
                BigInteger.ZERO.toByteArray(),  // provisory nonce
                EMPTY_TRIE_HASH,   // receipts root
                EMPTY_TRIE_HASH,  // transaction receipts
                EMPTY_TRIE_HASH,
                EMPTY_TRIE_HASH,   // state root
                txs,       // transaction list
                null,        // uncle list
                minimumGasPrice.toByteArray(),
                0L
        );
    }

    public Block createSimpleChildBlock(Block parent, int ntxs) {
        Bloom logBloom = new Bloom();

        List<Transaction> txs = new ArrayList<>();

        for (int ntx = 0; ntx < ntxs; ntx++)
            txs.add(new SimpleRskTransaction(PegTestUtils.createHash3().getBytes()));

        return new SimpleBlock(
                parent.getHash(), // parent hash
                EMPTY_LIST_HASH, // uncle hash
                parent.getCoinbase(), // coinbase
                logBloom.getData(), // logs bloom
                parent.getDifficulty(), // difficulty
                parent.getNumber() + 1,
                parent.getGasLimit(),
                parent.getGasUsed(),
                parent.getTimestamp() + ++count,
                EMPTY_BYTE_ARRAY,   // extraData
                EMPTY_BYTE_ARRAY,   // mixHash
                BigInteger.ZERO.toByteArray(),  // provisory nonce
                EMPTY_TRIE_HASH,   // receipts root
                EMPTY_TRIE_HASH,  // transaction receipts
                EMPTY_TRIE_HASH,
                EMPTY_TRIE_HASH,   // state root
                txs,       // transaction list
                null        // uncle list
        );
    }

    public List<Block> getBlockChain(int size) {
        return getBlockChain(getGenesisBlock(), size);
    }

    public List<Block> getBlockChain(Block parent, int size, long difficulty) {
        return getBlockChain(parent, size,0,false, difficulty);
    }

    public List<Block> getBlockChain(Block parent, int size) {
        return getBlockChain(parent, size, 0);
    }

    public List<Block> getMinedBlockChain(Block parent, int size) {
        return getBlockChain(parent, size, 0, false, true, null);
    }

    public List<Block> getSimpleBlockChain(Block parent, int size) {
        return getSimpleBlockChain(parent, size, 0);
    }

    public List<Block> getBlockChain(Block parent, int size, int ntxs) {
        return getBlockChain(parent, size, ntxs, false);
    }

    public List<Block> getBlockChain(Block parent, int size, int ntxs, boolean withUncles) {
        return getBlockChain(parent, size, ntxs, withUncles, null);
    }

    public List<Block> getBlockChain(Block parent, int size, int ntxs, boolean withUncles, Long difficulty) {
        return getBlockChain(parent, size, ntxs, false, false, difficulty);
    }


    public List<Block> getBlockChain(Block parent, int size, int ntxs, boolean withUncles, boolean withMining, Long difficulty) {
        List<Block> chain = new ArrayList<Block>();
        List<BlockHeader> uncles = new ArrayList<>();
        int chainSize = 0;

        while (chainSize < size) {
            List<Transaction> txs = new ArrayList<>();

            for (int ntx = 0; ntx < ntxs; ntx++) {
                txs.add(new SimpleRskTransaction(null));
            }

            if (difficulty == null) {
                difficulty = 0l;
            }

            Block newblock = createChildBlock(
                    parent, txs, uncles,
                    difficulty,
                    null);

            if (withMining) {
                newblock = BlockMiner.mineBlock(newblock);
            }

            chain.add(newblock);

            if (withUncles) {
                uncles = new ArrayList<>();

                Block newuncle = createChildBlock(parent, ntxs);
                chain.add(newuncle);
                uncles.add(newuncle.getHeader());

                newuncle = createChildBlock(parent, ntxs);
                chain.add(newuncle);
                uncles.add(newuncle.getHeader());
            }

            parent = newblock;
            chainSize++;
        }

        return chain;
    }

    public List<Block> getSimpleBlockChain(Block parent, int size, int ntxs) {
        List<Block> chain = new ArrayList<Block>();

        while (chain.size() < size) {
            Block newblock = createSimpleChildBlock(parent, ntxs);
            chain.add(newblock);
            parent = newblock;
        }

        return chain;
    }

    public Block getNewGenesisBlock(long initialGasLimit, Map<byte[], BigInteger> preMineMap) {
        return getNewGenesisBlock(initialGasLimit,preMineMap, (byte) 0);
    }
}
