package co.rsk.db.benchmarks;


public final class TestUtils {

    private static PseudoRandom pr = new PseudoRandom();

    private TestUtils() {
    }

    public static PseudoRandom getPseudoRandom() {
        return pr;
    }

}
