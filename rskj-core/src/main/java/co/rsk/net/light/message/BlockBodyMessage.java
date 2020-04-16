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

package co.rsk.net.light.message;

import org.bouncycastle.util.BigIntegers;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.ImmutableTransaction;
import org.ethereum.core.Transaction;
import org.ethereum.util.RLPList;

import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;

import static co.rsk.net.light.LightClientMessageCodes.BLOCK_BODY;
import static org.ethereum.util.RLP.*;

public class BlockBodyMessage extends LightClientMessage {

    private final long id;
    private final List<Transaction> transactions;
    private final List<BlockHeader> uncles;

    public BlockBodyMessage(byte[] encoded, BlockFactory blockFactory) {
        RLPList list = (RLPList) decode2(encoded).get(0);
        byte[] rlpId = list.get(0).getRLPData();
        this.id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();

        RLPList rlpTransactions = (RLPList) decode2(list.get(1).getRLPData()).get(0);

        List<Transaction> transactionList = new LinkedList<>();
        for (int k = 0; k < rlpTransactions.size(); k++) {
            byte[] rlpData = rlpTransactions.get(k).getRLPData();
            Transaction tx = new ImmutableTransaction(rlpData);
            transactionList.add(tx);
        }

        RLPList rlpUncles = (RLPList) decode2(list.get(2).getRLPData()).get(0);

        List<BlockHeader> uncleList = new LinkedList<>();
        for (int k = 0; k < rlpUncles.size(); k++) {
            byte[] rlpData = rlpUncles.get(k).getRLPData();
            BlockHeader uncle = blockFactory.decodeHeader(rlpData);
            uncleList.add(uncle);
        }

        this.transactions = transactionList;
        this.uncles = uncleList;
        this.code = BLOCK_BODY.asByte();
    }

    public BlockBodyMessage(long id, List<Transaction> transactionList, List<BlockHeader> uncleList) {

        this.id = id;
        this.transactions = new LinkedList<>(transactionList);
        this.uncles = new LinkedList<>(uncleList);

        this.code = BLOCK_BODY.asByte();
    }

    @Override
    public byte[] getEncoded() {
        byte[] rlpId = encodeBigInteger(BigInteger.valueOf(getId()));

        byte[][] rlpTransactions = getTransactions().stream()
                .map(Transaction::getEncoded)
                .toArray(byte[][]::new);

        byte[][] rlpUncles = getUncles().stream()
                .map(BlockHeader::getFullEncoded)
                .toArray(byte[][]::new);

        return encodeList(rlpId, encodeList(rlpTransactions), encodeList(rlpUncles));
    }

    @Override
    public Class<?> getAnswerMessage() {
        return null;
    }

    @Override
    public String toString() {
        return "";
    }

    public long getId() {
        return id;
    }

    public List<Transaction> getTransactions() {
        return new LinkedList<>(transactions);
    }

    public List<BlockHeader> getUncles() {
        return new LinkedList<>(uncles);
    }
}
