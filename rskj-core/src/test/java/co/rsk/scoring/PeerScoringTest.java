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

import java.util.concurrent.TimeUnit;

/**
 * Created by ajlopez on 27/06/2017.
 */
class PeerScoringTest {
    @Test
    void newStatusHasCounterInZero() {
        PeerScoring scoring = new PeerScoring("id1");

        Assertions.assertEquals(0, scoring.getEventCounter(EventType.INVALID_BLOCK));
        Assertions.assertEquals(0, scoring.getEventCounter(EventType.INVALID_TRANSACTION));
        Assertions.assertEquals(0, scoring.getTotalEventCounter());
    }

    @Test
    void newStatusHasGoodReputation() {
        PeerScoring scoring = new PeerScoring("id1");

        Assertions.assertTrue(scoring.refreshReputationAndPunishment());
    }

    @Test
    void getInformationFromNewScoring() {
        PeerScoring scoring = new PeerScoring("id1");
        PeerScoringInformation info = PeerScoringInformation.buildByScoring(scoring, "nodeid", "node");

        Assertions.assertTrue(info.getGoodReputation());
        Assertions.assertEquals(0, info.getValidBlocks());
        Assertions.assertEquals(0, info.getInvalidBlocks());
        Assertions.assertEquals(0, info.getValidTransactions());
        Assertions.assertEquals(0, info.getInvalidTransactions());
        Assertions.assertEquals(0, info.getScore());
        Assertions.assertEquals(0, info.getSuccessfulHandshakes());
        Assertions.assertEquals(0, info.getFailedHandshakes());
        Assertions.assertEquals(0, info.getRepeatedMessages());
        Assertions.assertEquals(0, info.getInvalidNetworks());
        Assertions.assertEquals("nodeid", info.getId());
        Assertions.assertEquals("node", info.getType());
    }

    @Test
    void getInformationFromScoringWithTwoValidBlocks() {
        PeerScoring scoring = new PeerScoring("id1");

        scoring.updateScoring(EventType.VALID_BLOCK);
        scoring.updateScoring(EventType.VALID_BLOCK);

        PeerScoringInformation info = PeerScoringInformation.buildByScoring(scoring, "nodeid", "node");

        Assertions.assertTrue(info.getGoodReputation());
        Assertions.assertEquals(2, info.getValidBlocks());
        Assertions.assertEquals(0, info.getInvalidBlocks());
        Assertions.assertEquals(0, info.getValidTransactions());
        Assertions.assertEquals(0, info.getInvalidTransactions());
        Assertions.assertTrue(info.getScore() > 0);
    }

    @Test
    void getInformationFromScoringWithThreeInvalidBlocks() {
        PeerScoring scoring = new PeerScoring("id1");

        scoring.updateScoring(EventType.INVALID_BLOCK);
        scoring.updateScoring(EventType.INVALID_BLOCK);
        scoring.updateScoring(EventType.INVALID_BLOCK);

        PeerScoringInformation info = PeerScoringInformation.buildByScoring(scoring, "node", "nodeid");

        Assertions.assertTrue(info.getGoodReputation());
        Assertions.assertEquals(0, info.getValidBlocks());
        Assertions.assertEquals(3, info.getInvalidBlocks());
        Assertions.assertEquals(0, info.getValidTransactions());
        Assertions.assertEquals(0, info.getInvalidTransactions());
        Assertions.assertTrue(info.getScore() < 0);
    }

    @Test
    void getInformationFromScoringWithTwoValidTransactions() {
        PeerScoring scoring = new PeerScoring("id1");

        scoring.updateScoring(EventType.VALID_TRANSACTION);
        scoring.updateScoring(EventType.VALID_TRANSACTION);

        PeerScoringInformation info = PeerScoringInformation.buildByScoring(scoring, "nodeid", "node");

        Assertions.assertTrue(info.getGoodReputation());
        Assertions.assertEquals(0, info.getValidBlocks());
        Assertions.assertEquals(0, info.getInvalidBlocks());
        Assertions.assertEquals(2, info.getValidTransactions());
        Assertions.assertEquals(0, info.getInvalidTransactions());
        Assertions.assertTrue(info.getScore() > 0);
    }

    @Test
    void getInformationFromScoringWithThreeInvalidTransactions() {
        PeerScoring scoring = new PeerScoring("id1");

        scoring.updateScoring(EventType.INVALID_TRANSACTION);
        scoring.updateScoring(EventType.INVALID_TRANSACTION);
        scoring.updateScoring(EventType.INVALID_TRANSACTION);

        PeerScoringInformation info = PeerScoringInformation.buildByScoring(scoring, "nodeid", "node");

        Assertions.assertTrue(info.getGoodReputation());
        Assertions.assertEquals(0, info.getValidBlocks());
        Assertions.assertEquals(0, info.getInvalidBlocks());
        Assertions.assertEquals(0, info.getValidTransactions());
        Assertions.assertEquals(3, info.getInvalidTransactions());
        Assertions.assertTrue(info.getScore() < 0);
    }

    @Test
    void newStatusHasNoTimeLostGoodReputation() {
        PeerScoring scoring = new PeerScoring("id1");

        Assertions.assertEquals(0, scoring.getTimeLostGoodReputation());
    }

    @Test
    void recordEvent() {
        PeerScoring scoring = new PeerScoring("id1");

        scoring.updateScoring(EventType.INVALID_BLOCK);

        Assertions.assertEquals(1, scoring.getEventCounter(EventType.INVALID_BLOCK));
        Assertions.assertEquals(0, scoring.getEventCounter(EventType.INVALID_TRANSACTION));
        Assertions.assertEquals(1, scoring.getTotalEventCounter());
    }

    @Test
    void recordManyEvent() {
        PeerScoring scoring = new PeerScoring("id1");

        scoring.updateScoring(EventType.INVALID_BLOCK);
        scoring.updateScoring(EventType.INVALID_BLOCK);
        scoring.updateScoring(EventType.INVALID_BLOCK);

        Assertions.assertEquals(3, scoring.getEventCounter(EventType.INVALID_BLOCK));
        Assertions.assertEquals(0, scoring.getEventCounter(EventType.INVALID_TRANSACTION));
        Assertions.assertEquals(3, scoring.getTotalEventCounter());
    }

    @Test
    void recordManyEventOfDifferentType() {
        PeerScoring scoring = new PeerScoring("id1");

        scoring.updateScoring(EventType.INVALID_BLOCK);
        scoring.updateScoring(EventType.INVALID_BLOCK);
        scoring.updateScoring(EventType.INVALID_BLOCK);
        scoring.updateScoring(EventType.INVALID_TRANSACTION);
        scoring.updateScoring(EventType.INVALID_TRANSACTION);

        Assertions.assertEquals(3, scoring.getEventCounter(EventType.INVALID_BLOCK));
        Assertions.assertEquals(2, scoring.getEventCounter(EventType.INVALID_TRANSACTION));
        Assertions.assertEquals(5, scoring.getTotalEventCounter());
    }

    @Test
    void getZeroScoreWhenEmpty() {
        PeerScoring scoring = new PeerScoring("id1");

        Assertions.assertEquals(0, scoring.getScore());
    }

    @Test
    void getPositiveScoreWhenValidBlock() {
        PeerScoring scoring = new PeerScoring("id1");

        scoring.updateScoring(EventType.VALID_BLOCK);

        Assertions.assertTrue(scoring.getScore() > 0);
    }

    @Test
    void getNegativeScoreWhenInvalidBlock() {
        PeerScoring scoring = new PeerScoring("id1");

        scoring.updateScoring(EventType.INVALID_BLOCK);

        Assertions.assertTrue(scoring.getScore() < 0);
    }

    @Test
    void getPositiveScoreWhenValidTransaction() {
        PeerScoring scoring = new PeerScoring("id1");

        scoring.updateScoring(EventType.VALID_TRANSACTION);

        Assertions.assertTrue(scoring.getScore() > 0);
    }

    @Test
    void getNegativeScoreWhenInvalidTransaction() {
        PeerScoring scoring = new PeerScoring("id1");

        scoring.updateScoring(EventType.INVALID_TRANSACTION);

        Assertions.assertTrue(scoring.getScore() < 0);
    }

    @Test
    void getNegativeScoreWhenValidAndInvalidTransaction() {
        PeerScoring scoring = new PeerScoring("id1");

        scoring.updateScoring(EventType.VALID_TRANSACTION);
        scoring.updateScoring(EventType.INVALID_TRANSACTION);

        Assertions.assertTrue(scoring.getScore() < 0);
    }

    @Test
    void getNegativeScoreWhenInvalidAndValidTransaction() {
        PeerScoring scoring = new PeerScoring("id1");

        scoring.updateScoring(EventType.INVALID_TRANSACTION);
        scoring.updateScoring(EventType.VALID_TRANSACTION);

        Assertions.assertTrue(scoring.getScore() < 0);
    }

    @Test
    void twoValidEventsHasBetterScoreThanOnlyOne() {
        PeerScoring scoring1 = new PeerScoring("id1");
        PeerScoring scoring2 = new PeerScoring("id2");

        scoring1.updateScoring(EventType.VALID_TRANSACTION);
        scoring1.updateScoring(EventType.VALID_TRANSACTION);

        scoring2.updateScoring(EventType.VALID_TRANSACTION);

        Assertions.assertTrue(scoring1.getScore() > scoring2.getScore());
    }

    @Test
    @SuppressWarnings("squid:S2925") // Thread.sleep() used
    void startPunishmentWhenEnabledPunishmentStarted() throws InterruptedException {
        PeerScoring scoring = new PeerScoring("id1");

        Assertions.assertEquals(0, scoring.getPunishmentTime());
        Assertions.assertEquals(0, scoring.getPunishmentCounter());

        int expirationTime = 1000;
        Assertions.assertTrue(scoring.startPunishment(expirationTime));

        Assertions.assertEquals(1, scoring.getPunishmentCounter());
        Assertions.assertFalse(scoring.refreshReputationAndPunishment());
        Assertions.assertEquals(expirationTime, scoring.getPunishmentTime());
        Assertions.assertEquals(scoring.getTimeLostGoodReputation() + expirationTime, scoring.getPunishedUntil());
        TimeUnit.MILLISECONDS.sleep(10);
        Assertions.assertFalse(scoring.refreshReputationAndPunishment());
        TimeUnit.MILLISECONDS.sleep(2000);
        Assertions.assertTrue(scoring.refreshReputationAndPunishment());
        Assertions.assertEquals(1, scoring.getPunishmentCounter());
        Assertions.assertEquals(0, scoring.getPunishedUntil());
    }

    @Test
    void startPunishmentWhenDisabledPunishmentNotStarted() {
        PeerScoring scoring = new PeerScoring("id1", false);
        Assertions.assertEquals(0, scoring.getPunishmentTime());
        Assertions.assertEquals(0, scoring.getPunishmentCounter());

        int expirationTime = 1000;
        Assertions.assertFalse(scoring.startPunishment(expirationTime));

        Assertions.assertEquals(0, scoring.getPunishmentCounter());
        Assertions.assertEquals(0, scoring.getPunishmentTime());
        Assertions.assertEquals(0, scoring.getPunishedUntil());

        Assertions.assertTrue(scoring.refreshReputationAndPunishment());
    }
}
