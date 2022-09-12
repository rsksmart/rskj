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

package org.ethereum.net;

import org.bouncycastle.util.encoders.Hex;
import org.ethereum.net.message.ReasonCode;
import org.ethereum.net.p2p.DisconnectMessage;
import org.ethereum.util.ByteUtil;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisconnectMessageTest {

    private static final Logger logger = LoggerFactory.getLogger("test");

    /* DISCONNECT_MESSAGE */

    @Test /* DisconnectMessage 1 - Requested */
    void test_1() {

        byte[] payload = Hex.decode("C100");
        DisconnectMessage disconnectMessage = new DisconnectMessage(payload);

        logger.trace("{}" + disconnectMessage);
        assertEquals(ReasonCode.REQUESTED, disconnectMessage.getReason());
    }

    @Test /* DisconnectMessage 2 - TCP Error */
    void test_2() {

        byte[] payload = Hex.decode("C101");
        DisconnectMessage disconnectMessage = new DisconnectMessage(payload);

        logger.trace("{}" + disconnectMessage);
        assertEquals(ReasonCode.TCP_ERROR, disconnectMessage.getReason());
    }

    @Test /* DisconnectMessage 2 - from constructor */
    void test_3() {

        DisconnectMessage disconnectMessage = new DisconnectMessage(ReasonCode.NULL_IDENTITY);

        logger.trace("{}" + disconnectMessage);

        String expected = "c107";
        assertEquals(expected, ByteUtil.toHexString(disconnectMessage.getEncoded()));

        assertEquals(ReasonCode.NULL_IDENTITY, disconnectMessage.getReason());
    }

    @Test //handling boundary-high
    void test_4() {

        byte[] payload = Hex.decode("C180");

        DisconnectMessage disconnectMessage = new DisconnectMessage(payload);
        logger.trace("{}" + disconnectMessage);

        assertEquals(ReasonCode.REQUESTED, disconnectMessage.getReason()); //high numbers are zeroed
    }

    @Test //handling boundary-low minus 1 (error)
    void test_6() {

        String disconnectMessageRaw = "C19999";
        byte[] payload = Hex.decode(disconnectMessageRaw);

        try {
            DisconnectMessage disconnectMessage = new DisconnectMessage(payload);
            disconnectMessage.toString(); //throws exception
            assertTrue(false, "Valid raw encoding for disconnectMessage");
        } catch (RuntimeException e) {
            assertTrue(true, "Invalid raw encoding for disconnectMessage");
        }
    }

    @Test //handling boundary-high plus 1 (error)
    void test_7() {

        String disconnectMessageRaw = "C28081";
        byte[] payload = Hex.decode(disconnectMessageRaw);

        try {
            DisconnectMessage disconnectMessage = new DisconnectMessage(payload);
            disconnectMessage.toString(); //throws exception
            assertTrue(false, "Valid raw encoding for disconnectMessage");
        } catch (RuntimeException e) {
            assertTrue(true, "Invalid raw encoding for disconnectMessage");
        }
    }
}

