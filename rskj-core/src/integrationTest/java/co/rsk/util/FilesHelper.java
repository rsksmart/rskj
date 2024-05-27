/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
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
package co.rsk.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class FilesHelper {
    public static String getIntegrationTestResourcesFullPath(String fileName) {
        String projectPath = System.getProperty("user.dir");
        String integrationTestResourcesPath = String.format("%s/src/integrationTest/resources/", projectPath);
        return integrationTestResourcesPath + fileName;
    }

    public static String getAbsolutPathFromResourceFile(Class clazz, String resourceFile) {
        ClassLoader classLoader = clazz.getClassLoader();
        File file = new File(classLoader.getResource(resourceFile).getFile());
        return file.getAbsolutePath();
    }

    public static byte[] readBytesFromFile(String filePath) throws IOException {
        try (InputStream inputStream = new FileInputStream(filePath);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            return outputStream.toByteArray();
        }
    }

    public static void writeBytesToFile(byte[] bytes, String filePath) throws IOException {
        try (OutputStream outputStream = new FileOutputStream(filePath)) {
            outputStream.write(bytes);
        }
    }

    public static void deleteContents(File folder) {
        if (folder.isDirectory()) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteContents(file);
                }
            }
        }
        folder.delete();
    }
}
