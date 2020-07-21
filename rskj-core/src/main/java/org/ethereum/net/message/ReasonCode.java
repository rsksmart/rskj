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

package org.ethereum.net.message;

import java.util.HashMap;
import java.util.Map;

/**
 * Reason is an optional integer specifying one
 * of a number of reasons for disconnect
 */
public enum ReasonCode {

    /**
     * [0x00] Disconnect request by other peer
     */
    REQUESTED(0x00),

    /**
     * [0x01]
     */
    TCP_ERROR(0x01),

    /**
     * [0x02] Packets can not be parsed
     */
    BAD_PROTOCOL(0x02),

    /**
     * [0x03] This peer is too slow or delivers unreliable data
     */
    USELESS_PEER(0x03),

    /**
     * [0x04] Already too many connections with other peers
     */
    TOO_MANY_PEERS(0x04),


    /**
     * [0x05] Already have a running connection with this peer
     */
    DUPLICATE_PEER(0x05),

    /**
     * [0x06] Version of the p2p protocol is not the same as ours
     */
    INCOMPATIBLE_PROTOCOL(0x06),

    /**
     * [0x07]
     */
    NULL_IDENTITY(0x07),

    /**
     * [0x08] Peer quit voluntarily
     */
    PEER_QUITING(0x08),

    UNEXPECTED_IDENTITY(0x09),

    LOCAL_IDENTITY(0x0A),

    PING_TIMEOUT(0x0B),

    USER_REASON(0x10),

    UNEXPECTED_GENESIS(0x11),

    /**
     * Light Client Disconnection Codes
     */

    NOT_PENDING_MESSAGE(0x20),

    WRONG_DIFFICULTY(0x21),

    INVALID_POW(0x22),

    WRONG_BLOCK_HEADERS_SIZE(0x23),

    DIFFERENT_FIRSTS_BLOCKS(0X24),

    INCORRECT_SKIPPED_BLOCK(0x25),

    MORE_BLOCKS_THAN_ALLOWED(0x26),

    INCORRECT_PARENT_HASH(0x27),

    FAILED_ATTEMPT(0x28),

    /**
     * [0xFF] Reason not specified
     */
    UNKNOWN(0xFF);


    private int reason;

    private static final Map<Integer, ReasonCode> intToTypeMap = new HashMap<>();

    static {
        for (ReasonCode type : ReasonCode.values()) {
            intToTypeMap.put(type.reason, type);
        }
    }

    private ReasonCode(int reason) {
        this.reason = reason;
    }

    public static ReasonCode fromInt(int i) {
        ReasonCode type = intToTypeMap.get(i);
        if (type == null) {
            return ReasonCode.UNKNOWN;
        }
        return type;
    }

    public byte asByte() {
        return (byte) reason;
    }
}
