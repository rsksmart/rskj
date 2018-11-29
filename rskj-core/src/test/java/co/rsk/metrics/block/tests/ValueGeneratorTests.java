package co.rsk.metrics.block.tests;

import co.rsk.metrics.block.ValueGenerator;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class ValueGeneratorTests {



    /**
     * Generates random values and stores them in csv files so they can be used later as input for deterministic
     * Test Blockchain generations
     * @throws IOException
     */
    @Test
    public void testDatasourceGeneration() throws IOException {
        int minorityCap = new Long(Math.round(Math.floor(TestContext.ACCOUNTS_TO_GENERATE*0.2))).intValue();
        int mayorityCap = TestContext.ACCOUNTS_TO_GENERATE - 1;

        ValueGenerator valueGenerator = new ValueGenerator(TestContext.DATASOURCE_DIR, TestContext.DATASOURCE_VALUES_TO_GENERATE,minorityCap, mayorityCap,TestContext.TRX_MAX_RND_AMOUNT);
        Assert.assertEquals(valueGenerator.getMayorityAccountsLength(), TestContext.DATASOURCE_VALUES_TO_GENERATE);
        Assert.assertEquals(valueGenerator.getMinorityAccountsLength(), TestContext.DATASOURCE_VALUES_TO_GENERATE);
        Assert.assertEquals(valueGenerator.getTokenContractsLength(), TestContext.DATASOURCE_VALUES_TO_GENERATE);
        Assert.assertEquals(valueGenerator.getTransferTypeLength(), TestContext.DATASOURCE_VALUES_TO_GENERATE);
        Assert.assertEquals(valueGenerator.getTrxAmountLength(), TestContext.DATASOURCE_VALUES_TO_GENERATE);
        Assert.assertEquals(valueGenerator.getCoinbaseLength(), TestContext.DATASOURCE_VALUES_TO_GENERATE);

        for(int i = 0; i< TestContext.DATASOURCE_VALUES_TO_GENERATE; i++){
            int value = valueGenerator.nextMayorityAccount().intValue();
            Assert.assertTrue(value <= mayorityCap);
            Assert.assertTrue(value > minorityCap);

            value = valueGenerator.nextMinorityAccount().intValue();
            Assert.assertTrue(value <= minorityCap);
            Assert.assertTrue(value >= 0);

            value = valueGenerator.nextTokenContract().intValue();
            Assert.assertTrue(value <= TestContext.contracts.length-1);
            Assert.assertTrue(value >= 0);

            value = valueGenerator.nextTrxAmount().intValue();
            Assert.assertTrue(value >= 1);
            Assert.assertTrue(value < TestContext.TRX_MAX_RND_AMOUNT);

            Boolean trxType = valueGenerator.nextTransferType();
            Assert.assertNotNull(trxType);

            value = valueGenerator.nextCoinbase();
            Assert.assertTrue(value >= 0);
            Assert.assertTrue(value < TestContext.DATASOURCE_COINBASES_TO_GENERATE);

        }

    }


    @Test public void testRandomValueGeneration() throws IOException {

        int minorityCap = new Long(Math.round(Math.floor(TestContext.ACCOUNTS_TO_GENERATE*0.2))).intValue();
        int mayorityCap = TestContext.ACCOUNTS_TO_GENERATE - 1;

        ValueGenerator valueGenerator = new ValueGenerator(minorityCap, mayorityCap,TestContext.TRX_MAX_RND_AMOUNT);

        for(int i = 0; i< TestContext.DATASOURCE_VALUES_TO_GENERATE; i++){
            int value = valueGenerator.nextMayorityAccount().intValue();
            Assert.assertTrue(value <mayorityCap);
            Assert.assertTrue(value >= minorityCap);

            value = valueGenerator.nextMinorityAccount().intValue();
            Assert.assertTrue(value <minorityCap);
            Assert.assertTrue(value >= 0);

            value = valueGenerator.nextTokenContract().intValue();
            Assert.assertTrue(value <= TestContext.contracts.length-1);
            Assert.assertTrue(value >= 0);

            value = valueGenerator.nextTrxAmount().intValue();
            Assert.assertTrue(value >= 1);
            Assert.assertTrue(value < TestContext.TRX_MAX_RND_AMOUNT);

            Boolean trxType = valueGenerator.nextTransferType();
            Assert.assertNotNull(trxType);

            value = valueGenerator.nextCoinbase();
            Assert.assertTrue(value >= 0);
            Assert.assertTrue(value < TestContext.DATASOURCE_COINBASES_TO_GENERATE);

        }
    }

}
