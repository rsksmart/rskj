package co.rsk.cli;

import co.rsk.NodeRunner;
import co.rsk.config.NodeCliFlags;
import co.rsk.config.NodeCliOptions;
import picocli.CommandLine;

import java.util.EnumSet;

@CommandLine.Command(name = "rskj", mixinStandardHelpOptions = true, version = "RSKJ Node 4.5.0",
        description = "RSKJ blockchain node implementation in Java")
public class RskCli {

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


    private NodeRunner runner;

    public void setRunner(NodeRunner runner) {
        this.runner = runner;
    }

    public void run() {

    }
    public CliArgs<NodeCliOptions, NodeCliFlags> getCliArgs() {
        // TODO: implement this method watching ConfigLoader.getConfigFromCliArgs()
        return null;
    }
    private EnumSet<NodeCliFlags> getFlags() {
        EnumSet<NodeCliFlags> activatedFlags = EnumSet.noneOf(NodeCliFlags.class);

        if (dbReset) {
            activatedFlags.add(NodeCliFlags.DB_RESET);
        }

        if (dbImport != null) {
            activatedFlags.add(NodeCliFlags.DB_IMPORT);
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

        return activatedFlags;
    }



}
