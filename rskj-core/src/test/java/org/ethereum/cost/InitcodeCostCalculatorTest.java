package org.ethereum.cost;

import org.bouncycastle.util.BigIntegers;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.ImmutableTransaction;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.HashUtil;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import java.math.BigInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

class InitcodeCostCalculatorTest {

 @ParameterizedTest
 @MethodSource("initCodeCostInputArguments")
 void testTransactionInitCodeCostCalculation(long expectedCost, byte[] rawData) {
  // given
  ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
  Mockito.doReturn(true).when(activations).isActive(Mockito.eq(ConsensusRule.RSKIP438));
  // when
  long initCodeCost = InitcodeCostCalculator.getInstance().calculateCost(rawData.length, activations);

  // then
  assertEquals(expectedCost, initCodeCost);
 }

 private static Stream<Arguments> initCodeCostInputArguments() {
  return Stream.of(
          Arguments.of(2,  new byte[]{0, 1, 2, 3}),
          Arguments.of(4, Hex.decode("fd5fa123fd5fa123a31231000076890afd5fa123a31231000076890afd5fa123a31231000076890a")),
          Arguments.of(6, Hex.decode("fd5fa123a31231000076890abcfff41239123912323123fd5fa123afd5fa12fd5fa123a31231000076890afd5fa123a31231000076890a3a31231000076890a31231000076890a"))
  );
 }
}