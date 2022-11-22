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

import co.rsk.net.eth.RskMessage;
import co.rsk.net.messages.MessageVersionValidator;
import org.ethereum.core.BlockFactory;
import org.ethereum.net.message.Message;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import static org.ethereum.net.eth.EthVersion.V62;

/**
 * @author Mikhail Kalinin
 * @since 04.09.2015
 */
public class Eth62MessageFactory {

    private final BlockFactory blockFactory;

    private final MessageVersionValidator messageVersionValidator;

    public Eth62MessageFactory(BlockFactory blockFactory, MessageVersionValidator messageVersionValidator) {
        this.blockFactory = blockFactory;
        this.messageVersionValidator = messageVersionValidator;
    }

    public Message create(byte code, byte[] encoded) {

        EthMessageCodes receivedCommand = EthMessageCodes.fromByte(code, V62);
        switch (receivedCommand) {
            case STATUS:
                return new StatusMessage(encoded);
            case RSK_MESSAGE:
                RLPList paramsList = (RLPList) RLP.decode2(encoded).get(0);
                return new RskMessage(co.rsk.net.messages.Message.create(blockFactory, (RLPList) paramsList.get(0), messageVersionValidator.isVersioningEnabled()));
            default:
                throw new IllegalArgumentException("No such message");
        }
    }
}
