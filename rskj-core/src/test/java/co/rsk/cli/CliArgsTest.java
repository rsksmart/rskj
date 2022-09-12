package co.rsk.cli;

import co.rsk.config.NodeCliFlags;
import co.rsk.config.NodeCliOptions;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CliArgsTest {

    @Test
    void parseArgsCorrectly() {
        String[] args = new String[]{"--regtest", "-Xdatabase.dir=/home/rsk/data"};

        CliArgs<NodeCliOptions, NodeCliFlags> cliArgs = new CliArgs.Parser<>(
                NodeCliOptions.class,
                NodeCliFlags.class
        ).parse(args);

        Assertions.assertEquals(1, cliArgs.getFlags().size());
        Assertions.assertEquals(1, cliArgs.getParamValueMap().size());
        Assertions.assertEquals("/home/rsk/data", cliArgs.getParamValueMap().get("database.dir"));
        Assertions.assertEquals("regtest", cliArgs.getFlags().iterator().next().getName());
    }

    @Test
    void parseArgsIncorrectlyWithBadXArg() {
        String[] args = new String[]{"--regtest", "-Xdatabase.dir"};
        CliArgs.Parser<NodeCliOptions, NodeCliFlags> parser = new CliArgs.Parser<>(
                NodeCliOptions.class,
                NodeCliFlags.class
        );

        Assertions.assertThrows(IllegalArgumentException.class, () -> parser.parse(args));
    }
}
