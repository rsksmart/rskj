package co.rsk.cli;

import co.rsk.config.NodeCliFlags;
import co.rsk.config.NodeCliOptions;
import co.rsk.util.VersionProviderUtil;
import picocli.CommandLine;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
@CommandLine.Command(name = "rskj", mixinStandardHelpOptions = true, versionProvider = VersionProviderUtil.class,
        description = "RSKJ blockchain node implementation in Java")
public class RskCli implements Runnable {

    // CLI FLAGS
    // db flags
    @CommandLine.Option(names = {"-r", "--reset"}, description = "Reset the database")
    private boolean dbReset;

    @CommandLine.Option(names = {"--import"}, description = "Import database")
    private String dbImport;

    // config flags
    @CommandLine.Option(names = {"--verify-config"}, description = "Verify configuration")
    private boolean verifyConfig;

    @CommandLine.Option(names = {"--print-system-info"}, description = "Print system info")
    private boolean printSystemInfo;

    @CommandLine.Option(names = {"--skip-java-check"}, description = "Skip Java version check")
    private boolean skipJavaCheck;

    // network
    @CommandLine.Option(names = {"--testnet"}, description = "Use testnet configuration")
    private boolean networkTestnet;

    @CommandLine.Option(names = {"--regtest"}, description = "Use regtest configuration")
    private boolean networkRegtest;

    @CommandLine.Option(names = {"--devnet"}, description = "Use devnet configuration")
    private boolean networkDevnet;

    @CommandLine.Option(names = {"--main"}, description = "Use mainnet configuration")
    private boolean networkMainnet;

    // CLI OPTIONS
    @CommandLine.Option(names = {"-rpccors"}, description = "Set RPC CORS")
    private String rpcCors;

    @CommandLine.Option(names = {"-base-path"}, description = "Set base path")
    private String basePath;

    @CommandLine.Option(names = {"-X"}, description = "Read arguments in command line")
    private String xArgument;
    private boolean versionOrHelpRequested = false;
    private CliArgs<NodeCliOptions, NodeCliFlags> cliArgs;

    public int load(String[] args) {
        CommandLine commandLine = new CommandLine(this);
        int exitCode = commandLine.execute(args);
        if (commandLine.isVersionHelpRequested() || commandLine.isUsageHelpRequested()) {
           this.versionOrHelpRequested = true;
        }
        loadCliArgs();
        return exitCode;
    }

    public CliArgs<NodeCliOptions, NodeCliFlags> getCliArgs() {
       return cliArgs;
    }

    public boolean isVersionOrHelpRequested() {
        return versionOrHelpRequested;
    }

    private void loadCliArgs() {
        EnumSet<NodeCliFlags> activatedFlags = EnumSet.noneOf(NodeCliFlags.class);
        Map<NodeCliOptions, String> activatedOptions = new HashMap<>();
        Map<String, String> paramValueMap = new HashMap<>();

        if (dbReset) {
            activatedFlags.add(NodeCliFlags.DB_RESET);
        }

        if (dbImport != null) {
            activatedFlags.add(NodeCliFlags.DB_IMPORT);
            paramValueMap.put("import", dbImport);
        }

        if (verifyConfig) {
            activatedFlags.add(NodeCliFlags.VERIFY_CONFIG);
        }

        if (printSystemInfo) {
            activatedFlags.add(NodeCliFlags.PRINT_SYSTEM_INFO);
        }

        if (skipJavaCheck) {
            activatedFlags.add(NodeCliFlags.SKIP_JAVA_CHECK);
        }

        if (networkTestnet) {
            activatedFlags.add(NodeCliFlags.NETWORK_TESTNET);
        }

        if (networkRegtest) {
            activatedFlags.add(NodeCliFlags.NETWORK_REGTEST);
        }

        if (networkDevnet) {
            activatedFlags.add(NodeCliFlags.NETWORK_DEVNET);
        }

        if (networkMainnet) {
            activatedFlags.add(NodeCliFlags.NETWORK_MAINNET);
        }

        if (rpcCors != null) {
            activatedOptions.put(NodeCliOptions.RPC_CORS, rpcCors);
            paramValueMap.put("rpc-cors", rpcCors);
        }

        if (basePath != null) {
            activatedOptions.put(NodeCliOptions.BASE_PATH, basePath);
            paramValueMap.put("base-path", basePath);
        }

        if (xArgument != null) {
            String[] keyValuePair = splitXArgumentIntoKeyValue(xArgument);
            paramValueMap.put(keyValuePair[0], keyValuePair[1]);
        }

        cliArgs = CliArgs.of(activatedOptions, activatedFlags, paramValueMap);
    }

    private String[] splitXArgumentIntoKeyValue(String xArgument) throws IllegalArgumentException {
        String[] keyValuePair = xArgument.split("=", 2);
        if (keyValuePair.length != 2) {
            throw new IllegalArgumentException("Invalid format for -X argument. Expected format is key=value.");
        }
        return keyValuePair;
    }
    @Override
    public void run() {
    }
}
