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
import org.ethereum.config.CommonConfig;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.db.MutableRepository;
import org.ethereum.validator.ParentBlockHeaderValidator;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * Created by ajlopez on 10/04/2017.
 */
public class CommonConfigTest {

    private final TestSystemProperties config = new TestSystemProperties();

    @Test
    public void createRepositoryUsingNewRepository() {
        CommonConfig config = new CommonConfig();

        Repository repository = config.repository(this.config);

        Assert.assertNotNull(repository);
        Assert.assertTrue(repository instanceof MutableRepository);
    }

    @Test
    public void createTransactionPoolTransactions() {
        CommonConfig config = new CommonConfig();

        List<Transaction> result = config.transactionPoolTransactions();

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void createParentHeaderValidator() {
        CommonConfig config = new CommonConfig();

        ParentBlockHeaderValidator result = config.parentHeaderValidator(this.config, new DifficultyCalculator(this.config));

        Assert.assertNotNull(result);
    }
}
