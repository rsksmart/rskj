package co.rsk.util;

import co.rsk.config.RskSystemProperties;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class ConfigFileLoader {
    // For now, this is a singleton class, and all methods are static.
    // Why? Because it will only be used for testing, and benchmarking, and tweaking,
    // but not in production.

    static Map<String,String> fileLocations;
    public static void setRemaps(Map<String,String> prop) {
        fileLocations = prop;
    }

    public static InputStream loadConfigurationFileFromFilesystem( String strPathId, String fileName) {
        if (fileLocations == null)
            return null;
        String location = null;
        location = fileLocations.get(strPathId);
        if (location==null)
            return null;
        Path path = Paths.get(location);
        path = path.resolve(fileName);

        try {
            InputStream is = new FileInputStream(path.toFile());
            return is;
        } catch (FileNotFoundException e) {
            // if not found, abort
            throw new RuntimeException("Configuration file " + path.toString() + " not found");
        }

    }
    // If relativeToClass is true, then the strPathId will be searched relative to the .class file position,
    // If relativeToClass is false, then strPathId will be an absolute path to be take from the resource root.
    // The path will be remapped, BUT the filename is not.
    public static InputStream loadConfigurationFile(Class origin,boolean relativeToClass,
                                                    String strPathId, String resourcePath,String fileName) {

        InputStream is = loadConfigurationFileFromFilesystem(strPathId,fileName);
        if (is != null)
            return is;

        String resourcePathname = resourcePath+fileName;

        if (relativeToClass) {
            is = origin.getResourceAsStream(resourcePathname);
        } else {
            is = origin.getClassLoader().getResourceAsStream(resourcePathname);
        }
        return is;
    }
}
