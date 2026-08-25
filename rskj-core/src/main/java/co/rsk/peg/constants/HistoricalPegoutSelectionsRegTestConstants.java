package co.rsk.peg.constants;

/**
 * Regtest has no historical chain to reproduce, so it records no selections.
 */
public class HistoricalPegoutSelectionsRegTestConstants extends HistoricalPegoutSelectionsConstants {

    private static final HistoricalPegoutSelectionsRegTestConstants instance = new HistoricalPegoutSelectionsRegTestConstants();

    private HistoricalPegoutSelectionsRegTestConstants() {
        // No pre-RSKIP559 chain to reproduce, so the inherited empty table applies.
    }

    public static HistoricalPegoutSelectionsRegTestConstants getInstance() {
        return instance;
    }
}
