package co.rsk.util;

import picocli.CommandLine;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class VersionProviderUtil implements CommandLine.IVersionProvider {
    @Override
    public String[] getVersion() throws Exception {
        Properties prop = new Properties();
        String propFileName = "version.properties";

        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(propFileName);
        if (inputStream != null) {
            prop.load(inputStream);
        } else {
            throw new IOException("Property file '" + propFileName + "' not found in the classpath");
        }

        String appVersion = prop.getProperty("versionNumber").replace("\'", "");

        return new String[]{appVersion};
    }
}