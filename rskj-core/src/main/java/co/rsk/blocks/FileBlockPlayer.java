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

import co.rsk.config.RskSystemProperties;
import org.ethereum.core.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bouncycastle.util.encoders.Hex;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * Created by ajlopez on 5/8/2016.
 */
public class FileBlockPlayer implements BlockPlayer, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger("blockplayer");
    private BufferedReader reader;
    private FileReader freader;
    private final RskSystemProperties config;

    public FileBlockPlayer(RskSystemProperties config, String filename) {
        this.config = config;
        try {
            this.freader = new FileReader(filename);
            this.reader = new BufferedReader(this.freader);
        }
        catch (IOException ex) {
            logger.error("Exception opening file block player", ex);
        }
    }

    public Block readBlock() {
        try {
            String line = this.reader.readLine();

            if (line == null) {
                return null;
            }

            String[] parts = line.split(",");

            return new Block(Hex.decode(parts[parts.length - 1]));
        }
        catch (IOException ex) {
            logger.error("Exception reader block", ex);
        }

        return null;
    }

    @Override
    public void close() throws Exception {
        if (this.freader != null) {
            this.freader.close();
            this.freader = null;
        }
    }

}
