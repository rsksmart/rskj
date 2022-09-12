/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

package org.ethereum.rpc;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.bouncycastle.util.encoders.DecoderException;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;

/**
 * Created by ajlopez on 18/01/2018.
 */
class TopicTest {
    @Test
    void testEquals() {
        Topic topicA = new Topic("0000000000000000000000000000000000000000000000000000000000000001");
        Topic topicB = new Topic("0000000000000000000000000000000000000000000000000000000000000001");
        Topic topicC = new Topic("0000000000000000000000000000000000000000000000000000000000000002");
        Topic topicD = new Topic("0x0000000000000000000000000000000000000000000000000000000000000003");

        Assertions.assertEquals(topicA, topicB);
        Assertions.assertNotEquals(topicA, topicC);
        Assertions.assertNotEquals(topicA, topicD);
    }

    @Test
    void invalidLongTopic() {
        Assertions.assertThrows(RuntimeException.class, () -> new Topic("000000000000000000000000000000000000000000000000000000000000000001"));
    }

    @Test
    void invalidShortTopic() {
        Assertions.assertThrows(RuntimeException.class, () -> new Topic("0000000000000000000000000000000001006"));
    }

    @Test
    void oddLengthAddressPaddedWithOneZero() {
        Topic topicA = new Topic("000000000000000000000000000000000000000000000000000000000000001");
        Topic topicB = new Topic("0000000000000000000000000000000000000000000000000000000000000001");

        Assertions.assertEquals(topicA, topicB);
    }

    @Test
    void invalidHexTopic() {
        Assertions.assertThrows(DecoderException.class, () -> new Topic("00000000000000000000000000000000000000000000000000000000000000X"));
    }

    @Test
    void invalidNullTopicBytes() {
        Assertions.assertThrows(NullPointerException.class, () -> new Topic((byte[]) null));
    }

    @Test
    void invalidNullTopicString() {
        Assertions.assertThrows(NullPointerException.class, () -> new Topic((String) null));
    }

    @Test
    void invalidShortTopicBytes() {
        Assertions.assertThrows(RuntimeException.class, () -> new Topic(new byte[31]));
    }

    @Test
    void invalidLongAddressBytes() {
        Assertions.assertThrows(RuntimeException.class, () -> new Topic(new byte[33]));
    }
}
