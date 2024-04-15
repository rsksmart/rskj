package co.rsk;

import co.rsk.util.CommandLineFixture;
import co.rsk.util.StreamGobbler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class NodeIntegrationTestCommandLine {
    private String modeArg;
    private String buildLibsPath;
    private String jarName;
    private String databaseDir;
    private String bloomsDbDir;
    private String[] baseArgs;
    private String strBaseArgs;
    private String baseJavaCmd;
    private int port;
    private String rskConfFilePath;
    private Path tempDir;
    private int timeout = 0;
    private StringBuilder processOutputBuilder;
    private Consumer<String> procOutputConsumer = output -> appendLinesToProcessOutput(output);
    private Process nodeProcess;

    public NodeIntegrationTestCommandLine(int port, Path tempDir, String rskConfFilePath, String modeArg) {
        this.port = port;
        this.tempDir = tempDir;
        this.rskConfFilePath = rskConfFilePath;
        this.modeArg = modeArg;
    }

    public NodeIntegrationTestCommandLine(int port, Path tempDir, String rskConfFilePath, String modeArg, int timeout) {
        this(port, tempDir, rskConfFilePath, modeArg);
        this.timeout = timeout;
    }

    private void appendLinesToProcessOutput(String output){
        processOutputBuilder.append(output).append(System.lineSeparator());
    }

    private void configureProcessStreamReaderToGetCommandLineOutput() {
        StreamGobbler streamGobbler = new StreamGobbler(nodeProcess.getInputStream(), procOutputConsumer);
        Executors.newSingleThreadExecutor().submit(streamGobbler);
    }

    //TODO: Improve this to use ProcessBuilder
    private void setUp() throws IOException {
        processOutputBuilder = new StringBuilder();
        String projectPath = System.getProperty("user.dir");
        buildLibsPath = String.format("%s/build/libs", projectPath);
        String integrationTestResourcesPath = String.format("%s/src/integrationTest/resources", projectPath);
        String logbackXmlFile = String.format("%s/logback.xml", integrationTestResourcesPath);
        //String rskConfFile = String.format("%s/%s", integrationTestResourcesPath, rskConfFilePath);
        Stream<Path> pathsStream = Files.list(Paths.get(buildLibsPath));
        jarName = pathsStream.filter(p -> !p.toFile().isDirectory())
                .map(p -> p.getFileName().toString())
                .filter(fn -> fn.endsWith("-all.jar"))
                .findFirst()
                .get();
        Path databaseDirPath = Files.createDirectories(tempDir.resolve("database"));
        databaseDir = databaseDirPath.toString();
        bloomsDbDir = Files.createDirectories(databaseDirPath.resolve("blooms")).toString();
        baseArgs = new String[]{
                String.format("-Xdatabase.dir=%s", databaseDir),
                this.modeArg,
                "-Xkeyvalue.datasource=rocksdb",
                String.format("-Xrpc.providers.web.http.port=%s", port)
        };
        strBaseArgs = String.join(" ", baseArgs);
        baseJavaCmd = String.format("java %s %s", String.format("-Dlogback.configurationFile=%s", logbackXmlFile), String.format("-Drsk.conf.file=%s", rskConfFilePath));
    }

    public Process startNode() throws IOException, InterruptedException {
        return startNode(null);
    }

    public Process startNode(Consumer<Process> beforeDestroyFn) throws IOException, InterruptedException {
        setUp();
        String cmd = String.format("%s -cp %s/%s co.rsk.Start --reset %s", baseJavaCmd, buildLibsPath, jarName, strBaseArgs);
        nodeProcess = Runtime.getRuntime().exec(cmd);
        configureProcessStreamReaderToGetCommandLineOutput();

        try {
            if(timeout != 0) {
                nodeProcess.waitFor(timeout, TimeUnit.MINUTES);
            }
        } finally {
            if (!nodeProcess.isAlive() && nodeProcess.exitValue() != 0) {
                    String procError = CommandLineFixture.readProcStream(nodeProcess.getErrorStream());
                    System.out.println("procError:" + procError);
                    System.out.println("Proc exited with value: " + nodeProcess.exitValue());
            } else if(timeout != 0) {
                killNode(beforeDestroyFn);
            }
        }

        return nodeProcess;  // We return the process so the test can use it to waitFor, to kill, to add in a Future operation
    }

    public String appendLinesToProcessOutput() {
        return this.processOutputBuilder.toString();
    }

    public int killNode() throws InterruptedException {
        return this.killNode(null);
    }

    public int killNode(Consumer<Process> beforeDestroyFn) throws InterruptedException {
        if (beforeDestroyFn != null) {
            beforeDestroyFn.accept(nodeProcess);
        }
        if(nodeProcess.isAlive()){
            nodeProcess.destroyForcibly();
        }
        nodeProcess.waitFor(1, TimeUnit.MINUTES); // We have to wait a bit so the process finishes the kill command
        return nodeProcess.exitValue();
    }
}
