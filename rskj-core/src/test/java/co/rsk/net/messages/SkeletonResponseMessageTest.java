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
import org.ethereum.util.ByteUtil;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static org.bouncycastle.util.encoders.Hex.decode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class SkeletonResponseMessageTest {

    @Test
    public void createMessage() {

        long someId = 42;
        List<BlockIdentifier> identifiers = Arrays.asList(
            new BlockIdentifier(decode("4ee6424d776b3f59affc20bc2de59e67f36e22cc07897ff8df152242c921716b"), 1),
            new BlockIdentifier(decode("7d2fe4df0dbbc9011da2b3bf177f0c6b7e71a11c509035c5d751efa5cf9b4817"), 2)
        );

        SkeletonResponseMessage skeletonMessage = new SkeletonResponseMessage(someId, identifiers);

        String expected = "f8500db84df84b2af848f846e2a04ee6424d776b3f59affc20bc2de59e67f36e22cc07897ff8df152242c921716b01e2a07d2fe4df0dbbc9011da2b3bf177f0c6b7e71a11c509035c5d751efa5cf9b481702";
        assertEquals(expected, ByteUtil.toHexString(skeletonMessage.getEncoded()));

        assertEquals(MessageType.SKELETON_RESPONSE_MESSAGE, skeletonMessage.getMessageType());
        assertEquals(42, skeletonMessage.getId());
        assertEquals(2, skeletonMessage.getBlockIdentifiers().size());
    }

    @Test
    public void accept() {
        List<BlockIdentifier> blockIdentifiers = new LinkedList<>();
        SkeletonResponseMessage message = new SkeletonResponseMessage(1, blockIdentifiers);

        MessageVisitor visitor = mock(MessageVisitor.class);

        message.accept(visitor);

        verify(visitor, times(1)).apply(message);
    }
}
