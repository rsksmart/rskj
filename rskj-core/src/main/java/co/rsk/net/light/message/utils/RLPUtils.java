/*
 * This file is part of RskJ
 * Copyright (C) 2020 RSK Labs Ltd.
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

package co.rsk.net.light.message.utils;

import org.ethereum.util.RLPList;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

public class RLPUtils {

    private RLPUtils() { }

    public static <T> List<T> mapInputRlp(RLPList rlpElements, Function<byte[], T> lambda) {
        List<T> elements = new LinkedList<>();
        for (int k = 0; k < rlpElements.size(); k++) {
            byte[] rpData = rlpElements.get(k).getRLPData();
            elements.add(lambda.apply(rpData));
        }
        return elements;
    }
}