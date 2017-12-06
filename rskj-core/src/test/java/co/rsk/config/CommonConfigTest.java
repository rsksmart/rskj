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

package co.rsk.config;

import co.rsk.core.DifficultyCalculator;
import co.rsk.db.RepositoryImpl;
import org.ethereum.config.CommonConfig;
import org.ethereum.core.PendingTransaction;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.validator.ParentBlockHeaderValidator;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Set;

/**
 * Created by ajlopez on 10/04/2017.
 */
public class CommonConfigTest {
    @Test
    public void createRepositoryUsingNewRepository() {
        CommonConfig config = new CommonConfig();

        Repository repository = config.repository();

        Assert.assertNotNull(repository);
        Assert.assertTrue(repository instanceof RepositoryImpl);
    }

    @Test
    public void createPendingStateTransactions() {
        CommonConfig config = new CommonConfig();

        List<Transaction> result = config.pendingStateTransactions();

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void createWireTransactions() {
        CommonConfig config = new CommonConfig();

        Set<PendingTransaction> result = config.wireTransactions();

        Assert.assertNotNull(result);
    }

    @Test
    public void createParentHeaderValidator() {
        CommonConfig config = new CommonConfig();

        ParentBlockHeaderValidator result = config.parentHeaderValidator(RskSystemProperties.CONFIG, new DifficultyCalculator(RskSystemProperties.CONFIG));

        Assert.assertNotNull(result);
    }
}
