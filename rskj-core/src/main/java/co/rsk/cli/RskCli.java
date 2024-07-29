/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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
package co.rsk.cli;

import co.rsk.config.NodeCliFlags;
import co.rsk.config.NodeCliOptions;
import co.rsk.util.VersionProviderUtil;
import picocli.CommandLine;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CommandLine.Command(name = "rskj", mixinStandardHelpOptions = true, versionProvider = VersionProviderUtil.class,
        description = "RSKJ blockchain node implementation in Java")
public class RskCli implements Runnable {

    // CLI FLAGS
    // network flags
    static class NetworkFlags {
        @CommandLine.Option(names = {"--testnet"}, description = "Use testnet configuration")
        private boolean networkTestnet;

        @CommandLine.Option(names = {"--regtest"}, description = "Use regtest configuration")
        private boolean networkRegtest;

        @CommandLine.Option(names = {"--devnet"}, description = "Use devnet configuration")
        private boolean networkDevnet;

        @CommandLine.Option(names = {"--main"}, description = "Use mainnet configuration")
        private boolean networkMainnet;
    }

    @CommandLine.ArgGroup(exclusive = true, multiplicity = "0..1")
    private NetworkFlags networkFlags;

    // db flags
    @CommandLine.Option(names = {"-r", "--reset"}, description = "Reset the database")
    private boolean dbReset;

    @CommandLine.Option(names = {"-i", "--import"}, description = "Import database")
    private boolean dbImport;

    // config flags
    @CommandLine.Option(names = {"--verify-config"}, description = "Verify configuration")
    private boolean verifyConfig;

    @CommandLine.Option(names = {"--print-system-info"}, description = "Print system info")
    private boolean printSystemInfo;

    @CommandLine.Option(names = {"--skip-java-check"}, description = "Skip Java version check")
    private boolean skipJavaCheck;

    // CLI OPTIONS
    @CommandLine.Option(names = {"-rpccors"}, description = "Set RPC CORS")
    private String rpcCors;

    @CommandLine.Option(names = {"-base-path"}, description = "Set base path")
    private String basePath;

    @CommandLine.Option(names = {"-X"}, description = "Read arguments in command line")
    private List<String> xArguments;

    @CommandLine.Option(names = {"--sync-mode"}, description = "Set Synchronization mode. Valid options are <full | snap>")
    private String syncMode;

    @CommandLine.Option(names = {"--snap-nodes"}, description = "Set snapboot nodes")
    private List<String> snapBootNodes;

    private boolean help;
    private boolean version;

    private CliArgs<NodeCliOptions, NodeCliFlags> cliArgs;

    public int load(String[] args) {
        CommandLine commandLine = new CommandLine(this).setUnmatchedArgumentsAllowed(true);
        int exitCode = commandLine.execute(args);
        version = commandLine.isVersionHelpRequested();
        help = commandLine.isUsageHelpRequested();
        loadCliArgs();
        return exitCode;
    }

    public CliArgs<NodeCliOptions, NodeCliFlags> getCliArgs() {
       return cliArgs;
    }

    private void loadCliArgs() {
        EnumSet<NodeCliFlags> activatedFlags = EnumSet.noneOf(NodeCliFlags.class);
        Map<NodeCliOptions, String> activatedOptions = new HashMap<>();
        Map<String, String> paramValueMap = new HashMap<>();

        if (help) {
            activatedFlags.add(NodeCliFlags.HELP);
        }
        if (version) {
            activatedFlags.add(NodeCliFlags.VERSION);
        }

        if (dbReset) {
            activatedFlags.add(NodeCliFlags.DB_RESET);
        }

        if (dbImport) {
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

        if (rpcCors != null) {
            activatedOptions.put(NodeCliOptions.RPC_CORS, rpcCors);
        }

        if (basePath != null) {
            activatedOptions.put(NodeCliOptions.BASE_PATH, basePath);
        }

        if (xArguments != null) {
            for (String arg : xArguments) {
                String[] keyValuePair = splitXArgumentIntoKeyValue(arg);
                paramValueMap.put(keyValuePair[0], keyValuePair[1]);
            }
        }

        if (networkFlags != null) {
            if (networkFlags.networkTestnet) {
                activatedFlags.add(NodeCliFlags.NETWORK_TESTNET);
            } else if (networkFlags.networkRegtest) {
                activatedFlags.add(NodeCliFlags.NETWORK_REGTEST);
            } else if (networkFlags.networkDevnet) {
                activatedFlags.add(NodeCliFlags.NETWORK_DEVNET);
            } else if (networkFlags.networkMainnet) {
                activatedFlags.add(NodeCliFlags.NETWORK_MAINNET);
            }
        }

        if (syncMode != null) {
            activatedOptions.put(NodeCliOptions.SYNC_MODE, syncMode);
        }

        if (snapBootNodes != null) {
            activatedOptions.put(NodeCliOptions.SNAP_NODES, String.join(",", snapBootNodes));
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
