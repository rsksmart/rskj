package co.rsk.cli;

import co.rsk.config.NodeCliFlags;
import co.rsk.config.NodeCliOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RskCliTest {

    @Test
    void shouldReturnTrueIfHelpRequested() {
        RskCli rskCli = new RskCli();
        String[] args = {"--help"};
        rskCli.load(args);
        assertTrue(rskCli.getCliArgs().getFlags().contains(NodeCliFlags.HELP));
    }

    @Test
    void shouldReturnTrueIfVersionRequested() {
        RskCli rskCli = new RskCli();
        String[] args = {"--version"};
        rskCli.load(args);
        assertTrue(rskCli.getCliArgs().getFlags().contains(NodeCliFlags.VERSION));
    }
    @Test
    void argsAreParsedCorrectly() {
        RskCli rskCli = new RskCli();
        String[] args = {"--main", "--skip-java-check", "--print-system-info", "--verify-config", "--reset"};
        rskCli.load(args);
        CliArgs<NodeCliOptions, NodeCliFlags> parsedArgs = rskCli.getCliArgs();
        assertEquals(5, parsedArgs.getFlags().size());
    }

}
