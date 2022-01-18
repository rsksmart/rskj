package co.rsk.cli;

import co.rsk.config.NodeCliFlags;
import co.rsk.config.NodeCliOptions;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

public class CliArgsTest {

    @Test
    public void parseArgsCorrectly() {
        String[] args = new String[]{"--regtest", "-Xdatabase.dir=/home/rsk/data"};

        CliArgs<NodeCliOptions, NodeCliFlags> cliArgs = new CliArgs.Parser<>(
                NodeCliOptions.class,
                NodeCliFlags.class
        ).parse(args);

        Assert.assertEquals(cliArgs.getFlags().size(), 1);
        Assert.assertEquals(cliArgs.getParamValueMap().size(), 1);
        Assert.assertEquals(((Map<String, String>)cliArgs.getParamValueMap().get("database")).get("dir"), "/home/rsk/data");
        Assert.assertEquals(cliArgs.getFlags().iterator().next().getName(), "regtest");
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseArgsIncorrectlyWithBadXArg() {
        String[] args = new String[]{"--regtest", "-Xdatabase.dir"};

        new CliArgs.Parser<>(
                NodeCliOptions.class,
                NodeCliFlags.class
        ).parse(args);
    }
}