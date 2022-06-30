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

import org.junit.Assert;
import org.junit.Test;
import org.bouncycastle.util.encoders.DecoderException;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;

/**
 * Created by ajlopez on 18/01/2018.
 */
public class TopicTest {
    @Test
    public void testEquals() {
        Topic topicA = new Topic("0000000000000000000000000000000000000000000000000000000000000001");
        Topic topicB = new Topic("0000000000000000000000000000000000000000000000000000000000000001");
        Topic topicC = new Topic("0000000000000000000000000000000000000000000000000000000000000002");
        Topic topicD = new Topic("0x0000000000000000000000000000000000000000000000000000000000000003");

        Assert.assertEquals(topicA, topicB);
        Assert.assertNotEquals(topicA, topicC);
        Assert.assertNotEquals(topicA, topicD);
    }

    @Test(expected = RuntimeException.class)
    public void invalidLongTopic() {
        new Topic("000000000000000000000000000000000000000000000000000000000000000001");
    }

    @Test(expected = RuntimeException.class)
    public void invalidShortTopic() {
        new Topic("0000000000000000000000000000000001006");
    }

    @Test
    public void oddLengthAddressPaddedWithOneZero() {
        Topic topicA = new Topic("000000000000000000000000000000000000000000000000000000000000001");
        Topic topicB = new Topic("0000000000000000000000000000000000000000000000000000000000000001");

        Assert.assertEquals(topicA, topicB);
    }

    @Test(expected = DecoderException.class)
    public void invalidHexTopic() {
        new Topic("00000000000000000000000000000000000000000000000000000000000000X");
    }

    @Test(expected = NullPointerException.class)
    public void invalidNullTopicBytes() {
        new Topic((byte[]) null);
    }

    @Test(expected = NullPointerException.class)
    public void invalidNullTopicString() {
        new Topic((String) null);
    }

    @Test(expected = RuntimeException.class)
    public void invalidShortTopicBytes() {
        new Topic(new byte[31]);
    }

    @Test(expected = RuntimeException.class)
    public void invalidLongAddressBytes() {
        new Topic(new byte[33]);
    }
}
