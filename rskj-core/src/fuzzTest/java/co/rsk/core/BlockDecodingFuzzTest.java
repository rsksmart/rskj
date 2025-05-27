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

package co.rsk.core;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.BlockFactory;

import org.junit.jupiter.api.Tag;

/**
 * Created by SDL on 12/5/2017.
 */
class BlockDecodingFuzzTest {

    private final BlockFactory blockFactory = new BlockFactory(ActivationConfigsForTest.all());

    @Tag("BlockDecodingFuzzBlockDecoding")
    @FuzzTest
    void testBlockDecoding(FuzzedDataProvider data) {
        byte[] bs;
        try {
             bs = data.consumeBytes(80);
            blockFactory.decodeBlock(bs);
        } catch (Exception e) {}

        try {
            bs = data.consumeBytes(256);
            blockFactory.decodeBlock(bs);
        } catch (Exception e) {}

        try {
            bs = data.consumeBytes(4096);
            blockFactory.decodeBlock(bs);
        } catch (Exception e) {}

    }

}
