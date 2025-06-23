package co.rsk.fasterblocks;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DifficultyUpdateBenchmark {

  @Test
  public void runBenchTest() {
    List<BenchElem> ranking = new ArrayList<>();
    for (int n = 2; n < 50; n++) {
      final double powerOfTwo = Math.pow(2, n);
      final BigInteger SCALE = BigInteger.valueOf((long) powerOfTwo); // SCALE is 2^n
      final BigInteger ALPHA_POS = BigInteger.valueOf((long) Math.floor(1.005 * powerOfTwo)); // 1 + 0.005 = 1.005 =>
                                                                                              // 1.005 * (2^n)
      final BigInteger ALPHA_NEG = BigInteger.valueOf((long) Math.floor(0.995 * powerOfTwo)); // 1 - 0.005 = 0.995 =>
                                                                                              // 0.995 * (2^n)
      System.out.printf("SCALE (2^%d): %s\n", n, SCALE.toString());
      System.out.printf("Scaled ALPHA_POS as double: %s\n", Double.valueOf(1.005 * powerOfTwo).toString());
      System.out.printf("Scaled ALPHA_POS as BigInterger: %s\n\n", ALPHA_POS.toString());

      BigInteger difficultyBI = new BigInteger("d2a47da04a3b12c8", 16);
      BigDecimal difficultyBD = new BigDecimal("15178394771539038920");

      assertEquals(difficultyBI, difficultyBD.toBigInteger());

      // benchmark fixed point
      long start = System.nanoTime();
      BigInteger updatedDifficultyBI = difficultyBI.multiply(ALPHA_POS).divide(SCALE);
      long end = System.nanoTime();
      long benchDifficultyBI = end - start;
      System.out.printf("Difficulty as BigInteger %d ns\n", benchDifficultyBI);
      System.out.printf("%s\n", updatedDifficultyBI.toString());

      // benchmark BigDecimal
      start = System.nanoTime();
      BigDecimal updatedDifficultyBD = difficultyBD.multiply(BigDecimal.valueOf(1.005));
      end = System.nanoTime();
      long benchDifficultyBD = end - start;
      System.out.printf("Difficulty as BigDecimal %d ns\n", benchDifficultyBD);
      System.out.printf("%s\n", updatedDifficultyBD.toString());

      // calculate absolute and relative errors
      BigDecimal fixedPointDiff = new BigDecimal(updatedDifficultyBI);
      BigDecimal realDiff = updatedDifficultyBD;
      BigDecimal absError = realDiff.subtract(fixedPointDiff).abs();
      BigDecimal relError = absError
          .divide(realDiff, 16, RoundingMode.HALF_UP)
          .divide(new BigDecimal(0.005), RoundingMode.HALF_UP)
          .multiply(BigDecimal.valueOf(100));

      System.out.printf(
          // "\nAbsolute error: %s%nRelative error: %s%%\n\n",
          "\nRelative error: %s%%\n\n",
          // absError.toPlainString(),
          relError.toPlainString());

      System.out.println("----------------------\n");

      ranking.add(new BenchElem(difficultyBI, difficultyBD,
          benchDifficultyBI, benchDifficultyBD, absError, relError, n));
    }

    System.out.println("----------------------\n");

    // sort from low to high
    Collections.sort(ranking);
    System.out.printf("FASTEST SCALES\n\n");
    for (int i = 0; i < ranking.size(); i++) {
      BenchElem elem = ranking.get(i);
      System.out.printf("%d. SCALE 2^%d\n", i, elem.n);
      System.out.printf("   Bench fixed point: %d ns\n", elem.benchDifficultyBI);
      System.out.printf("   Bench BigDecimal: %d ns\n", elem.benchDifficultyBD);
      System.out.printf("   Relative Error: %s %%\n", elem.relError.toPlainString());
    }
    System.out.printf("\n");
  }

  public class BenchElem implements Comparable<BenchElem> {
    public BigInteger difficultyBI;
    public BigDecimal difficultyBD;
    public long benchDifficultyBI;
    public long benchDifficultyBD;
    public BigDecimal absError;
    public BigDecimal relError;
    public int n;

    public BenchElem(BigInteger difficultyBI, BigDecimal difficultyBD,
        long benchDifficultyBI, long benchDifficultyBD,
        BigDecimal absError, BigDecimal relError, int n) {
      this.difficultyBI = difficultyBI;
      this.difficultyBD = difficultyBD;
      this.benchDifficultyBI = benchDifficultyBI;
      this.benchDifficultyBD = benchDifficultyBD;
      this.absError = absError;
      this.relError = relError;
      this.n = n;
    }

    @Override
    public int compareTo(BenchElem o) {
      BenchElem other = (BenchElem) o;
      return Long.valueOf(benchDifficultyBI)
          .compareTo(other.benchDifficultyBI);
    }
  }
}
