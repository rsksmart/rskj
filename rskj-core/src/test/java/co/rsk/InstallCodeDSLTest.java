package co.rsk;

import co.rsk.db.RepositorySnapshot;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import org.ethereum.core.Account;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.TransactionReceipt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InstallCodeDSLTest {
    private World world;
    private WorldDslProcessor processor;

    @BeforeEach
    void setup() {
        this.world = new World();
        this.processor = new WorldDslProcessor(world);

    }

    /**
     * account1 Installs code in its own EOA (via InstallCode), and then acc2 calls a contract
     * that invokes a method on acc1 and checks for success.
     * */
    @Test
    public void installCodeViaPrecAndCallEOAViaContractCall() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/installcode/installcode.txt");
        processor.processCommands(parser);

        Account acc1 = world.getAccountByName("acc1");
        acc1.getAddress();

        RepositorySnapshot snapshot = world.getRepositoryLocator()
                .findSnapshotAt(world.getBlockByName("b02").getHeader())
                .get();

        byte[] code = snapshot.getCode(acc1.getAddress());
        assertNotNull(code);
        assertTrue(code.length  > 0);
    }
}
