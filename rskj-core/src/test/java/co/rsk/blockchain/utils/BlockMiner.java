package co.rsk.blockchain.utils;

import co.rsk.crypto.Keccak256;
import co.rsk.mine.MinerUtils;
import co.rsk.util.DifficultyUtils;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;

import java.math.BigInteger;

import static co.rsk.mine.MinerServerImpl.compressCoinbase;

/**
 * Created by ajlopez on 13/09/2017.
 */
public class BlockMiner {
    private static long nextNonceToUse = 0L;

    private final BlockFactory blockFactory;
    private final ActivationConfig activationConfig;

    public BlockMiner(ActivationConfig activationConfig) {
        this.activationConfig = activationConfig;
        this.blockFactory = new BlockFactory(activationConfig);
    }

    public Block mineBlock(Block block) {
        Keccak256 blockMergedMiningHash = new Keccak256(block.getHashForMergedMining());

        co.rsk.bitcoinj.core.NetworkParameters bitcoinNetworkParameters = co.rsk.bitcoinj.params.RegTestParams.get();
        co.rsk.bitcoinj.core.BtcTransaction bitcoinMergedMiningCoinbaseTransaction = MinerUtils.getBitcoinMergedMiningCoinbaseTransaction(bitcoinNetworkParameters, blockMergedMiningHash.getBytes());
        co.rsk.bitcoinj.core.BtcBlock bitcoinMergedMiningBlock = MinerUtils.getBitcoinMergedMiningBlock(bitcoinNetworkParameters, bitcoinMergedMiningCoinbaseTransaction);

        BigInteger targetBI = DifficultyUtils.difficultyToTarget(block.getDifficulty());

        findNonce(bitcoinMergedMiningBlock, targetBI);

        Block newBlock = blockFactory.cloneBlockForModification(block);

        newBlock.setBitcoinMergedMiningHeader(bitcoinMergedMiningBlock.cloneAsHeader().bitcoinSerialize());

        bitcoinMergedMiningCoinbaseTransaction = bitcoinMergedMiningBlock.getTransactions().get(0);
        byte[] merkleProof = MinerUtils.buildMerkleProof(
                activationConfig,
                pb -> pb.buildFromBlock(bitcoinMergedMiningBlock),
                newBlock.getNumber()
        );

        newBlock.setBitcoinMergedMiningCoinbaseTransaction(compressCoinbase(bitcoinMergedMiningCoinbaseTransaction.bitcoinSerialize()));
        newBlock.setBitcoinMergedMiningMerkleProof(merkleProof);

        return newBlock;
    }

    /**
     * findNonce will try to find a valid nonce for bitcoinMergedMiningBlock, that satisfies the given target difficulty.
     *
     * @param bitcoinMergedMiningBlock bitcoinBlock to find nonce for. This block's nonce will be modified.
     * @param target                   target difficulty. Block's hash should be lower than this number.
     */
    public void findNonce(co.rsk.bitcoinj.core.BtcBlock bitcoinMergedMiningBlock, BigInteger target) {
        bitcoinMergedMiningBlock.setNonce(nextNonceToUse++);

        while (true) {
            // Is our proof of work valid yet?
            BigInteger blockHashBI = bitcoinMergedMiningBlock.getHash().toBigInteger();

            if (blockHashBI.compareTo(target) <= 0)
                return;

            // No, so increment the nonce and try again.
            bitcoinMergedMiningBlock.setNonce(nextNonceToUse++);
        }
   }
}
