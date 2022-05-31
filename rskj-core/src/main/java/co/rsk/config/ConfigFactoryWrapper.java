package co.rsk.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigIncluder;

import java.io.File;

public class ConfigFactoryWrapper {

    private static ConfigFactoryWrapper instance;

    public static ConfigFactoryWrapper getInstance() {
        if (instance == null) {
            instance = new ConfigFactoryWrapper();
        }

        return instance;
    }

    public Config systemProperties() {
        return ConfigFactory.systemProperties();
    }

    public Config systemEnvironment() {
        return ConfigFactory.systemEnvironment();
    }

    public Config empty() {
        return ConfigFactory.empty();
    }

    public Config parseResourcesAnySyntax(String resourcePath) {
        return ConfigFactory.parseResourcesAnySyntax(resourcePath);
    }

    public Config parseFile(File file) {
        return ConfigFactory.parseFile(file);
    }

    public Config load(String resourcePath) {
        return ConfigFactory.load(resourcePath);
    }
}
