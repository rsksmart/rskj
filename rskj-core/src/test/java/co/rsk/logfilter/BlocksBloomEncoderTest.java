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

package co.rsk.logfilter;

import org.ethereum.core.Bloom;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Random;

/**
 * Created by ajlopez on 19/02/2020.
 */
class BlocksBloomEncoderTest {
    @Test
    void encodeDecodeEmptyBlocksBloom() {
        BlocksBloom blocksBloom = new BlocksBloom();

        byte[] data = BlocksBloomEncoder.encode(blocksBloom);

        Assertions.assertNotNull(data);

        BlocksBloom result = BlocksBloomEncoder.decode(data);

        Assertions.assertEquals(0, result.size());

        byte[] bytes = new byte[Bloom.BLOOM_BYTES];

        Assertions.assertArrayEquals(bytes, result.getBloom().getData());
    }

    @Test
    void encodeDecodeBlocksBloom() {
        BlocksBloom blocksBloom = new BlocksBloom(0, 100, new Bloom());

        byte[] data = BlocksBloomEncoder.encode(blocksBloom);

        Assertions.assertNotNull(data);

        BlocksBloom result = BlocksBloomEncoder.decode(data);

        Assertions.assertEquals(blocksBloom.fromBlock(), result.fromBlock());
        Assertions.assertEquals(blocksBloom.toBlock(), result.toBlock());
        Assertions.assertEquals(blocksBloom.size(), result.size());
        Assertions.assertArrayEquals(blocksBloom.getBloom().getData(), result.getBloom().getData());
    }

    @Test
    void encodeDecodeBlocksBloomWithData() {
        byte[] bloomData = new byte[Bloom.BLOOM_BYTES];
        (new Random()).nextBytes(bloomData);
        BlocksBloom blocksBloom = new BlocksBloom(100, 2000, new Bloom(bloomData));

        byte[] data = BlocksBloomEncoder.encode(blocksBloom);

        Assertions.assertNotNull(data);

        BlocksBloom result = BlocksBloomEncoder.decode(data);

        Assertions.assertEquals(blocksBloom.fromBlock(), result.fromBlock());
        Assertions.assertEquals(blocksBloom.toBlock(), result.toBlock());
        Assertions.assertEquals(blocksBloom.size(), result.size());
        Assertions.assertArrayEquals(blocksBloom.getBloom().getData(), result.getBloom().getData());
    }
}
