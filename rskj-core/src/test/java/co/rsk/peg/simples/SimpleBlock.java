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

package co.rsk.peg.simples;

import co.rsk.core.Coin;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Transaction;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * Created by ajlopez on 6/8/2016.
 */
public class SimpleBlock extends Block {
    private List<Transaction> transactionList;

    public SimpleBlock(byte[] parentHash, byte[] unclesHash, byte[] coinbase, byte[] logsBloom,
                       byte[] difficulty, long number, byte[] gasLimit,
                       long gasUsed, long timestamp, byte[] extraData,
                       byte[] mixHash,
                       byte[] nonce, byte[] receiptsRoot,
                       byte[] transactionsRoot, byte[] stateRoot,
                       List<Transaction> transactionsList, List<BlockHeader> uncleList) {
        super(parentHash, unclesHash, coinbase, logsBloom, difficulty, number, gasLimit, gasUsed,
                timestamp, extraData, mixHash, nonce, receiptsRoot, transactionsRoot, stateRoot,
                    transactionsList, uncleList, Coin.valueOf(10).getBytes(), Coin.ZERO);

        if (transactionsList != null) {
            this.transactionList = Collections.unmodifiableList(transactionsList);
        }
    }

    @Override
    @Nullable
    public List<Transaction> getTransactionsList() {
        return this.transactionList;
    }
}
