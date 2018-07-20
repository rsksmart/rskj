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

import org.ethereum.core.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bouncycastle.util.encoders.Hex;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by ajlopez on 5/8/2016.
 */
public class FileBlockRecorder implements BlockRecorder, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger("blockrecorder");
    private SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private BufferedWriter writer;
    private FileWriter fwriter;

    public FileBlockRecorder(String filename) {
        try {
            this.fwriter = new FileWriter(filename);
            this.writer = new BufferedWriter(this.fwriter);
        }
        catch (IOException ex) {
            logger.error("Exception creating file block recorder: ", ex);
        }
    }

    public void writeBlock(Block block) {
        try {
            writer.write(formatter.format(new Date()));
            writer.write(",");
            writer.write(String.valueOf(block.getNumber()));
            writer.write(",");
            writer.write(Hex.toHexString(block.getEncoded()));
            writer.newLine();
            writer.flush();
        }
        catch (IOException ex) {
            logger.error("Exception writing block: ", ex);
        }
    }

    @Override
    public void close() throws Exception {
        if (this.fwriter != null) {
            this.fwriter.close();
            this.fwriter = null;
        }
    }
}

