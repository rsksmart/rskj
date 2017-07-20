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

package co.rsk.net.discovery;

import co.rsk.net.discovery.table.DistanceCalculator;
import org.ethereum.net.rlpx.Node;

import java.io.Serializable;
import java.util.Comparator;

/**
 * Created by ajlopez on 22/02/17.
 */
public class NodeDistanceScoreComparator implements Comparator<NodeInformation> {
    @Override
    public int compare(NodeInformation n1, NodeInformation n2) {
        int result = Integer.compare(n1.getDistance(), n2.getDistance());

        if (result == 0)
            return -Integer.compare(n1.getScore(), n2.getScore());

        return result;
    }
}
