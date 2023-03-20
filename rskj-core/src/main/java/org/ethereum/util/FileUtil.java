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

package org.ethereum.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.lang.System.getProperty;

public class FileUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger("file");

    private FileUtil() {

    }

    public static Path getDatabaseDirectoryPath(String databaseDirectory, String name) {
        if (Paths.get(databaseDirectory).isAbsolute()) {
            return Paths.get(databaseDirectory, name);
        } else {
            return Paths.get(getProperty("user.dir"), databaseDirectory, name);
        }
    }

    public static boolean recursiveDelete(String fileName) {
        File file = new File(fileName);
        if (file.exists()) {
            //check if the file is a directory
            if (file.isDirectory() && (file.list()).length > 0) {
                for(String s:file.list()){
                    //call deletion of file individually
                    recursiveDelete(fileName + System.getProperty("file.separator") + s);
                }
            }

            if (!file.setWritable(true)) {
                LOGGER.error("File {} is not writable", file);
            }

            boolean result = file.delete();
            return result;
        } else {
            return false;
        }
    }

}
