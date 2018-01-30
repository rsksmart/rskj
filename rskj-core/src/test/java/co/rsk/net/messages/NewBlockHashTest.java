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
import co.rsk.core.commons.Keccak256;
import org.junit.Assert;
import org.junit.Test;

public class NewBlockHashTest {
    @Test
    public void createWithBlockHash() {
        Keccak256 hash = BlockGenerator.getInstance().getGenesisBlock().getHash();
        NewBlockHashMessage message = new NewBlockHashMessage(hash);

        Assert.assertEquals(hash, message.getBlockHash());
        Assert.assertEquals(MessageType.NEW_BLOCK_HASH_MESSAGE, message.getMessageType());
    }
}
