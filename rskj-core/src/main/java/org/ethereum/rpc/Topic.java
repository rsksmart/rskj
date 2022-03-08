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

import java.util.Arrays;

import org.ethereum.util.ByteUtil;

import co.rsk.util.HexUtils;

/**
 * Created by ajlopez on 18/01/2018.
 */
public final class Topic {
    /**
     * This is the size of a topic in bytes.
     */
    private static final int LENGTH_IN_BYTES = 32;

    private final byte[] bytes;

    /**
     * @param topic the hex-encoded 32 bytes long topic, with or without 0x prefix.
     */
    public Topic(String topic) {
        this(HexUtils.stringHexToByteArray(topic));
    }

    /**
     * @param bytes the 32 bytes long raw topic bytes.
     */
    public Topic(byte[] bytes) {
        if (bytes.length != LENGTH_IN_BYTES) {
            throw new RuntimeException(String.format("A topic must be %d bytes long", LENGTH_IN_BYTES));
        }

        this.bytes = bytes;
    }

    public byte[] getBytes() {
        return bytes;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other == null || this.getClass() != other.getClass()) {
            return false;
        }

        Topic otherTopic = (Topic) other;

        return Arrays.equals(bytes, otherTopic.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return ByteUtil.toHexString(bytes);
    }

    public String toJsonString() {
        return HexUtils.toUnformattedJsonHex(this.getBytes());
    }
}
