package co.rsk.metrics.block.tests;

import co.rsk.config.TestSystemProperties;
import co.rsk.metrics.block.builder.AccountStatus;
import co.rsk.metrics.block.builder.GenesisBuilder;
import co.rsk.metrics.block.builder.GenesisInfo;
import co.rsk.metrics.block.builder.GenesisLoader;
import co.rsk.metrics.block.builder.InvalidGenesisFileException;
import org.bouncycastle.util.encoders.DecoderException;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.regtest.RegTestGenesisConfig;
import org.junit.Assert;
import org.junit.Test;

import java.io.*;
import java.math.BigInteger;

public class GenesisBuilderTests {


    @Test
    public void testTokenOwnerAddress() throws IOException, ClassNotFoundException, InvalidGenesisFileException {
        testGenerateGenesisFile();
        TestSystemProperties config = new TestSystemProperties();
        config.setBlockchainConfig(new RegTestGenesisConfig());
        config.setGenesisInfo(TestContext.GENESIS_FILE);

        GenesisLoader genesisLoader = GenesisLoader.newGenesisLoader(config,TestContext.GENESIS_FILE_ROOT);
        System.out.println("Tokens owner address:" + genesisLoader.getTokensOwner().getAddress());

    }
    @Test
    public void testGenerateGenesisFile() throws IOException, ClassNotFoundException {
        GenesisBuilder.generateGenesis(TestContext.ACCOUNTS_TO_GENERATE, TestContext.ACCOUNT_BALANCE, TestContext.GENESIS_FILE_ROOT);


        FileInputStream fis = new FileInputStream(TestContext.GENESIS_FILE_ROOT + "/genesis-info");
        ObjectInputStream ois = new ObjectInputStream(fis);
        GenesisInfo genesisInfo  = (GenesisInfo) ois.readObject();
        ois.close();
        fis.close();

        int i = 0;
        BigInteger nonce = TestContext.INITIAL_TRX_NONCE;
        for(AccountStatus accountStatus: genesisInfo.getRegularAccounts()){
            Assert.assertEquals("ACC_"+i, accountStatus.getAccountName());
            i++;
            Assert.assertEquals(40, accountStatus.getAddress().toCharArray().length);

            try{
                Hex.decode(accountStatus.getAddress());
            }
            catch (DecoderException e){
                Assert.fail("The account " + accountStatus.getAccountName()+ " has an incorrect address format :" + accountStatus.getAddress());
            }

            Assert.assertTrue(accountStatus.nextNonce().equals(nonce));
        }
        Assert.assertEquals(TestContext.ACCOUNTS_TO_GENERATE, i);

        i = 0;
        for(AccountStatus accountStatus: genesisInfo.getTokenContracts()){
            Assert.assertEquals(TestContext.contracts[i], accountStatus.getAccountName());
            Assert.assertEquals(40, accountStatus.getAddress().toCharArray().length);
            try{
                Hex.decode(accountStatus.getAddress());
            }
            catch (DecoderException e){
                Assert.fail("The account " + accountStatus.getAccountName()+ " has an incorrect address format :" + accountStatus.getAddress());
            }
            i++;
        }
        Assert.assertEquals(TestContext.contracts.length, i);

    }


    @Test
    public void testLoadGenesis() throws InvalidGenesisFileException {
        TestSystemProperties config = new TestSystemProperties();
        config.setBlockchainConfig(new RegTestGenesisConfig());
        config.setGenesisInfo(TestContext.GENESIS_FILE);
        GenesisLoader genesisLoader = GenesisLoader.newGenesisLoader(config,TestContext.GENESIS_FILE_ROOT);
        genesisLoader.loadGenesis();
    }

}
