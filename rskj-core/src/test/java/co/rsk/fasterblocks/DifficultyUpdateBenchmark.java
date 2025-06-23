package co.rsk.fasterblocks;

import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DifficultyUpdateBenchmark {

  @Test
  public void runBenchTest() {
    final double FACTOR = 1.005; // 1 + ALPHA
    // final double FACTOR = 0.995; // 1 - ALPHA
    List<BenchElem> benchResult = new ArrayList<>();
    for (int n = 2; n <= 100; n++) {
      final double powerOfTwo = Math.pow(2, n);
      final BigInteger scale = BigInteger.valueOf((long) powerOfTwo); // SCALE is 2^n
      final BigInteger factorScaled = BigInteger.valueOf((long) Math.floor(FACTOR * powerOfTwo)); // 1 + 0.005 = 1.005 =>
                                                                                              // 0.995 * (2^n)
      // System.out.printf("SCALE (2^%d): %s\n", n, SCALE.toString());
      // System.out.printf("Scaled FACTOR as double: %s\n", Double.valueOf(1.005 * powerOfTwo).toString());
      // System.out.printf("Scaled FACTOR as BigInterger: %s\n\n", factorScaled.toString());

      BigInteger difficultyBI = new BigInteger("d2a47da04a3b12c8", 16);
      BigDecimal difficultyBD = new BigDecimal("15178394771539038920");

      assertEquals(difficultyBI, difficultyBD.toBigInteger());

      // benchmark fixed point
      long start = System.nanoTime();
      BigInteger resultBI = difficultyBI.multiply(factorScaled).divide(scale);
      long end = System.nanoTime();
      long benchDifficultyBI = end - start;
      // System.out.printf("Difficulty as BigInteger %d ns\n", benchDifficultyBI);
      // System.out.printf("%s\n", resultBI.toString());

      // benchmark BigDecimal
      start = System.nanoTime();
      BigDecimal resultBD = difficultyBD.multiply(BigDecimal.valueOf(1.005));
      end = System.nanoTime();
      long benchDifficultyBD = end - start;
      // System.out.printf("Difficulty as BigDecimal %d ns\n", benchDifficultyBD);
      // System.out.printf("%s\n", resultBD.toString());

      // calculate absolute and relative errors
      BigDecimal absError = resultBD.subtract(
          new BigDecimal(resultBI)).abs();
      BigDecimal relError = absError
          .divide(resultBD, 16, RoundingMode.HALF_UP)
          .divide(new BigDecimal(0.005), RoundingMode.HALF_UP)
          .multiply(BigDecimal.valueOf(100));

      // System.out.printf(
      //     "\nAbsolute error: %s%nRelative error: %s%%\n\n",
      //     "\nRelative error: %s%%\n\n",
      //     absError.toPlainString(),
      //     relError.toPlainString());

      // System.out.println("----------------------\n");

      benchResult.add(new BenchElem(difficultyBI, difficultyBD,
          benchDifficultyBI, benchDifficultyBD, absError, relError, n, scale, FACTOR, factorScaled));
    }

    toCsv(benchResult);

    // System.out.println("----------------------\n");
    //
    // // sort from low to high
    // Collections.sort(benchResult);
    // System.out.printf("FASTEST SCALES\n\n");
    // for (int i = 0; i < benchResult.size(); i++) {
    //   BenchElem elem = benchResult.get(i);
    //   System.out.printf("%d. SCALE 2^%d\n", i, elem.n);
    //   System.out.printf("   Bench fixed point: %d ns\n", elem.benchDifficultyBI);
    //   System.out.printf("   Bench BigDecimal: %d ns\n", elem.benchDifficultyBD);
    //   System.out.printf("   Relative Error: %s %%\n", elem.relError.toPlainString());
    // }
    // System.out.printf("\n");
  }

  private void toCsv(List<BenchElem> benchResult) {
    try (BufferedWriter out = new BufferedWriter(new FileWriter("bench.csv"))) {
      out.write("n,scale,factor,factorScaled,benchBI_ns,benchBD_ns,absError,relError\n");
      for (int i = 0; i < benchResult.size(); i++) {
        BenchElem e = benchResult.get(i);
        out.write(String.format(
            "%d,%s,%s,%s,%d,%d,%s,%s\n",
            e.n,
            e.scale.toString(),
            Double.valueOf(e.factor).toString(),
            e.factorScaled.toString(),
            e.benchDifficultyBI,
            e.benchDifficultyBD,
            e.absError.toPlainString(),
            e.relError.toPlainString()
        ));
      }
    } catch (IOException ex) {
      System.err.println("csv print failed");
      ex.printStackTrace();
    }
  }

  public class BenchElem implements Comparable<BenchElem> {
    public BigInteger difficultyBI;
    public BigDecimal difficultyBD;
    public long benchDifficultyBI;
    public long benchDifficultyBD;
    public BigDecimal absError;
    public BigDecimal relError; // relative to selected update factor => factor = 1 + ALPHA
    public int n;
    public BigInteger scale;
    public double factor;
    public BigInteger factorScaled;

    public BenchElem(BigInteger difficultyBI, BigDecimal difficultyBD,
        long benchDifficultyBI, long benchDifficultyBD,
        BigDecimal absError, BigDecimal relError, int n, BigInteger scale,
        double factor, BigInteger factorScaled) {
      this.difficultyBI = difficultyBI;
      this.difficultyBD = difficultyBD;
      this.benchDifficultyBI = benchDifficultyBI;
      this.benchDifficultyBD = benchDifficultyBD;
      this.absError = absError;
      this.relError = relError;
      this.n = n;
      this.scale = scale;
      this.factor = factor;
      this.factorScaled = factorScaled;
    }

    @Override
    public int compareTo(BenchElem o) {
      BenchElem other = (BenchElem) o;
      return Long.valueOf(benchDifficultyBI)
          .compareTo(other.benchDifficultyBI);
    }
  }
}
