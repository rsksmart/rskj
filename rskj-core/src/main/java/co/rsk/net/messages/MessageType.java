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

package co.rsk.net.messages;

import co.rsk.net.Status;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.util.RLPList;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.ethereum.util.ByteUtil.byteArrayToInt;

/**
 * Created by mario on 16/02/17.
 */
public enum MessageType {

    STATUS_MESSAGE(1) {
        @Override
        public Message createMessage(RLPList list) {
            byte[] rlpdata = list.get(0).getRLPData();
            long number = rlpdata == null ? 0 : new BigInteger(1, list.get(0).getRLPData()).longValue();
            byte[] hash = list.get(1).getRLPData();
            return new StatusMessage(new Status(number, hash));
        }
    },
    BLOCK_MESSAGE(2) {
        @Override
        public Message createMessage(RLPList list) {
            return new BlockMessage(new Block(list.get(0).getRLPData()));
        }
    },
    GET_BLOCK_MESSAGE(3) {
        @Override
        public Message createMessage(RLPList list) {
            return new GetBlockMessage(list.get(0).getRLPData());
        }
    },
    BLOCK_HEADERS_MESSAGE(4) {
        @Override
        public Message createMessage(RLPList list) {
            return new BlockHeadersMessage(list.getRLPData());
        }
    },
    GET_BLOCK_HEADERS_MESSAGE(5) {
        @Override
        public Message createMessage(RLPList list) {
            return new GetBlockHeadersMessage(list.getRLPData());
        }
    },
    NEW_BLOCK_HASHES(6) {
        @Override
        public Message createMessage(RLPList list) {
            return new NewBlockHashesMessage(list.getRLPData());
        }
    },
    TRANSACTIONS(7) {
        @Override
        public Message createMessage(RLPList list) {
            List<Transaction> txs = new ArrayList<>();

            if (CollectionUtils.isNotEmpty(list))
                list.stream().filter(element ->  element.getRLPData().length <= 1 << 19 /* 512KB */)
                .forEach(element -> txs.add(new Transaction(element.getRLPData())));
            return new TransactionsMessage(txs);
        }
    },
    GET_BLOCK_HEADERS_BY_HASH_MESSAGE(9) {
        @Override
        public Message createMessage(RLPList list) {
            byte[] rlpId = list.get(0).getRLPData();
            byte[] hash = list.get(1).getRLPData();
            byte[] rlpCount = list.get(2).getRLPData();

            long id = rlpId == null ? 0 : new BigInteger(1, rlpId).longValue();
            int count = byteArrayToInt(rlpCount);

            return new GetBlockHeadersByHashMessage(id, hash, count);
        }
    },
    BLOCK_HEADERS_BY_HASH_MESSAGE(10) {
        @Override
        public Message createMessage(RLPList list) {
            return null;
        }
    };

    private int type;

    MessageType(int type) {
        this.type = type;
    }

    public abstract Message createMessage(RLPList list);

    public byte getTypeAsByte() {
        return (byte) this.type;
    }

    public static MessageType valueOfType(int type) {
        for(MessageType mt : MessageType.values()) {
            if(mt.type == type)
                return mt;
        }
        throw new IllegalArgumentException(String.format("Invalid Message Type: %d", type));
    }
}
