package com.volleyhub.pro;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class MatchDataTest {

    private MatchData match;

    @Before
    public void setUp() {
        match = new MatchData("Squadra A", "Squadra B", "#00fbff", "#ff0055");
    }

    @Test
    public void testIsSetWinningPointRegularSet() {
        match.setScoreA(24);
        match.setScoreB(20);
        match.setCurrentSet(1);

        // Point for A gives 25-20 (diff 5 >= 2) -> winning point
        assertTrue(match.isSetWinningPoint(true));

        // Point for B gives 24-21 (B not at 25) -> not winning point
        assertFalse(match.isSetWinningPoint(false));

        // Tie at 24-24: 25-24 is not winning (diff < 2)
        match.setScoreA(24);
        match.setScoreB(24);
        assertFalse(match.isSetWinningPoint(true));
        assertFalse(match.isSetWinningPoint(false));

        // Advantage at 25-24: 26-24 is winning (diff 2)
        match.setScoreA(25);
        match.setScoreB(24);
        assertTrue(match.isSetWinningPoint(true));
        assertFalse(match.isSetWinningPoint(false));
    }

    @Test
    public void testIsSetWinningPointTieBreakSet() {
        match.setCurrentSet(5);
        match.setScoreA(14);
        match.setScoreB(12);

        // Point for A gives 15-12 in tiebreak (set 5) -> winning point
        assertTrue(match.isSetWinningPoint(true));
    }

    @Test
    public void testCompleteCurrentSet() {
        match.setScoreA(25);
        match.setScoreB(23);
        match.setCurrentSet(1);

        match.completeCurrentSet(true);

        assertEquals(1, match.getSetsWonA());
        assertEquals(0, match.getSetsWonB());
        assertEquals(0, match.getScoreA());
        assertEquals(0, match.getScoreB());
        assertEquals(2, match.getCurrentSet());
        assertFalse(match.isMatchComplete());
    }

    @Test
    public void testMatchComplete() {
        match.setSetsWonA(2);
        match.setCurrentSet(3);

        match.completeCurrentSet(true);

        assertEquals(3, match.getSetsWonA());
        assertTrue(match.isMatchComplete());
    }
}
