package co.rsk.datasource;

import org.ethereum.datasource.LevelDbDataSource;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created by ajlopez on 09/08/2017.
 */
public class LevelDbDataSourceTest {
    @Test
    public void testDoesNotExists() {
        LevelDbDataSource dataSource = new LevelDbDataSource("unknown");
        Assert.assertFalse(dataSource.exists());
    }

    @Test
    public void testCreateAndExists() {
        LevelDbDataSource dataSource = new LevelDbDataSource("created");
        dataSource.init();
        Assert.assertTrue(dataSource.exists());
        dataSource.close();
    }
}
