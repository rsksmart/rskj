package co.rsk.util.cli;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class NodeIntegrationTestCommandLine extends RskjCommandLineBase {
    private final String modeArg;
    private final String rskConfFilePath;
    private int timeout = 0;

    public NodeIntegrationTestCommandLine(String rskConfFilePath, String modeArg) {
        super("co.rsk.Start", new String[]{}, new String[]{});
        this.rskConfFilePath = rskConfFilePath;
        this.modeArg = modeArg;
    }

    public NodeIntegrationTestCommandLine(String rskConfFilePath, String modeArg, int timeout) {
        this(rskConfFilePath, modeArg);
        this.timeout = timeout;
    }

    private void setUp() {
        String projectPath = System.getProperty("user.dir");
        String integrationTestResourcesPath = String.format("%s/src/integrationTest/resources", projectPath);
        String logbackXmlFile = String.format("%s/logback.xml", integrationTestResourcesPath);
        arguments = new String[]{
                this.modeArg,
        };
        parameters = new String[]{
                String.format("-Dlogback.configurationFile=%s", logbackXmlFile),
                String.format("-Drsk.conf.file=%s", rskConfFilePath)
        };
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
