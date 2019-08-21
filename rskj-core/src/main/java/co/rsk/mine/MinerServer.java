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

package co.rsk.mine;

import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import org.ethereum.core.Block;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Optional;


public interface MinerServer {

    void start();

    void stop();

    boolean isRunning();

    SubmitBlockResult submitBitcoinBlockPartialMerkle(
            Keccak256 blockHashForMergedMining,
            BtcBlock blockWithOnlyHeader,
            BtcTransaction coinbase,
            List<String> merkleHashes,
            int blockTxnCount
    );

    SubmitBlockResult submitBitcoinBlockTransactions(
            Keccak256 blockHashForMergedMining,
            BtcBlock blockWithOnlyHeader,
            BtcTransaction coinbase,
            List<String> txHashes
    );

    SubmitBlockResult submitBitcoinBlock(Keccak256 blockHashForMergedMining, BtcBlock bitcoinMergedMiningBlock);

    RskAddress getCoinbaseAddress();

    MinerWork getWork();

    void buildBlockToMine(@Nonnull Block blockToMineOnTopOf, boolean createCompetitiveBlock);

    void buildBlockToMine(boolean createCompetitiveBlock);

    void setExtraData(byte[] extraData);

    Optional<Block> getLatestBlock();
}
