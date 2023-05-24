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

import co.rsk.core.BlockDifficulty;
import co.rsk.net.Status;
import co.rsk.remasc.RemascTransaction;
import org.ethereum.core.*;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;
import org.bouncycastle.util.BigIntegers;

import java.util.ArrayList;
import java.util.List;

import static org.ethereum.util.ByteUtil.byteArrayToInt;

/**
 * Created by mario on 16/02/17.
 */
public enum MessageType {

    STATUS_MESSAGE(1) {
        @Override
        public Message createMessage(BlockFactory blockFactory, RLPList list) {
            byte[] rlpdata = list.get(0).getRLPData();
            long number = rlpdata == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpdata).longValue();
            byte[] hash = list.get(1).getRLPData();

            if (list.size() == 2) {
                return new StatusMessage(new Status(number, hash));
            }

            byte[] parentHash = list.get(2).getRLPData();
            byte[] rlpTotalDifficulty = list.get(3).getRLPData();
            BlockDifficulty totalDifficulty = rlpTotalDifficulty == null ? BlockDifficulty.ZERO : RLP.parseBlockDifficulty(rlpTotalDifficulty);

            return new StatusMessage(new Status(number, hash, parentHash, totalDifficulty));
        }
    },
    BLOCK_MESSAGE(2) {
        @Override
        public Message createMessage(BlockFactory blockFactory, RLPList list) {
            return new BlockMessage(blockFactory.decodeBlock(list.get(0).getRLPData()));
        }
    },
    GET_BLOCK_MESSAGE(3) {
        @Override
        public Message createMessage(BlockFactory blockFactory, RLPList list) {
            return new GetBlockMessage(list.get(0).getRLPData());
        }
    },
    NEW_BLOCK_HASHES(6) {
        @Override
        public Message createMessage(BlockFactory blockFactory, RLPList list) {
            return new NewBlockHashesMessage(list.getRLPData());
        }
    },
    TRANSACTIONS(7) {
        @Override
        public Message createMessage(BlockFactory blockFactory, RLPList list) {
            List<Transaction> txs = new ArrayList<>();

            for (int k = 0; k < list.size(); k++) {
                RLPElement element = list.get(k);
                byte[] data = element.getRLPData();

                if (!MessageType.validTransactionLength(data)) {
                    continue;
                }

                ImmutableTransaction tx = new ImmutableTransaction(data);

                txs.add(tx);
            }

            return new TransactionsMessage(txs);
        }
    },
    BLOCK_HASH_REQUEST_MESSAGE(8) {
        @Override
        public Message createMessage(BlockFactory blockFactory, RLPList list) {
            RLPList message = (RLPList)RLP.decode2(list.get(1).getRLPData()).get(0);
            byte[] rlpId = list.get(0).getRLPData();
            long id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();
            byte[] rlpHeight = message.get(0).getRLPData();
            long height = rlpHeight == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpHeight).longValue();

            return new BlockHashRequestMessage(id, height);
        }
    },
    BLOCK_HASH_RESPONSE_MESSAGE(18) {
        @Override
        public Message createMessage(BlockFactory blockFactory, RLPList list) {
            RLPList message = (RLPList)RLP.decode2(list.get(1).getRLPData()).get(0);
            byte[] rlpId = list.get(0).getRLPData();
            long id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();
            byte[] hash = message.get(0).getRLPData();

            return new BlockHashResponseMessage(id, hash);
        }
    },
    BLOCK_HEADERS_REQUEST_MESSAGE(9) {
        @Override
        public Message createMessage(BlockFactory blockFactory, RLPList list){
            RLPList message = (RLPList)RLP.decode2(list.get(1).getRLPData()).get(0);
            byte[] rlpId = list.get(0).getRLPData();
            byte[] hash = message.get(0).getRLPData();
            byte[] rlpCount = message.get(1).getRLPData();

            long id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();
            int count = byteArrayToInt(rlpCount);

            return new BlockHeadersRequestMessage(id, hash, count);
        }
    },
    BLOCK_HEADERS_RESPONSE_MESSAGE(10) {
        @Override
        public Message createMessage(BlockFactory blockFactory, RLPList list) {
            RLPList message = (RLPList)RLP.decode2(list.get(1).getRLPData()).get(0);
            byte[] rlpId = list.get(0).getRLPData();
            RLPList rlpHeaders = (RLPList)RLP.decode2(message.get(0).getRLPData()).get(0);
            long id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();

            List<BlockHeader> headers = new ArrayList<>();

            for (int k = 0; k < rlpHeaders.size(); k++) {
                RLPElement element = rlpHeaders.get(k);
                BlockHeader header = blockFactory.decodeHeader(element.getRLPData());
                headers.add(header);
            }

            return new BlockHeadersResponseMessage(id, headers);
        }
    },
    BLOCK_REQUEST_MESSAGE(11) {
        @Override
        public Message createMessage(BlockFactory blockFactory, RLPList list) {
            RLPList message = (RLPList)RLP.decode2(list.get(1).getRLPData()).get(0);
            byte[] rlpId = list.get(0).getRLPData();
            long id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();
            byte[] hash = message.get(0).getRLPData();
            return new BlockRequestMessage(id, hash);
        }
    },
    BLOCK_RESPONSE_MESSAGE(12) {
        @Override
        public Message createMessage(BlockFactory blockFactory, RLPList list) {
            RLPList message = (RLPList)RLP.decode2(list.get(1).getRLPData()).get(0);
            byte[] rlpId = list.get(0).getRLPData();
            byte[] rlpBlock = message.get(0).getRLPData();

            long id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();
            Block block = blockFactory.decodeBlock(rlpBlock);

            return new BlockResponseMessage(id, block);
        }
    },
    SKELETON_RESPONSE_MESSAGE(13) {
        @Override
        public Message createMessage(BlockFactory blockFactory, RLPList list) {
            RLPList message = (RLPList)RLP.decode2(list.get(1).getRLPData()).get(0);
            byte[] rlpId = list.get(0).getRLPData();
            long id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();

            RLPList paramsList = (RLPList)RLP.decode2(message.get(0).getRLPData()).get(0);

            List<BlockIdentifier> blockIdentifiers = new ArrayList<>();

            for (int k = 0; k < paramsList.size(); k++) {
                RLPElement param = paramsList.get(k);
                BlockIdentifier blockIdentifier = new BlockIdentifier((RLPList)param);
                blockIdentifiers.add(blockIdentifier);
            }

            return new SkeletonResponseMessage(id, blockIdentifiers);
        }
    },
    BODY_REQUEST_MESSAGE(14) {
        @Override
        public Message createMessage(BlockFactory blockFactory, RLPList list) {
            RLPList message = (RLPList)RLP.decode2(list.get(1).getRLPData()).get(0);
            byte[] rlpId = list.get(0).getRLPData();
            byte[] hash = message.get(0).getRLPData();

            long id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();
            return new BodyRequestMessage(id, hash);
        }
    },
    BODY_RESPONSE_MESSAGE(15) {
        @Override
        public Message createMessage(BlockFactory blockFactory, RLPList list) {
            RLPList message = (RLPList) RLP.decode2(list.get(1).getRLPData()).get(0);
            byte[] rlpId = list.get(0).getRLPData();
            long id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();
            RLPList rlpTransactions = (RLPList) RLP.decode2(message.get(0).getRLPData()).get(0);
            RLPList rlpUncles = (RLPList) RLP.decode2(message.get(1).getRLPData()).get(0);

            List<Transaction> transactions = new ArrayList<>();
            for (int k = 0; k < rlpTransactions.size(); k++) {
                byte[] txdata = rlpTransactions.get(k).getRLPData();
                Transaction tx = new ImmutableTransaction(txdata);

                if (tx.isRemascTransaction(k, rlpTransactions.size())) {
                    tx = new RemascTransaction(txdata);
                }

                transactions.add(tx);
            }

            List<BlockHeader> uncles = new ArrayList<>();

            for (int j = 0; j < rlpUncles.size(); j++) {
                RLPElement element = rlpUncles.get(j);
                uncles.add(blockFactory.decodeHeader(element.getRLPData()));
            }

            return new BodyResponseMessage(id, transactions, uncles);
        }
    },
    SKELETON_REQUEST_MESSAGE(16) {
        @Override
        public Message createMessage(BlockFactory blockFactory, RLPList list) {
            RLPList message = (RLPList)RLP.decode2(list.get(1).getRLPData()).get(0);
            byte[] rlpId = list.get(0).getRLPData();
            long id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();
            byte[] rlpStartNumber = message.get(0).getRLPData();
            long startNumber = rlpStartNumber == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpStartNumber).longValue();
            return new SkeletonRequestMessage(id, startNumber);
        }
    },
    NEW_BLOCK_HASH_MESSAGE(17) {
        @Override
        public Message createMessage(BlockFactory blockFactory, RLPList list) {
            byte[] hash = list.get(0).getRLPData();
            return new NewBlockHashMessage(hash);
        }
    },
    STATE_CHUNK_REQUEST_MESSAGE(20) {
        @Override
        public Message createMessage(BlockFactory blockFactory, RLPList list) {
            RLPList message = (RLPList)RLP.decode2(list.get(1).getRLPData()).get(0);
            byte[] rlpId = list.get(0).getRLPData();
            byte[] hash = message.get(0).getRLPData();
            long id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();
            return new StateChunkRequestMessage(id);
        }
    };

    private int type;

    MessageType(int type) {
        this.type = type;
    }

    public abstract Message createMessage(BlockFactory blockFactory, RLPList list);

    public byte getTypeAsByte() {
        return (byte) this.type;
    }

    public static MessageType valueOfType(int type) {
        for(MessageType mt : MessageType.values()) {
            if(mt.type == type) {
                return mt;
            }
        }
        throw new IllegalArgumentException(String.format("Invalid Message Type: %d", type));
    }

    private static boolean validTransactionLength(byte[] data) {
        return data.length <= 1 << 19;  /* 512KB */
    }
}
