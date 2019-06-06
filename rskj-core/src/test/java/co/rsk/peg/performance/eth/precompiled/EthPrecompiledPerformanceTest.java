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

package co.rsk.peg.performance.eth.precompiled;

import co.rsk.peg.performance.PrecompiledContractPerformanceTest;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        IdentityPerformanceTestCase.class,
        Sha256PerformanceTestCase.class,
        Ripempd160PerformanceTestCase.class,
        ECRecoverPerformanceTestCase.class,
        BigIntegerModexpPerformanceTestCase.class
})
@Ignore
public class EthPrecompiledPerformanceTest extends PrecompiledContractPerformanceTest {
}
