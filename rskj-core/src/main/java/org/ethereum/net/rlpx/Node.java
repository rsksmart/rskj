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

import co.rsk.net.NodeID;
import co.rsk.util.MaxSizeHashMap;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

import static org.ethereum.util.ByteUtil.byteArrayToInt;

public class Node implements Serializable {
    private static final long serialVersionUID = -4267600517925770636L;

    private static final MaxSizeHashMap<String, InetSocketAddress> addressCache = new MaxSizeHashMap<>(40, true); // ~ max active peers + margin

    private final byte[] id;
    private final String host;
    private final int port;

    public Node(String enodeURL) {
        try {
            URI uri = new URI(enodeURL);
            if (!uri.getScheme().equals("enode")) {
                throw new RuntimeException("expecting URL in the format enode://PUBKEY@HOST:PORT");
            }
            this.id = Hex.decode(uri.getUserInfo());
            this.host = uri.getHost();
            this.port = uri.getPort();
        } catch (URISyntaxException e) {
            throw new RuntimeException("expecting URL in the format enode://PUBKEY@HOST:PORT", e);
        }
    }

    public Node(byte[] id, String host, int port) {
        this.id = id;
        this.host = host;
        this.port = port;
    }

    public Node(byte[] rlp) {
        RLPList nodeRLP = (RLPList)RLP.decode2(rlp).get(0);

        byte[] hostB = nodeRLP.get(0).getRLPData();
        byte[] portB = nodeRLP.get(1).getRLPData();
        byte[] idB;

        //Check getRLP()
        if (nodeRLP.size() > 3) {
            idB = nodeRLP.get(3).getRLPData();
        } else {
            idB = nodeRLP.get(2).getRLPData();
        }

        String host = new String(hostB, Charset.forName("UTF-8"));
        int port = byteArrayToInt(portB);

        this.id = idB;
        this.host = host;
        this.port = port;
    }


    public NodeID getId() {
        return new NodeID(id);
    }

    public String getHexId() {
        return ByteUtil.toHexString(id);
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public byte[] getRLP() {
        byte[] rlphost = RLP.encodeElement(host.getBytes(StandardCharsets.UTF_8));
        byte[] rlpTCPPort = RLP.encodeInt(port);
        byte[] rlpUDPPort = RLP.encodeInt(port);
        byte[] rlpId = RLP.encodeElement(id);

        return RLP.encodeList(rlphost, rlpUDPPort, rlpTCPPort, rlpId);
    }

    public InetSocketAddress getAddress() {
        InetSocketAddress address = addressCache.get(this.getAddressCacheId());
        if (address != null) {
            return address;
        }

        address = new InetSocketAddress(this.getHost(), this.getPort());
        addressCache.put(this.getAddressCacheId(), address);
        return address;
    }

    public String getAddressAsString() {
        InetSocketAddress address = this.getAddress();
        InetAddress addr = address.getAddress();
        // addr == null if the hostname can't be resolved
        return (addr == null ? address.getHostString() : addr.getHostAddress()) + ":" + address.getPort();
    }

    @Override
    public String toString() {
        return "Node{" +
                " host='" + host + '\'' +
                ", port=" + port +
                ", id=" + getHexId() +
                '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port, Arrays.hashCode(id));
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }

        if (o == this) {
            return true;
        }

        if (!(o instanceof Node)) {
            return false;
        }

        // TODO(mc): do we need to check host and port too?
        return Arrays.equals(id, ((Node) o).id);
    }

    private String getAddressCacheId() {
        // we cannot use NodeId as cache id, currently we allow multiple nodeId connections per host
        return this.host + ":" + this.port;
    }
}
