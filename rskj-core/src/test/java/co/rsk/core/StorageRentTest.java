package co.rsk.core;

import co.rsk.config.TestSystemProperties;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import org.ethereum.core.*;
import org.ethereum.util.ByteUtil;
import org.junit.Assert;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.io.FileNotFoundException;
import java.math.BigInteger;


public class StorageRentTest {
    private static final TestSystemProperties config = new TestSystemProperties();

    @Test
    public void testStorageRentEncode() {
        byte[] nonce = BigInteger.valueOf(9).toByteArray();
        byte[] gasPrice = BigInteger.valueOf(20000000000L).toByteArray();
        byte[] rentGasLimit = BigInteger.valueOf(21000).toByteArray();
        byte[] gas = BigInteger.valueOf(21000).toByteArray();
        byte[] to = Hex.decode("3535353535353535353535353535353535353535");
        byte[] value = BigInteger.valueOf(1000000000000000000L).toByteArray();
        byte[] data = new byte[0];
        byte chainId = 1;
        Transaction tx = new Transaction(nonce, gasPrice, gas, to, value, data, chainId, rentGasLimit);
        byte[] encoded = tx.getEncodedRaw();
        byte[] hash = tx.getRawHash().getBytes();
        String strenc = Hex.toHexString(encoded);
        Assert.assertEquals("ef098504a817c800825208943535353535353535353535353535353535353535880de0b6b3a764000080018080825208", strenc);
        String strhash = Hex.toHexString(hash);
        Assert.assertEquals("e6620f7109142d2756a1453e9ebb59fa8bcf501454145b2df1f09e4824d432d2", strhash);
        System.out.println(strenc);
        System.out.println(strhash);
    }

    @Test
    public void createAContractAndDontPayStorage() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/contractStorageTest.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);
    }

    @Test
    public void callAContractAndPayStorage() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/timePassageAndRunContract.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);
    }


    @Test
    public void contractCallAnotherContract() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/contractA.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);
    }

    @Test
    public void contractEmptyTheStorageAndPayOnce() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/emptyTheStorage.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);
    }

    @Test
    public void contractEmptyTheStorageButWithoutGas() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/timePassageAndRunContractWithoutGas.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);
    }

    @Test
    public void contractEmptyTheStorageButWithoutRentGas() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/timePassageAndRunContractWithoutRentGas.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);
    }

    @Test
    public void extCodeCopyAndSizePayRentGasOnce() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/extCodeCopyandExtCodeSizeTest.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);
    }

    @Test
    public void balancePayRentGas() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/balanceOpCodePayStorage.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);
    }

    @Test
    public void unknownAddressesDontPayRentGas() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/aContractWithUnknownAddress.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);
    }

    @Test
    public void aContractCallCall() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/aContractCallCall.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);
    }

    @Test
    public void extCodeSizePayStorage() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/extCodeSize.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);
    }



    @Test
    public void extCodeCopyPayStorage() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/extCodeCopy.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);
    }


    @Test
    public void codeReplaceAppearModified() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/codeReplace.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);
    }


    @Test
    public void suicideAContractPayStorage() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/contractSuicide.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);
    }

    @Test
    public void transferZeroValue() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/transferZeroValue.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);
    }

    @Test
    public void transferZeroValueByContract() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/sendFromAContractZeroValue.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);
    }



}
