package co.rsk;

import co.rsk.util.CommandLineFixture;
import co.rsk.util.RskjCommandLineBase;
import co.rsk.util.StreamGobbler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class NodeIntegrationTestCommandLine extends RskjCommandLineBase {
    private String modeArg;
    private String bloomsDbDir;
    private int port;
    private String rskConfFilePath;
    private Path tempDir;
    private int timeout = 0;

    public NodeIntegrationTestCommandLine(int port, Path tempDir, String rskConfFilePath, String modeArg) {
        super("co.rsk.Start ", new String[]{}, new String[]{});
        this.port = port;
        this.tempDir = tempDir;
        this.rskConfFilePath = rskConfFilePath;
        this.modeArg = modeArg;
    }

    public NodeIntegrationTestCommandLine(int port, Path tempDir, String rskConfFilePath, String modeArg, int timeout) {
        this(port, tempDir, rskConfFilePath, modeArg);
        this.timeout = timeout;
    }

    private void setUp() throws IOException {
        String projectPath = System.getProperty("user.dir");
        String integrationTestResourcesPath = String.format("%s/src/integrationTest/resources", projectPath);
        String logbackXmlFile = String.format("%s/logback.xml", integrationTestResourcesPath);
        Path databaseDirPath = Files.createDirectories(tempDir.resolve("database"));
        arguments = new String[]{
                String.format("-Xdatabase.dir=%s", databaseDirPath.toString()),
                this.modeArg,
                "-Xkeyvalue.datasource=rocksdb",
                String.format("-Xrpc.providers.web.http.port=%s", port)
        };
        parameters = new String[]{
                String.format("-Dlogback.configurationFile=%s", logbackXmlFile),
                String.format("-Drsk.conf.file=%s", rskConfFilePath)
        };
        bloomsDbDir = Files.createDirectories(databaseDirPath.resolve("blooms")).toString();
    }

    public Process startNode() throws IOException, InterruptedException {
        return startNode(null);
    }

    public Process startNode(Consumer<Process> beforeDestroyFn) throws IOException, InterruptedException {
        setUp();

        try {
            executeCommand(timeout);
        } finally {
            if(timeout != 0) {
                killNode(beforeDestroyFn);
            }
        }

        return cliProcess;  // We return the process so the test can use it to waitFor, to kill, to add in a Future operation
    }

    public int killNode() throws InterruptedException {
        return this.killNode(null);
    }

    public int killNode(Consumer<Process> beforeDestroyFn) throws InterruptedException {
        if (beforeDestroyFn != null) {
            beforeDestroyFn.accept(cliProcess);
        }
        if(cliProcess.isAlive()){
            cliProcess.destroyForcibly();
        }
        cliProcess.waitFor(1, TimeUnit.MINUTES); // We have to wait a bit so the process finishes the kill command
        return cliProcess.exitValue();
    }
}
