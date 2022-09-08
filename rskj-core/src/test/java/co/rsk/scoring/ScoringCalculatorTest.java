/*
 * This file is part of RskJ
 * Copyright (C) 2022 RSK Labs Ltd.
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

package co.rsk.scoring;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Created by ajlopez on 30/06/2017.
 */
public class ScoringCalculatorTest {
    @Test
    public void emptyScoringHasGoodReputation() {
        ScoringCalculator calculator = new ScoringCalculator();
        PeerScoring scoring = new PeerScoring("id1");

        Assertions.assertTrue(calculator.hasGoodScore(scoring));
    }

    @Test
    public void scoringWithOneValidBlockHasGoodReputation() {
        ScoringCalculator calculator = new ScoringCalculator();
        PeerScoring scoring = new PeerScoring("id1");
        scoring.updateScoring(EventType.VALID_BLOCK);

        Assertions.assertTrue(calculator.hasGoodScore(scoring));
    }

    @Test
    public void scoringWithOneValidTransactionHasGoodReputation() {
        ScoringCalculator calculator = new ScoringCalculator();
        PeerScoring scoring = new PeerScoring("id1");
        scoring.updateScoring(EventType.VALID_TRANSACTION);

        Assertions.assertTrue(calculator.hasGoodScore(scoring));
    }

    @Test
    public void scoringWithOneInvalidBlockHasBadReputation() {
        ScoringCalculator calculator = new ScoringCalculator();
        PeerScoring scoring = new PeerScoring("id1");
        scoring.updateScoring(EventType.INVALID_BLOCK);

        Assertions.assertFalse(calculator.hasGoodScore(scoring));
    }

    @Test
    public void scoringWithOneInvalidTransactionHasNoBadReputation() {
        ScoringCalculator calculator = new ScoringCalculator();
        PeerScoring scoring = new PeerScoring("id1");
        scoring.updateScoring(EventType.INVALID_TRANSACTION);

        Assertions.assertTrue(calculator.hasGoodScore(scoring));
    }
}
