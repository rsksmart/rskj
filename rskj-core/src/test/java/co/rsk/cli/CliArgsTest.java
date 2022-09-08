package co.rsk.cli;

import co.rsk.config.NodeCliFlags;
import co.rsk.config.NodeCliOptions;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CliArgsTest {

    @Test
    public void parseArgsCorrectly() {
        String[] args = new String[]{"--regtest", "-Xdatabase.dir=/home/rsk/data"};

        CliArgs<NodeCliOptions, NodeCliFlags> cliArgs = new CliArgs.Parser<>(
                NodeCliOptions.class,
                NodeCliFlags.class
        ).parse(args);

        Assertions.assertEquals(cliArgs.getFlags().size(), 1);
        Assertions.assertEquals(cliArgs.getParamValueMap().size(), 1);
        Assertions.assertEquals(cliArgs.getParamValueMap().get("database.dir"), "/home/rsk/data");
        Assertions.assertEquals(cliArgs.getFlags().iterator().next().getName(), "regtest");
    }

    @Test
    public void parseArgsIncorrectlyWithBadXArg() {
        String[] args = new String[]{"--regtest", "-Xdatabase.dir"};
        CliArgs.Parser<NodeCliOptions, NodeCliFlags> parser = new CliArgs.Parser<>(
                NodeCliOptions.class,
                NodeCliFlags.class
        );

        Assertions.assertThrows(IllegalArgumentException.class, () -> parser.parse(args));
    }
}
