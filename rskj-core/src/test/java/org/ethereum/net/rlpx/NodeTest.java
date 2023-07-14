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

package org.ethereum.net.rlpx;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Created by mario on 01/08/2016.
 */
class NodeTest {

    private Logger logger = LoggerFactory.getLogger(NodeTest.class);

    private static final byte[] NODE_ID_1 = HashUtil.keccak256("+++".getBytes(Charset.forName("UTF-8")));

    private static final String NODE_HOST_1 = "85.65.19.231";
    private static final String GOOGLE = "www.google.com";

    private static final Integer NODE_PORT_1 = 30303;
    private static final Integer GOOGLE_PORT = 80;

    private static final String IP_ADDRESS_PATTERN = "^([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\."
                                                    + "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\."
                                                    + "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\."
                                                    + "([01]?\\d\\d?|2[0-4]\\d|25[0-5])$";


    @Test
    void nodeSerialization() {

        Node node_1 = new Node(NODE_ID_1, NODE_HOST_1, NODE_PORT_1);
        Node node_2 = new Node(node_1.getRLP());

        byte[] id_2 = node_2.getId().getID();
        String host_2 = node_2.getHost();
        int port_2 = node_2.getPort();

        assertEquals(ByteUtil.toHexString(NODE_ID_1), ByteUtil.toHexString(id_2));
        assertEquals(NODE_HOST_1, host_2);
        assertEquals((int) NODE_PORT_1, port_2);
    }

    @Test
    void getAddress() {
        Node node = new Node(NODE_ID_1, GOOGLE, GOOGLE_PORT);
        Pattern pattern = Pattern.compile(IP_ADDRESS_PATTERN);

        String address = node.getAddress().getAddress().getHostAddress();
        Matcher matcher = pattern.matcher(address);

        Assertions.assertTrue(StringUtils.isNotBlank(address));
        Assertions.assertTrue(matcher.matches(), address);

        node = new Node(NODE_ID_1, NODE_HOST_1, NODE_PORT_1);

        address = node.getAddressAsString();
        Assertions.assertTrue(StringUtils.isNotBlank(address));
        Assertions.assertTrue(address.startsWith(NODE_HOST_1));

    }

    @Test
    void nodeCreationWithUrlIsParsedCorrectly() {
        String host = "host";
        int port = 1234;
        String hexNodeId = String.valueOf(Hex.encodeHex(NODE_ID_1));
        String enodeUrl = "enode://" + hexNodeId + "@" + host + ":" + port;
        Node node = new Node(enodeUrl);
        assertNotNull(node);

        assertEquals(port, node.getPort());
        assertEquals(host, node.getHost());
        assertEquals(hexNodeId, node.getHexId());
    }

    @Test
    void nodeCreationWithBadSchemeMustFail() {
        assertThrows(RuntimeException.class, () -> new Node("http://www.google.es"));
    }

    @Test
    void nodeCreationWithBadUriMustThrowCorrectException() {
        assertThrows(RuntimeException.class, () -> new Node("invalidUri"));
    }

    @Test
    void nodeReturnsExpectedStringValue() {
        Node node = new Node(NODE_ID_1, GOOGLE, GOOGLE_PORT);
        String expectedString = "Node{ host='" + GOOGLE
                + "', port=" + GOOGLE_PORT
                + ", id=" + Hex.encodeHexString(NODE_ID_1)
                + "}";

        assertEquals(expectedString, node.toString());
    }

    @Test
    void equalsMethodWorksAsExpected() {
        Node node1 = new Node(NODE_ID_1, NODE_HOST_1, NODE_PORT_1);
        Node node2 = new Node(NODE_ID_1, NODE_HOST_1, NODE_PORT_1);

        assertEquals(node1, node2);
        assertNotEquals(node1, new Object());
        assertNotEquals(node1, null);
    }

    @Test
    void hashCodeIsUsingHostPortAndIdToBeCalculated() {
        int expectedHash = Objects.hash(NODE_HOST_1, NODE_PORT_1, Arrays.hashCode(NODE_ID_1));
        Node node = new Node(NODE_ID_1, NODE_HOST_1, NODE_PORT_1);
        assertEquals(expectedHash, node.hashCode());
    }
}
