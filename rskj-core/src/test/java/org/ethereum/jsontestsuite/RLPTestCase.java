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

package org.ethereum.jsontestsuite;


import org.bouncycastle.util.encoders.Hex;
import org.ethereum.util.*;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class RLPTestCase {
    private Object in;
    private String out;

    private List<String> computed = new ArrayList<>();
    private List<String> expected = new ArrayList<>();

    public Object getIn() {
        return in;
    }

    public void setIn(Object in) {
        this.in = in;
    }

    public String getOut() {
        return out;
    }

    public void setOut(String out) {
        this.out = out;
    }

    public List<String> getComputed() {
        return computed;
    }

    public List<String> getExpected() {
        return expected;
    }

    public void doEncode() {
        byte[] in = buildRLP(this.in);
        String expected = this.out.toLowerCase();
        String computed = ByteUtil.toHexString(in);
        this.computed.add(computed);
        this.expected.add(expected);
    }

    public void doDecode() {
        String out = this.out.toLowerCase();
        ArrayList<RLPElement> list = RLP.decode2(Hex.decode(out));
        checkRLPAgainstJson(list.get(0), in);
    }

    public byte[] buildRLP(Object in) {
        if (in instanceof ArrayList) {
            List<byte[]> elementList = new Vector<>();
            for (Object o : ((ArrayList) in).toArray()) {
                elementList.add(buildRLP(o));
            }
            byte[][] elements = elementList.toArray(new byte[elementList.size()][]);
            return RLP.encodeList(elements);
        } else {
            if (in instanceof String) {
                String s = in.toString();
                if (s.contains("#")) {
                    return RLP.encodeBigInteger(new BigInteger(s.substring(1)));
                }
            } else if (in instanceof Integer) {
                return RLP.encodeInt(Integer.parseInt(in.toString()));
            }
            return RLPTestUtil.encode(in);
        }
    }

    public void checkRLPAgainstJson(RLPElement element, Object in) {
        if (in instanceof List) {
            Object[] array = ((List) in).toArray();
            RLPList list = (RLPList) element;
            for (int i = 0; i < array.length; i++) {
                checkRLPAgainstJson(list.get(i), array[i]);
            }
        } else if (in instanceof Number) {
            int computed = ByteUtil.byteArrayToInt(element.getRLPData());
            this.computed.add(Integer.toString(computed));
            this.expected.add(in.toString());
        } else if (in instanceof String) {
            String s = in.toString();
            if (s.contains("#")) {
                s = s.substring(1);
                BigInteger expected = new BigInteger(s);
                byte[] payload = element.getRLPData();
                BigInteger computed = new BigInteger(1, payload);
                this.computed.add(computed.toString());
                this.expected.add(expected.toString());
            } else {
                String expected = new String(element.getRLPData() != null ? element.getRLPData() :
                        new byte[0], StandardCharsets.UTF_8);
                this.expected.add(expected);
                this.computed.add(s);
            }
        } else {
            throw new RuntimeException("Unexpected type: " + in.getClass());
        }
    }
}
