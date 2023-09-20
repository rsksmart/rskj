/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package co.rsk.pcc.bto;

import co.rsk.bridge.performance.PrecompiledContractPerformanceTest;
import org.junit.jupiter.api.Disabled;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
        ToBase58CheckPerformanceTestCase.class,
        DeriveExtendedPublicKeyPerformanceTestCase.class,
        ExtractPublicKeyFromExtendedPublicKeyPerformanceTestCase.class,
        GetMultisigScriptHashPerformanceTestCase.class
})
@Disabled("manual performance tests")
@SuppressWarnings({"squid:S2187"}) // used from another class
class HDWalletUtilsPerformanceTest extends PrecompiledContractPerformanceTest {
}
