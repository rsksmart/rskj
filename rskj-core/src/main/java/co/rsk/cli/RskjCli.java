package co.rsk.cli;

import picocli.CommandLine;

import java.util.concurrent.Callable;

import picocli.CommandLine;

@CommandLine.Command(name = "rskj", mixinStandardHelpOptions = true, version = "RSKJ Node 4.5.0",
        description = "RSKJ blockchain node implementation in Java")
public class RskjCli {
    @CommandLine.Option(names = {"-r", "--reset"}, description = "Reset the database")
    private boolean dbReset;

    public boolean isVersionRequested() {
        return versionRequested;
    }

    public boolean isUsageRequested() {
        return usageRequested;
    }

    @CommandLine.Option(names = {"-v", "--version"}, versionHelp = true, description = "Display version info")
    private boolean versionRequested;

    @CommandLine.Option(names = {"-h", "--help"}, usageHelp = true, description = "Display this help message")
    private boolean usageRequested;

}
