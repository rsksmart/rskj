package co.rsk.util;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public class ConfigFileLoader {

    // For now, this is a singleton class, and all methods are static.
    // Why? Because it will only be used for testing, and benchmarking, and tweaking,
    // but not in production.

    private ConfigFileLoader() {
    }

    private static Map<ConfigRemap, String> fileLocations = Collections.emptyMap();

    public static void setRemaps(Map<ConfigRemap, String> remaps) {
        if (remaps == null) {
            throw new IllegalStateException("Remaps cannot be null");
        }

        fileLocations = remaps;
    }

    /**
     * Retrieves Config either from filesystem (remapping) or regular embedded resource load.
     *
     * @param resourcePath   Path for the config file to be loaded as embedded resource. Can be relative to {@code origin} class or absolute depending on the provided {@code resourceLoader}. Ignored if a remap is defined and found.
     * @param resourceLoader Embedded-resource loader. Ignored if a remap is defined and found.
     * @param configRemap    Remap type to be checked & applied if defined and found.
     * @return InputStream for the loaded config
     */
    public static InputStream loadConfigurationFile(String resourcePath, ResourceLoader resourceLoader, ConfigRemap configRemap) {
        InputStream is = loadConfigurationFileFromFilesystem(configRemap);
        if (is != null) {
            return is;
        }

        return resourceLoader.getResourceAsStream(resourcePath);
    }

    private static InputStream loadConfigurationFileFromFilesystem(ConfigRemap configRemap) {
        String location = fileLocations.get(configRemap);
        if (location == null) {
            return null;
        }

        Path path = Paths.get(location);
        try {
            return new FileInputStream(path.toFile());
        } catch (FileNotFoundException e) {
            // if not found, abort
            throw new IllegalStateException("Remapped config file " + path + " not found");
        }

    }

    public interface ResourceLoader {
        InputStream getResourceAsStream(String name);
    }

    public enum ConfigRemap {

        BUILD_INFO("build-info"),
        REMASC("remasc"),
        RSK_BITCOIN_CHECKPOINTS("rskbitcoincheckpoints"),
        GENESIS("genesis");

        private final String id;

        ConfigRemap(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public static Optional<ConfigRemap> lookUp(String id) {
            return Arrays.stream(values()).filter(r -> r.id.equals(id)).findFirst();
        }
    }
}
