package co.rsk.cli;

import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

import picocli.CommandLine;

@CommandLine.Command(name = "rskj", mixinStandardHelpOptions = true, version = "RSKJ Node 4.5.0",
        description = "RSKJ blockchain node implementation in Java")
public class RskjCli {
    @CommandLine.Option(names = {"-v", "--version"}, versionHelp = true, description = "Display version info")
    private boolean versionRequested;
    @CommandLine.Option(names = {"-h", "--help"}, usageHelp = true, description = "Display this help message")
    private boolean usageRequested;

    @CommandLine.Option(names = {"-r", "--reset"}, description = "Reset the database")
    private boolean dbReset;

    @CommandLine.Option(names = {"--rpccors"}, description = "Set RPC CORS")
    private String rpcCors;

    @CommandLine.Option(names = {"--base-path"}, description = "Set base path")
    private String basePath;

    @CommandLine.Option(names = {"--db-import"}, description = "Import database")
    private String dbImport;

    @CommandLine.Option(names = {"--verify-config"}, description = "Verify configuration")
    private boolean verifyConfig;

    @CommandLine.Option(names = {"--print-system-info"}, description = "Print system info")
    private boolean printSystemInfo;

    @CommandLine.Option(names = {"--skip-java-check"}, description = "Skip Java version check")
    private boolean skipJavaCheck;

    @CommandLine.Option(names = {"--testnet"}, description = "Use testnet configuration")
    private boolean networkTestnet;

    @CommandLine.Option(names = {"--regtest"}, description = "Use regtest configuration")
    private boolean networkRegtest;

    @CommandLine.Option(names = {"--devnet"}, description = "Use devnet configuration")
    private boolean networkDevnet;

    @CommandLine.Option(names = {"--main"}, description = "Use mainnet configuration")
    private boolean networkMainnet;

    public void run() {
        // logic to handle options and flags
    }

    public boolean isVersionRequested() {
        return versionRequested;
    }

    public boolean isUsageRequested() {
        return usageRequested;
    }



}
