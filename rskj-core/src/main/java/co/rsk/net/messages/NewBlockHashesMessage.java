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
package co.rsk.net.messages;

import org.ethereum.core.BlockIdentifier;
import org.ethereum.net.eth.message.EthMessageCodes;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper around an Ethereum NewBlockHashes message on the network<br>
 *
 * @see EthMessageCodes#NEW_BLOCK_HASHES
 *
 * @author Mikhail Kalinin
 * @since 05.09.2015
 */
public class NewBlockHashesMessage extends Message {

    /**
     * List of identifiers holding hash and number of the blocks
     */
    private List<BlockIdentifier> blockIdentifiers;

    private boolean parsed;
    private byte[] encoded;

    public NewBlockHashesMessage(byte[] payload) {
        this.encoded = payload;
        this.parsed = false;
    }

    public NewBlockHashesMessage(List<BlockIdentifier> blockIdentifiers) {
        this.blockIdentifiers = blockIdentifiers;
        parsed = true;
    }

    private void parse() {
        RLPList paramsList = RLP.decodeList(encoded);

        blockIdentifiers = new ArrayList<>();

        for (int i = 0; i < paramsList.size(); ++i) {
            RLPList rlpData = ((RLPList) paramsList.get(i));
            blockIdentifiers.add(new BlockIdentifier(rlpData));
        }
        parsed = true;
    }

    private void encode() {
        List<byte[]> encodedElements = new ArrayList<>();

        for (BlockIdentifier identifier : blockIdentifiers) {
            encodedElements.add(identifier.getEncoded());
        }

        byte[][] encodedElementArray = encodedElements.toArray(new byte[encodedElements.size()][]);
        this.encoded = RLP.encodeList(encodedElementArray);
    }


    @Override
    public MessageType getMessageType() {
        return MessageType.NEW_BLOCK_HASHES;
    }

    @Override
    public byte[] getEncodedMessage() {
        if (encoded == null) {
            encode();
        }

        return encoded;
    }

    public List<BlockIdentifier> getBlockIdentifiers() {
        if (!parsed) {
            parse();
        }

        return blockIdentifiers;
    }

    @Override
    public void accept(MessageVisitor v) {
        v.apply(this);
    }

    @Override
    public String toString() {
        if (!parsed) {
            parse();
        }

        return "[" + getMessageType() + "] (" + blockIdentifiers.size() + ")";
    }

}
