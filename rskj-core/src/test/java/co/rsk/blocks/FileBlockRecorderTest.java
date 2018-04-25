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

package co.rsk.blocks;

import co.rsk.blockchain.utils.BlockGenerator;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;

/**
 * Created by usuario on 20/01/2017.
 */
public class FileBlockRecorderTest {
    @Test
    public void createFile() throws Exception {
        FileBlockRecorder recorder = new FileBlockRecorder("testrecorder.txt");

        File file = new File("testrecorder.txt");
        Assert.assertTrue(file.exists());

        recorder.close();

        Assert.assertTrue(file.delete());
    }

    @Test
    public void writeBlock() throws Exception {
        FileBlockRecorder recorder = new FileBlockRecorder("testrecorder.txt");
        recorder.writeBlock(new BlockGenerator().getGenesisBlock());

        File file = new File("testrecorder.txt");
        Assert.assertTrue(file.exists());

        recorder.close();

        Assert.assertTrue(file.delete());
    }
}
