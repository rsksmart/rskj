/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.net.discovery.table;

import org.ethereum.net.rlpx.Node;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.bouncycastle.util.encoders.Hex;

/**
 * Created by mario on 21/02/17.
 */
public class DistanceCalculatorTest {

    private static final String NODE_ID_1 = "826fbe97bc03c7c09d7b7d05b871282d8ac93d4446d44b55566333b240dd06260a9505f0fd3247e63d84d557f79bb63691710e40d4d9fc39f3bfd5397bcea065";
private static final String NODE_ID_2 = "01";


    @Test
    public void distance() {
        DistanceCalculator calculator = new DistanceCalculator(256);

        Node node1 = new Node(Hex.decode(NODE_ID_1), "190.0.0.128", 8080);
        Node node2 = new Node(Hex.decode(NODE_ID_2), "192.0.0.127", 8080);

        Assertions.assertEquals(0, calculator.calculateDistance(node1.getId(), node1.getId()));

        Assertions.assertEquals(calculator.calculateDistance(node1.getId(), node2.getId()), calculator.calculateDistance(node2.getId(), node1.getId()));
    }
}
