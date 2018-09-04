/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package org.ethereum.net.eth.message;

import org.ethereum.core.Block;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.bouncycastle.util.encoders.Hex;

/**
 * Wrapper around an Ethereum Blocks message on the network
 *
 * @see EthMessageCodes#NEW_BLOCK
 */
public class NewBlockMessage extends EthMessage {

    private Block block;
    private byte[] difficulty;

    public NewBlockMessage(byte[] encoded) {
        super(encoded);
    }

    private void parse() {
        RLPList paramsList = (RLPList) RLP.decode2(encoded).get(0);

        RLPList blockRLP = ((RLPList) paramsList.get(0));
        block = new Block(blockRLP.getRLPData());
        difficulty = paramsList.get(1).getRLPData();

        parsed = true;
    }

    private Block getBlock() {
        if (!parsed) {
            parse();
        }
        return block;
    }

    @Override
    public byte[] getEncoded() {
        return encoded;
    }

    @Override
    public EthMessageCodes getCommand() {
        return EthMessageCodes.NEW_BLOCK;
    }

    @Override
    public Class<?> getAnswerMessage() {
        return null;
    }

    public String toString() {
        if (!parsed) {
            parse();
        }

        String hash = this.getBlock().getShortHash();
        long number = this.getBlock().getNumber();
        return "NEW_BLOCK [ number: " + number + " hash:" + hash + " difficulty: " + Hex.toHexString(difficulty) + " ]";
    }
}