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

package co.rsk.test.builderstest;

import co.rsk.core.bc.BlockChainImpl;
import co.rsk.test.builders.BlockChainBuilder;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created by ajlopez on 8/7/2016.
 */
public class BlockChainBuilderTest {
    @Test
    public void createBlockChain() {
        BlockChainBuilder builder = new BlockChainBuilder();
        BlockChainImpl blockChain = builder.build();

        Assert.assertNotNull(blockChain);
        Assert.assertNotNull(blockChain.getRepository());
        Assert.assertNotNull(blockChain.getBlockStore());
    }
}
