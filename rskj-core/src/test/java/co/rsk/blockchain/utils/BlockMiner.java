package co.rsk.blockchain.utils;

import co.rsk.crypto.Sha3Hash;
import co.rsk.mine.MinerUtils;
import co.rsk.util.DifficultyUtils;
import org.ethereum.core.Block;

import javax.annotation.Nonnull;
import java.math.BigInteger;

import static co.rsk.mine.MinerServerImpl.compressCoinbase;
import static co.rsk.mine.MinerServerImpl.getBitcoinMergedMerkleBranch;

/**
 * Created by ajlopez on 13/09/2017.
 */
public class BlockMiner {
    private static long nextNonceToUse = 0L;

    public static Block mineBlock(Block block) {
        Sha3Hash blockMergedMiningHash = new Sha3Hash(block.getHashForMergedMining());

        co.rsk.bitcoinj.core.NetworkParameters bitcoinNetworkParameters = co.rsk.bitcoinj.params.RegTestParams.get();
        co.rsk.bitcoinj.core.BtcTransaction bitcoinMergedMiningCoinbaseTransaction = MinerUtils.getBitcoinMergedMiningCoinbaseTransaction(bitcoinNetworkParameters, blockMergedMiningHash.getBytes());
        co.rsk.bitcoinj.core.BtcBlock bitcoinMergedMiningBlock = MinerUtils.getBitcoinMergedMiningBlock(bitcoinNetworkParameters, bitcoinMergedMiningCoinbaseTransaction);

        BigInteger targetBI = DifficultyUtils.difficultyToTarget(block.getDifficultyBI());

        findNonce(bitcoinMergedMiningBlock, targetBI);

        // We need to clone to allow modifications
        Block newBlock = new Block(block.getEncoded()).cloneBlock();

        newBlock.setBitcoinMergedMiningHeader(bitcoinMergedMiningBlock.cloneAsHeader().bitcoinSerialize());

        bitcoinMergedMiningCoinbaseTransaction = bitcoinMergedMiningBlock.getTransactions().get(0);
        co.rsk.bitcoinj.core.PartialMerkleTree bitcoinMergedMiningMerkleBranch = getBitcoinMergedMerkleBranch(bitcoinMergedMiningBlock);

        newBlock.setBitcoinMergedMiningCoinbaseTransaction(compressCoinbase(bitcoinMergedMiningCoinbaseTransaction.bitcoinSerialize()));
        newBlock.setBitcoinMergedMiningMerkleProof(bitcoinMergedMiningMerkleBranch.bitcoinSerialize());

        return newBlock;
    }

    /**
     * findNonce will try to find a valid nonce for bitcoinMergedMiningBlock, that satisfies the given target difficulty.
     *
     * @param bitcoinMergedMiningBlock bitcoinBlock to find nonce for. This block's nonce will be modified.
     * @param target                   target difficulty. Block's hash should be lower than this number.
     */
    public static void findNonce(@Nonnull final co.rsk.bitcoinj.core.BtcBlock bitcoinMergedMiningBlock,
                              @Nonnull final BigInteger target) {
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
