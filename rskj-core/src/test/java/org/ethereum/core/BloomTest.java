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

package org.ethereum.core;

import org.ethereum.crypto.HashUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.bouncycastle.util.encoders.Hex;

/**
 * @author Roman Mandeleil
 * @since 20.11.2014
 */
class BloomTest {


    @Test /// based on http://bit.ly/1MtXxFg
    void test1(){

        byte[] address = Hex.decode("095e7baea6a6c7c4c2dfeb977efac326af552d87");
        Bloom addressBloom = Bloom.create(HashUtil.keccak256(address));

        byte[] topic = Hex.decode("0000000000000000000000000000000000000000000000000000000000000000");
        Bloom topicBloom = Bloom.create(HashUtil.keccak256(topic));

        Bloom totalBloom = new Bloom();
        totalBloom.or(addressBloom);
        totalBloom.or(topicBloom);


        Assertions.assertEquals(
                "00000000000000001000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000020000000000000000000800000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000020000000000040000000000000000000000000000000000000000000000000000000",
                totalBloom.toString()
        );
    }

    // todo: more testing

}
