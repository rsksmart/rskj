package co.rsk.cli;

import co.rsk.config.NodeCliFlags;
import co.rsk.config.NodeCliOptions;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class CliArgsTest {

    @Test
    void parseArgsCorrectly() {
        String[] args = new String[]{"--regtest", "-Xdatabase.dir=/home/rsk/data"};

        RskCli rskCli = new RskCli();
        CommandLine commandLine = new CommandLine(rskCli);
        commandLine.parseArgs(args);
        CliArgs<NodeCliOptions, NodeCliFlags> cliArgs = rskCli.getCliArgs();

        Assertions.assertEquals(1, cliArgs.getFlags().size());
        Assertions.assertEquals(1, cliArgs.getParamValueMap().size());
        Assertions.assertEquals("/home/rsk/data", cliArgs.getParamValueMap().get("database.dir"));
        Assertions.assertEquals("regtest", cliArgs.getFlags().iterator().next().getName());
    }

    @Test
    void parseArgsIncorrectlyWithBadXArg() {
        String[] args = new String[]{"--regtest", "-Xdatabase.dir"};

        RskCli rskCli = new RskCli();
        CommandLine commandLine = new CommandLine(rskCli);
        // preguntar si hay que tirar esta excepcion exacta o esta bien la de PicoCli
        Assertions.assertThrows(IllegalArgumentException.class, () -> commandLine.parseArgs(args));
    }
}
