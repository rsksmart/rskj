/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
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

package co.rsk.validators;

import org.ethereum.core.Block;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the evaluation contract of the parent-dependant composite.
 *
 * <p>The order the rules are given in is only worth anything because of what is asserted here: the composite
 * evaluates them in that order and returns on the first failure, so a block that a cheap header check can
 * reject never reaches the per-transaction rules behind it. If the composite ever evaluated every rule (or
 * evaluated them in an unspecified order), ordering the rules cheap-first in {@code RskContext} would stop
 * bounding what an invalid block costs, without anything else failing.
 */
class BlockParentCompositeRuleTest {

    private Block block;
    private Block parent;

    @BeforeEach
    void setUp() {
        block = mock(Block.class);
        when(block.getNumber()).thenReturn(42L);
        when(block.getPrintableHash()).thenReturn("abcdef");

        parent = mock(Block.class);
    }

    @Test
    void isValidWhenEveryRulePasses() {
        BlockParentDependantValidationRule first = rule(true);
        BlockParentDependantValidationRule second = rule(true);

        assertTrue(new BlockParentCompositeRule(first, second).isValid(block, parent));

        verify(first).isValid(block, parent);
        verify(second).isValid(block, parent);
    }

    @Test
    void stopsAtTheFirstFailingRuleAndNeverEvaluatesTheOnesBehindIt() {
        // this is what makes the cheap-rules-first ordering pay off: work behind the failure is not done
        BlockParentDependantValidationRule cheapPassing = rule(true);
        BlockParentDependantValidationRule cheapFailing = rule(false);
        BlockParentDependantValidationRule expensive = rule(true);

        assertFalse(new BlockParentCompositeRule(cheapPassing, cheapFailing, expensive).isValid(block, parent));

        verify(cheapPassing).isValid(block, parent);
        verify(cheapFailing).isValid(block, parent);
        verify(expensive, never()).isValid(block, parent);
    }

    @Test
    void evaluatesRulesInTheOrderTheyWereGiven() {
        BlockParentDependantValidationRule first = rule(true);
        BlockParentDependantValidationRule second = rule(true);
        BlockParentDependantValidationRule third = rule(true);

        assertTrue(new BlockParentCompositeRule(first, second, third).isValid(block, parent));

        InOrder evaluation = inOrder(first, second, third);
        evaluation.verify(first).isValid(block, parent);
        evaluation.verify(second).isValid(block, parent);
        evaluation.verify(third).isValid(block, parent);
    }

    @Test
    void skipsNullRulesWithoutFailingTheBlock() {
        BlockParentDependantValidationRule passing = rule(true);

        assertTrue(new BlockParentCompositeRule(passing, null).isValid(block, parent));

        verify(passing).isValid(block, parent);
    }

    @Test
    void isValidWhenThereAreNoRulesToEvaluate() {
        assertTrue(new BlockParentCompositeRule().isValid(block, parent));
    }

    private static BlockParentDependantValidationRule rule(boolean valid) {
        BlockParentDependantValidationRule rule = mock(BlockParentDependantValidationRule.class);
        when(rule.isValid(any(), any())).thenReturn(valid);

        return rule;
    }
}
