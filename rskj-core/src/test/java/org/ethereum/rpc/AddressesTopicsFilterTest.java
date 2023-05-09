/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

package org.ethereum.rpc;

import co.rsk.core.RskAddress;
import co.rsk.test.builders.AccountBuilder;
import org.ethereum.TestUtils;
import org.ethereum.core.Account;
import org.ethereum.core.Bloom;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Created by ajlopez on 18/01/2018.
 */
class AddressesTopicsFilterTest {
    @Test
    void matchAddress() {
        Account account = new AccountBuilder().name("account").build();
        RskAddress address = account.getAddress();

        AddressesTopicsFilter filter = new AddressesTopicsFilter(new RskAddress[] { address }, null);

        Assertions.assertTrue(filter.matchesContractAddress(address));
        Assertions.assertFalse(filter.matchesContractAddress(RskAddress.nullAddress()));
    }

    @Test
    void matchEmptyBloomWithAllFilter() {
        AddressesTopicsFilter filter = new AddressesTopicsFilter(new RskAddress[0], null);

        Assertions.assertTrue(filter.matchBloom(new Bloom()));
    }

    @Test
    void noMatchEmptyBloomWithFilterWithAccount() {
        Account account = new AccountBuilder().name("account").build();
        RskAddress address = account.getAddress();

        AddressesTopicsFilter filter = new AddressesTopicsFilter(new RskAddress[] { address }, null);

        Assertions.assertFalse(filter.matchBloom(new Bloom()));
    }

    @Test
    void noMatchEmptyBloomWithFilterWithTopic() {
        Topic topic = createTopic();

        AddressesTopicsFilter filter = new AddressesTopicsFilter(new RskAddress[0], new Topic[][] {{ topic }});

        Assertions.assertFalse(filter.matchBloom(new Bloom()));
    }

    @Test
    void matchAllBloomWithFilterWithTopic() {
        Topic topic = createTopic();

        AddressesTopicsFilter filter = new AddressesTopicsFilter(new RskAddress[0], new Topic[][] {{ topic }});

        Assertions.assertTrue(filter.matchBloom(getAllBloom()));
    }

    @Test
    void matchAllBloomWithFilterWithAccount() {
        Account account = new AccountBuilder().name("account").build();
        RskAddress address = account.getAddress();

        AddressesTopicsFilter filter = new AddressesTopicsFilter(new RskAddress[] { address }, null);

        Assertions.assertTrue(filter.matchBloom(getAllBloom()));
    }

    private static Topic createTopic() {
        byte[] bytes = TestUtils.generateBytes(AddressesTopicsFilterTest.class,"topic",32);
        return new Topic(bytes);
    }

    private static Bloom getAllBloom() {
        byte[] bytes = new byte[256];

        for (int k = 0; k < bytes.length; k++)
            bytes[k] = (byte)0xff;

        return new Bloom(bytes);
    }
}
