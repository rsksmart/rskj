package co.rsk.cli;

import co.rsk.config.NodeCliFlags;
import co.rsk.config.NodeCliOptions;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CliArgsTest {

    @Test
    void parseArgsCorrectly() {
        String[] args = new String[]{"--regtest", "-Xdatabase.dir=/home/rsk/data"};

        RskCli rskCli = new RskCli();
        rskCli.load(args);
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
        Assertions.assertThrows(IllegalArgumentException.class, () -> rskCli.load(args));
    }
}
