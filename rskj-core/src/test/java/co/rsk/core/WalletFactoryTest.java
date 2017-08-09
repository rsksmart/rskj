package co.rsk.core;

import org.ethereum.datasource.LevelDbDataSource;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Random;

/**
 * Created by ajlopez on 09/08/2017.
 */
public class WalletFactoryTest {
    private static Random random = new Random();

    @Test
    public void convertBytes() {
        byte[] value1 = generateBytes();
        byte[] value2 = generateBytes();
        random.nextBytes(value1);
        random.nextBytes(value2);

        byte[] encryptedValues = serializeValuesToOldFormat(value1, value2);

        Values values = convertAndDeserializeValues(encryptedValues);

        Assert.assertArrayEquals(value1, values.value1);
        Assert.assertArrayEquals(value2, values.value2);
    }

    @Test
    public void convertWallets() {
        byte[] key1 = generateBytes();
        byte[] value11 = generateBytes();
        byte[] value12 = generateBytes();

        byte[] key2 = generateBytes();
        byte[] value21 = generateBytes();
        byte[] value22 = generateBytes();

        LevelDbDataSource ds = new LevelDbDataSource("oldwallet1");
        ds.init();

        ds.put(key1, serializeValuesToOldFormat(value11, value12));
        ds.put(key2, serializeValuesToOldFormat(value21, value22));

        ds.close();

        WalletFactory.convertWallet("oldwallet1", "newwallet1");

        LevelDbDataSource newds = new LevelDbDataSource("newwallet1");
        newds.init();

        byte[] value1 = newds.get(key1);
        RLPList rlpList1 = (RLPList) RLP.decode2(value1).get(0);

        Assert.assertArrayEquals(value11, rlpList1.get(0).getRLPData());
        Assert.assertArrayEquals(value12, rlpList1.get(1).getRLPData());

        byte[] value2 = newds.get(key2);
        RLPList rlpList2 = (RLPList) RLP.decode2(value2).get(0);

        Assert.assertArrayEquals(value21, rlpList2.get(0).getRLPData());
        Assert.assertArrayEquals(value22, rlpList2.get(1).getRLPData());

        newds.close();
    }

    private byte[] serializeValuesToOldFormat(byte[] value1, byte[] value2) {
        try {
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            ObjectOutputStream byteStream = new ObjectOutputStream(result);

            ArrayList<byte[]> bytes = new ArrayList<>();
            bytes.add(value1);
            bytes.add(value2);
            byteStream.writeObject(bytes);

            return result.toByteArray();
        } catch (IOException e) {
            //How is this even possible ???
            throw new IllegalStateException(e);
        }
    }

    private Values convertAndDeserializeValues(byte[] encryptedValues) {
        byte[] valuesBytes = WalletFactory.convertBytes(encryptedValues);
        RLPList rlpList = (RLPList) RLP.decode2(valuesBytes).get(0);
        Values values = new Values();
        values.value1 = rlpList.get(0).getRLPData();
        values.value2 = rlpList.get(1).getRLPData();
        return values;
    }

    private static class Values {
        public byte[] value1;
        public byte[] value2;
    }

    private static byte[] generateBytes() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return bytes;
    }
}
