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
import org.ethereum.crypto.HashUtil;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.spongycastle.util.encoders.Hex.decode;
import static org.spongycastle.util.encoders.Hex.toHexString;

public class GetSkeletonMessageTest {

    @Test
    public void createMessage() {
        byte[] hash_start = HashUtil.randomHash();
        byte[] hash_end = HashUtil.randomHash();
        GetSkeletonMessage message = new GetSkeletonMessage(hash_start, hash_end);

        assertEquals(MessageType.GET_SKELETON_MESSAGE, message.getMessageType());
        assertArrayEquals(hash_start, message.getHashStart());
        assertArrayEquals(hash_end, message.getHashEnd());
    }
}
