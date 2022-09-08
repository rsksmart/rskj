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

import co.rsk.blockchain.utils.BlockGenerator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class NewBlockHashTest {
    @Test
    public void createWithBlockHash() {
        byte[] hash = new BlockGenerator().getGenesisBlock().getHash().getBytes();
        NewBlockHashMessage message = new NewBlockHashMessage(hash);

        Assertions.assertArrayEquals(hash, message.getBlockHash());
        Assertions.assertEquals(MessageType.NEW_BLOCK_HASH_MESSAGE, message.getMessageType());
    }
}
