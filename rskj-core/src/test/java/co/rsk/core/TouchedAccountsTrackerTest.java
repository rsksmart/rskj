/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.core;

import org.ethereum.core.Repository;
import org.ethereum.util.RskTestFactory;
import org.ethereum.vm.DataWord;
import org.junit.Assert;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;

public class TouchedAccountsTrackerTest {
    @Test
    public void clearsEmptyAccount() {
        RskTestFactory factory = new RskTestFactory();
        Repository repository = factory.getRepository();
        byte[] testAddr = Hex.decode("0000000000000000000000001230000004560000007890000000000001000006");
        repository.addBalance(testAddr, BigInteger.ZERO);

        Assert.assertNotNull(repository.getAccountState(testAddr));

        TouchedAccountsTracker tracker = new TouchedAccountsTracker();
        tracker.add(new DataWord(testAddr));
        tracker.clearEmptyAccountsFrom(repository);

        Assert.assertNull(repository.getAccountState(testAddr));
    }

    @Test
    public void doesntClearEmptyAccountWithCode() {
        RskTestFactory factory = new RskTestFactory();
        Repository repository = factory.getRepository();
        byte[] testAddr = Hex.decode("0000000000000000000000001230000004560000007890000000000001000006");
        repository.addBalance(testAddr, BigInteger.ZERO);
        repository.saveCode(testAddr, new byte[]{ 0x01, 0x02 });

        Assert.assertNotNull(repository.getAccountState(testAddr));

        TouchedAccountsTracker tracker = new TouchedAccountsTracker();
        tracker.add(new DataWord(testAddr));
        tracker.clearEmptyAccountsFrom(repository);

        Assert.assertNotNull(repository.getAccountState(testAddr));
    }

    @Test
    public void doesntClearEmptyAccountWithNonZeroNonce() {
        RskTestFactory factory = new RskTestFactory();
        Repository repository = factory.getRepository();
        byte[] testAddr = Hex.decode("0000000000000000000000001230000004560000007890000000000001000006");
        repository.addBalance(testAddr, BigInteger.ZERO);
        repository.increaseNonce(testAddr);

        Assert.assertNotNull(repository.getAccountState(testAddr));

        TouchedAccountsTracker tracker = new TouchedAccountsTracker();
        tracker.add(new DataWord(testAddr));
        tracker.clearEmptyAccountsFrom(repository);

        Assert.assertNotNull(repository.getAccountState(testAddr));
    }

    @Test
    public void doesntClearAccountWithBalance() {
        RskTestFactory factory = new RskTestFactory();
        Repository repository = factory.getRepository();
        byte[] testAddr = Hex.decode("0000000000000000000000001230000004560000007890000000000001000006");
        repository.addBalance(testAddr, BigInteger.TEN);

        Assert.assertNotNull(repository.getAccountState(testAddr));

        TouchedAccountsTracker tracker = new TouchedAccountsTracker();
        tracker.add(new DataWord(testAddr));
        tracker.clearEmptyAccountsFrom(repository);

        Assert.assertNotNull(repository.getAccountState(testAddr));
    }

    @Test
    public void clearsEmptyAccountsFromMergedTrackers() {
        RskTestFactory factory = new RskTestFactory();
        Repository repository = factory.getRepository();
        byte[] testAddr1 = Hex.decode("0000000000000000000000001230000004560000007890000000000001000006");
        repository.addBalance(testAddr1, BigInteger.ZERO);
        byte[] testAddr2 = Hex.decode("0000000000000000000000001230000004560000007890000000000001234567");
        repository.addBalance(testAddr2, BigInteger.ZERO);

        Assert.assertNotNull(repository.getAccountState(testAddr1));
        Assert.assertNotNull(repository.getAccountState(testAddr2));

        TouchedAccountsTracker tracker1 = new TouchedAccountsTracker();
        tracker1.add(new DataWord(testAddr1));
        TouchedAccountsTracker tracker2 = new TouchedAccountsTracker();
        tracker2.add(new DataWord(testAddr2));

        tracker1.mergeFrom(tracker2);
        tracker1.clearEmptyAccountsFrom(repository);

        Assert.assertNull(repository.getAccountState(testAddr1));
        Assert.assertNull(repository.getAccountState(testAddr2));
    }
}
