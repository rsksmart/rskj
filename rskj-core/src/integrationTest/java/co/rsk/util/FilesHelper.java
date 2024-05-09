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
