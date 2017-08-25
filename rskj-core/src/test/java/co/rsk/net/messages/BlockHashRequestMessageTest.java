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

import org.junit.Assert;
import org.junit.Test;

public class BlockHashRequestMessageTest {
    @Test
    public void createMessage() {
        long someId = 42;
        long someHeight = 99;
        BlockHashRequestMessage message = new BlockHashRequestMessage(someId, someHeight);

        Assert.assertEquals(someId, message.getId());
        Assert.assertEquals(someHeight, message.getHeight());
        Assert.assertEquals(MessageType.BLOCK_HASH_REQUEST_MESSAGE, message.getMessageType());
    }
}
