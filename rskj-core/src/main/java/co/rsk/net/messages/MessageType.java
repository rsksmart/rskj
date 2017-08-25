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
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockIdentifier;
import org.ethereum.core.Transaction;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;
import org.spongycastle.util.BigIntegers;

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
            long number = rlpdata == null ? 0 : BigIntegers.fromUnsignedByteArray(list.get(0).getRLPData()).longValue();
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
    BLOCK_HASH_REQUEST_MESSAGE(8) {
        @Override
        public Message createMessage(RLPList list) {
            byte[] rlpId = list.get(0).getRLPData();
            byte[] rlpHeight = list.get(1).getRLPData();
            long id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();
            long height = rlpHeight == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpHeight).longValue();
            return new BlockHashRequestMessage(id, height);
        }
    },
    BLOCK_HASH_RESPONSE_MESSAGE(18) {
        @Override
        public Message createMessage(RLPList list) {
            byte[] rlpId = list.get(0).getRLPData();
            long id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();
            byte[] hash = list.get(1).getRLPData();
            return new BlockHashResponseMessage(id, hash);
        }
    },
    BLOCK_HEADERS_REQUEST_MESSAGE(9) {
        @Override
        public Message createMessage (RLPList list){
            byte[] rlpId = list.get(0).getRLPData();
            byte[] hash = list.get(1).getRLPData();
            byte[] rlpCount = list.get(2).getRLPData();

            long id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();
            int count = byteArrayToInt(rlpCount);

            return new BlockHeadersRequestMessage(id, hash, count);
        }
    },
    BLOCK_HEADERS_RESPONSE_MESSAGE(10) {
        @Override
        public Message createMessage(RLPList list) {
            byte[] rlpId = list.get(0).getRLPData();
            RLPList rlpHeaders = (RLPList)RLP.decode2(list.get(1).getRLPData()).get(0);
            long id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();

            List<BlockHeader> headers = new ArrayList<>();

            for (int k = 0; k < rlpHeaders.size(); k++)
                headers.add(new BlockHeader(rlpHeaders.get(k).getRLPData()));

            return new BlockHeadersResponseMessage(id, headers);
        }
    },
    BLOCK_REQUEST_MESSAGE(11) {
        @Override
        public Message createMessage(RLPList list) {
            byte[] rlpId = list.get(0).getRLPData();
            long id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();
            byte[] hash = list.get(1).getRLPData();
            return new BlockRequestMessage(id, hash);
        }
    },
    BLOCK_RESPONSE_MESSAGE(12) {
        @Override
        public Message createMessage(RLPList list) {
            byte[] rlpId = list.get(0).getRLPData();
            byte[] rlpBlock = list.get(1).getRLPData();

            long id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();
            Block block = new Block(rlpBlock);

            return new BlockResponseMessage(id, block);
        }
    },
    SKELETON_RESPONSE_MESSAGE(13) {
        @Override
        public Message createMessage(RLPList list) {
            byte[] rlpId = list.get(0).getRLPData();
            long id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();

            RLPList paramsList = (RLPList) RLP.decode2(list.get(1).getRLPData()).get(0);
            List<BlockIdentifier> blockIdentifiers = new ArrayList<>();
            for (RLPElement param : paramsList) {
                RLPList rlpData = ((RLPList) param);
                blockIdentifiers.add(new BlockIdentifier(rlpData));
            }

            return new SkeletonResponseMessage(id, blockIdentifiers);
        }
    },
    BODY_REQUEST_MESSAGE(14) {
        @Override
        public Message createMessage(RLPList list) {
            byte[] rlpId = list.get(0).getRLPData();
            long id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();
            byte[] hash = list.get(1).getRLPData();
            return new BodyRequestMessage(id, hash);
        }
    },
    BODY_RESPONSE_MESSAGE(15) {
        @Override
        public Message createMessage(RLPList list) {
            byte[] rlpId = list.get(0).getRLPData();
            long id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();
            RLPList rlpTransactions = (RLPList)RLP.decode2(list.get(1).getRLPData()).get(0);
            RLPList rlpUncles = (RLPList)RLP.decode2(list.get(2).getRLPData()).get(0);

            List<Transaction> transactions = new ArrayList<>();
            List<BlockHeader> uncles = new ArrayList<>();

            for (int k = 0; k < rlpTransactions.size(); k++)
                transactions.add(new Transaction(rlpTransactions.get(k).getRLPData()));

            for (int k = 0; k < rlpUncles.size(); k++)
                uncles.add(new BlockHeader(rlpUncles.get(k).getRLPData()));

            return new BodyResponseMessage(id, transactions, uncles);
        }
    },
    SKELETON_REQUEST_MESSAGE(16) {
        @Override
        public Message createMessage(RLPList list) {
            byte[] hash_start = list.get(0).getRLPData();
            byte[] hash_end = list.get(1).getRLPData();
            return new SkeletonRequestMessage(hash_start, hash_end);
        }
    },
    NEW_BLOCK_HASH_MESSAGE(17) {
        @Override
        public Message createMessage(RLPList list) {
            byte[] hash = list.get(0).getRLPData();
            return new NewBlockHashMessage(hash);
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
