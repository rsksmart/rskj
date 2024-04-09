package co.rsk;

import co.rsk.util.CommandLineFixture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class NodeIntegrationTestCommandLine {
    private String buildLibsPath;
    private String jarName;
    private String databaseDir;
    private String bloomsDbDir;
    private String[] baseArgs;
    private String strBaseArgs;
    private String baseJavaCmd;
    private int port;
    private String rskConfFileName;
    private Path tempDatabaseDir;
    private int timeout = 30;
    private String procOutput;

    public NodeIntegrationTestCommandLine(int port, Path tempDatabaseDir, String rskConfFileName) {
        this.port = port;
        this.tempDatabaseDir = tempDatabaseDir;
        this.rskConfFileName = rskConfFileName;
    }

    public NodeIntegrationTestCommandLine(int port, Path tempDatabaseDir, String rskConfFileName, int timeout) {
        this(port, tempDatabaseDir, rskConfFileName);
        this.timeout = timeout;
    }

    private void setUp() throws IOException {
        String projectPath = System.getProperty("user.dir");
        buildLibsPath = String.format("%s/build/libs", projectPath);
        String integrationTestResourcesPath = String.format("%s/src/integrationTest/resources", projectPath);
        String logbackXmlFile = String.format("%s/logback.xml", integrationTestResourcesPath);
        String rskConfFile = String.format("%s/%s", integrationTestResourcesPath, rskConfFileName);
        Stream<Path> pathsStream = Files.list(Paths.get(buildLibsPath));
        jarName = pathsStream.filter(p -> !p.toFile().isDirectory())
                .map(p -> p.getFileName().toString())
                .filter(fn -> fn.endsWith("-all.jar"))
                .findFirst()
                .get();
        Path databaseDirPath = tempDatabaseDir.resolve("database");
        databaseDir = databaseDirPath.toString();
        bloomsDbDir = databaseDirPath.resolve("blooms").toString();
        baseArgs = new String[]{
                String.format("-Xdatabase.dir=%s", databaseDir),
                "--regtest",
                "-Xkeyvalue.datasource=rocksdb",
                String.format("-Xrpc.providers.web.http.port=%s", port)
        };
        strBaseArgs = String.join(" ", baseArgs);
        baseJavaCmd = String.format("java %s %s", String.format("-Dlogback.configurationFile=%s", logbackXmlFile), String.format("-Drsk.conf.file=%s", rskConfFile));
    }

    public String startNode(Consumer<Process> beforeDestroyFn) throws IOException, InterruptedException {
        setUp();
        String cmd = String.format("%s -cp %s/%s co.rsk.Start --reset %s", baseJavaCmd, buildLibsPath, jarName, strBaseArgs);
        Process proc = Runtime.getRuntime().exec(cmd);

        try {
            proc.waitFor(timeout, TimeUnit.MINUTES);

            procOutput = CommandLineFixture.readProcStream(proc.getInputStream());
            String procError = CommandLineFixture.readProcStream(proc.getErrorStream());

            if (!proc.isAlive()) {
                if(proc.exitValue() != 0){
                    System.out.println("procError:" + procError);
                    System.out.println("Proc exited with value: " + proc.exitValue());
                }
            }

            if (beforeDestroyFn != null) {
                beforeDestroyFn.accept(proc);
            }
        } finally {
            proc.destroy();
        }

        return procOutput;
    }

    public String startNode() throws IOException, InterruptedException {
        return startNode(null);
    }
}
