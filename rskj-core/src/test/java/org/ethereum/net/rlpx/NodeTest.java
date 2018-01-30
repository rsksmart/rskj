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

import org.apache.commons.lang3.StringUtils;
import org.ethereum.crypto.HashUtil;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.ethereum.crypto.HashUtil.keccak256;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by mario on 01/08/2016.
 */
public class NodeTest {

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
    public void nodeSerialization() {

        Node node_1 = new Node(NODE_ID_1, NODE_HOST_1, NODE_PORT_1);
        Node node_2 = new Node(node_1.getRLP());

        byte[] id_2 = node_2.getId();
        String host_2 = node_2.getHost();
        int port_2 = node_2.getPort();

        assertEquals(Hex.toHexString(NODE_ID_1), Hex.toHexString(id_2));
        assertEquals(NODE_HOST_1, host_2);
        assertTrue(NODE_PORT_1 == port_2);
    }

    @Test
    public void getAddress() {
        Node node = new Node(NODE_ID_1, GOOGLE, GOOGLE_PORT);
        Pattern pattern = Pattern.compile(IP_ADDRESS_PATTERN);

        String address = node.getAddress().getAddress().getHostAddress();
        Matcher matcher = pattern.matcher(address);

        Assert.assertTrue(StringUtils.isNotBlank(address));
        Assert.assertTrue(address, matcher.matches());

        node = new Node(NODE_ID_1, NODE_HOST_1, NODE_PORT_1);

        address = node.getAddressAsString();
        Assert.assertTrue(StringUtils.isNotBlank(address));
        Assert.assertTrue(address.startsWith(NODE_HOST_1));

    }

}
