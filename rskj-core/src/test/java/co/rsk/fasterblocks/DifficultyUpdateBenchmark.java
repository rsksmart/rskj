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
    for (int n = 0; n <= 100; n++) {
      final double powerOfTwo = Math.pow(2, n);
      final BigInteger scale = BigInteger.valueOf((long) powerOfTwo); // SCALE is 2^n
      final BigInteger factorScaled = BigInteger.valueOf((long) Math.floor(FACTOR * powerOfTwo)); // 1 + 0.005 = 1.005 =>
                                                                                              // 0.995 * (2^n)
      BigInteger difficultyBI = new BigInteger("d2a47da04a3b12c8", 16);
      BigDecimal difficultyBD = new BigDecimal("15178394771539038920");

      assertEquals(difficultyBI, difficultyBD.toBigInteger());

      // benchmark fixed point
      long start = System.nanoTime();
      // BigInteger resultBI = difficultyBI.multiply(factorScaled).divide(scale);
      BigInteger resultBI = difficultyBI.multiply(factorScaled).shiftRight(n);
      long end = System.nanoTime();
      long benchDifficultyBI = end - start;

      // benchmark BigDecimal
      start = System.nanoTime();
      BigDecimal resultBD = difficultyBD.multiply(BigDecimal.valueOf(1.005));
      end = System.nanoTime();
      long benchDifficultyBD = end - start;

      // calculate absolute and relative errors
      BigDecimal absError = resultBD.subtract(
          new BigDecimal(resultBI)).abs();
      BigDecimal relError = absError
          .divide(resultBD, 16, RoundingMode.HALF_UP)
          .divide(new BigDecimal(0.005), RoundingMode.HALF_UP)
          .multiply(BigDecimal.valueOf(100));

      benchResult.add(new BenchElem(difficultyBI, difficultyBD,
          benchDifficultyBI, benchDifficultyBD, absError, relError, n, scale, FACTOR, factorScaled));
    }

    toCsv(benchResult);
  }

  private void toCsv(List<BenchElem> benchResult) {
    try (BufferedWriter out = new BufferedWriter(new FileWriter("bench.csv"))) {
      out.write("n,scale,factor,factorScaled,benchFP_ns_avg,benchBD_ns_avg,absError_avg,relError_avg\n");
      // out.write("n,scale,factor,factorScaled,benchFP_ns,benchBD_ns,absError,relError\n");
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

