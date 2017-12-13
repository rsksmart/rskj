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

import org.ethereum.core.BlockHeader;
import org.ethereum.net.eth.message.EthMessageCodes;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Wrapper around an Ethereum BlockHeaders message on the network
 *
 * @see EthMessageCodes#BLOCK_HEADERS
 *
 * @author Mikhail Kalinin
 * @since 04.09.2015
 */
public class BlockHeadersMessage extends Message {

    /**
     * List of block headers from the peer
     */
    private List<BlockHeader> blockHeaders;

    // TODO(mvanotti): We should move these vars to Message.
    private byte[] encoded;
    private boolean parsed;

    public BlockHeadersMessage(byte[] encoded) {
        this.encoded = encoded;
        this.parsed = false;
    }

    public BlockHeadersMessage(@Nonnull final BlockHeader header) {
        blockHeaders = new LinkedList<>();
        blockHeaders.add(header);
        parsed = true;
    }

    public BlockHeadersMessage(List<BlockHeader> headers) {
        this.blockHeaders = headers;
        parsed = true;
    }

    private void parse() {
        RLPList paramsList = (RLPList) RLP.decode2(encoded).get(0);

        blockHeaders = new ArrayList<>();

        for (int i = 0; i < paramsList.size(); ++i) {
            RLPList rlpData = ((RLPList) paramsList.get(i));
            blockHeaders.add(new BlockHeader(rlpData, true));
        }

        parsed = true;
    }

    private void encode() {
        List<byte[]> encodedElements = new ArrayList<>();
        for (BlockHeader blockHeader : blockHeaders) {
            encodedElements.add(blockHeader.getEncoded());
        }

        byte[][] encodedElementArray = encodedElements.toArray(new byte[encodedElements.size()][]);
        this.encoded = RLP.encodeList(encodedElementArray);
    }


    @Override
    public MessageType getMessageType() {
        return MessageType.BLOCK_HEADERS_MESSAGE;
    }

    @Override
    public byte[] getEncodedMessage() {
        if (encoded == null) {
            encode();
        }

        return encoded;
    }

    public List<BlockHeader> getBlockHeaders() {
        if (!parsed) {
            parse();
        }

        return blockHeaders;
    }

    @Override
    public String toString() {
        if (!parsed) {
            parse();
        }

        StringBuilder payload = new StringBuilder();

        payload.append("count( ").append(blockHeaders.size()).append(" )");

        return "[" + getMessageType() + " " + payload + "]";
    }
}
