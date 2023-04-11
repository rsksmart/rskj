package co.rsk.cli;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigValueFactory;
import org.ethereum.config.SystemProperties;
import picocli.CommandLine;

@CommandLine.Command(name = "rskj", mixinStandardHelpOptions = true, version = "RSKJ Node 4.5.0", description = "RSKJ node command line interface")
public class PicoNewParser implements Runnable {
    @CommandLine.Option(names = {"-c", "--config"}, description = "Path to the configuration file")
    private String configPath;


    public String getConfigPath() {
        return configPath;
    }

    // Add other options from NodeCliFlags/NodeCliOptions here

    public static void main(String[] args) {
        int exitCode = new CommandLine(new PicoNewParser()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        // Initialize and start the RSKJ node with the provided options
        // Use the configPath and other options to configure the node
    }

    @CommandLine.Option(names = {"--reset"}, description = "Reset the database")
    private boolean dbReset;

    @CommandLine.Option(names = {"--import"}, description = "Import the database")
    private boolean dbImport;

    @CommandLine.Option(names = {"--verify-config"}, description = "Verify configuration")
    private boolean verifyConfig;

    @CommandLine.Option(names = {"--print-system-info"}, description = "Print system information")
    private boolean printSystemInfo;

    @CommandLine.Option(names = {"--skip-java-check"}, description = "Skip Java version check")
    private boolean skipJavaCheck;

    @CommandLine.Option(names = {"--testnet"}, description = "Use testnet network")
    private boolean testnet;

    @CommandLine.Option(names = {"--regtest"}, description = "Use regtest network")
    private boolean regtest;

    @CommandLine.Option(names = {"--devnet"}, description = "Use devnet network")
    private boolean devnet;

    @CommandLine.Option(names = {"--main"}, description = "Use mainnet network")
    private boolean mainnet;

    public Config updateConfig(Config config) {
        if (dbReset) {
            config = config.withValue(SystemProperties.PROPERTY_DB_RESET, ConfigValueFactory.fromAnyRef(true));
        }
        if (dbImport) {
            config = config.withValue(SystemProperties.PROPERTY_DB_IMPORT, ConfigValueFactory.fromAnyRef(true));
        }
        if (verifyConfig) {
            config = config.withValue(SystemProperties.PROPERTY_BC_VERIFY, ConfigValueFactory.fromAnyRef(true));
        }
        if (printSystemInfo) {
            config = config.withValue(SystemProperties.PROPERTY_PRINT_SYSTEM_INFO, ConfigValueFactory.fromAnyRef(true));
        }
        if (skipJavaCheck) {
            config = config.withValue(SystemProperties.PROPERTY_SKIP_JAVA_VERSION_CHECK, ConfigValueFactory.fromAnyRef(false));
        }
        if (testnet) {
            config = config.withValue(SystemProperties.PROPERTY_BC_CONFIG_NAME, ConfigValueFactory.fromAnyRef("testnet"));
        }
        if (regtest) {
            config = config.withValue(SystemProperties.PROPERTY_BC_CONFIG_NAME, ConfigValueFactory.fromAnyRef("regtest"));
        }
        if (devnet) {
            config = config.withValue(SystemProperties.PROPERTY_BC_CONFIG_NAME, ConfigValueFactory.fromAnyRef("devnet"));
        }
        if (mainnet) {
            config = config.withValue(SystemProperties.PROPERTY_BC_CONFIG_NAME, ConfigValueFactory.fromAnyRef("main"));
        }

        return config;
    }

    public boolean isDbReset() {
        return dbReset;
    }

    public boolean isDbImport() {
        return dbImport;
    }

    public boolean isVerifyConfig() {
        return verifyConfig;
    }

    public boolean isPrintSystemInfo() {
        return printSystemInfo;
    }

    public boolean isSkipJavaCheck() {
        return skipJavaCheck;
    }

    public boolean isTestnet() {
        return testnet;
    }

    public boolean isRegtest() {
        return regtest;
    }

    public boolean isDevnet() {
        return devnet;
    }

    public boolean isMainnet() {
        return mainnet;
    }
}
