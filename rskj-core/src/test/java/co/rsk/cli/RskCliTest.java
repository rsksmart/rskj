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
        String[] mainnetArgs = {"--main", "--skip-java-check", "--print-system-info", "--verify-config", "--reset", "--import", "-rpccors=*", "-base-path=./test-db", "-Xdatabase.dir="};
        rskCli.load(mainnetArgs);
        CliArgs<NodeCliOptions, NodeCliFlags> parsedArgs = rskCli.getCliArgs();
        assertEquals(6, parsedArgs.getFlags().size());
        assertEquals(2, parsedArgs.getOptions().size());
        assertEquals(1, parsedArgs.getParamValueMap().size());

        assertEquals("*", parsedArgs.getOptions().get(NodeCliOptions.RPC_CORS));
        assertEquals("./test-db", parsedArgs.getOptions().get(NodeCliOptions.BASE_PATH));

        rskCli = new RskCli();
        String[] shortArgs = {"-r", "-i"};
        rskCli.load(shortArgs);
        parsedArgs = rskCli.getCliArgs();
        assertEquals(2, parsedArgs.getFlags().size());
        assertTrue(parsedArgs.getFlags().contains(NodeCliFlags.DB_RESET));
        assertTrue(parsedArgs.getFlags().contains(NodeCliFlags.DB_IMPORT));

        rskCli = new RskCli();
        String[] testnetArgs = {"--testnet", "--skip-java-check", "--print-system-info", "--verify-config", "--reset", "--import", "-rpccors=*", "-base-path=./test-db", "-Xdatabase.dir="};
        rskCli.load(testnetArgs);
        parsedArgs = rskCli.getCliArgs();
        assertEquals(6, parsedArgs.getFlags().size());
        assertEquals(2, parsedArgs.getOptions().size());
        assertEquals(1, parsedArgs.getParamValueMap().size());

        rskCli = new RskCli();
        String[] regtestArgs = {"--regtest", "--skip-java-check", "--print-system-info", "--verify-config", "--reset", "--import", "-rpccors=*", "-base-path=./test-db", "-Xdatabase.dir="};
        rskCli.load(regtestArgs);
        parsedArgs = rskCli.getCliArgs();
        assertEquals(6, parsedArgs.getFlags().size());
        assertEquals(2, parsedArgs.getOptions().size());
        assertEquals(1, parsedArgs.getParamValueMap().size());
    }

}
