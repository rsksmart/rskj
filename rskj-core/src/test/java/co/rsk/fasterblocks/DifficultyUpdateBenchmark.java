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



// import org.junit.jupiter.api.Test;
//
// import java.io.BufferedWriter;
// import java.io.FileWriter;
// import java.io.IOException;
// import java.math.BigDecimal;
// import java.math.BigInteger;
// import java.math.RoundingMode;
// import java.util.ArrayList;
// import java.util.List;
//
// import static org.junit.jupiter.api.Assertions.assertEquals;
//
// public class DifficultyUpdateBenchmark {
//
//   private static final int REPEATS = 100;
//   // private static final int REPEATS = 50;
//
//   @Test
//   public void runBenchTest() {
//     final double FACTOR = 1.005; // 1 + ALPHA
//     List<BenchElem> benchResult = new ArrayList<>();
//
//     // for (int n = 0; n <= 100; n++) {
//     for (int n = 0; n <= 30; n++) {
//       double powerOfTwo = Math.pow(2, n);
//       BigInteger scale = BigInteger.valueOf((long)powerOfTwo);
//       BigInteger factorScaled = BigInteger.valueOf((long)Math.floor(FACTOR * powerOfTwo));
//
//       BigInteger difficultyBI = new BigInteger("d2a47da04a3b12c8", 16);
//       BigDecimal difficultyBD = new BigDecimal("15178394771539038920");
//
//       assertEquals(difficultyBI, difficultyBD.toBigInteger());
//
//       long sumBI = 0, sumBD = 0;
//       BigDecimal sumAbsError = BigDecimal.ZERO;
//       BigDecimal sumRelError = BigDecimal.ZERO;
//
//       for (int i = 0; i < REPEATS; i++) {
//         // fixed-point
//         long t0 = System.nanoTime();
//         BigInteger resultBI = difficultyBI.multiply(factorScaled).divide(scale);
//         // BigInteger resultBI = difficultyBI.multiply(factorScaled).shiftRight(scale.intValue());
//         long t1 = System.nanoTime();
//         sumBI += (t1 - t0);
//
//         // BigDecimal
//         t0 = System.nanoTime();
//         BigDecimal resultBD = difficultyBD.multiply(BigDecimal.valueOf(FACTOR));
//         t1 = System.nanoTime();
//         sumBD += (t1 - t0);
//
//         // errors
//         BigDecimal absError = resultBD.subtract(new BigDecimal(resultBI)).abs();
//         BigDecimal relError = absError
//             .divide(resultBD, 16, RoundingMode.HALF_UP)
//             .divide(new BigDecimal(0.005), RoundingMode.HALF_UP)
//             .multiply(BigDecimal.valueOf(100));
//
//         sumAbsError = sumAbsError.add(absError);
//         sumRelError = sumRelError.add(relError);
//       }
//
//       // compute averages
//       long avgBI = sumBI  / REPEATS;
//       long avgBD = sumBD  / REPEATS;
//       BigDecimal avgAbs = sumAbsError.divide(BigDecimal.valueOf(REPEATS), 16, RoundingMode.HALF_UP);
//       BigDecimal avgRel = sumRelError.divide(BigDecimal.valueOf(REPEATS), 16, RoundingMode.HALF_UP);
//
//       benchResult.add(new BenchElem(
//           n, scale, FACTOR, factorScaled,
//           avgBI, avgBD, avgAbs, avgRel
//       ));
//     }
//
//     toCsv(benchResult);
//     System.out.println("Averaged benchmark CSV written.");
//   }
//
//   private void toCsv(List<BenchElem> benchResult) {
//     try (BufferedWriter out = new BufferedWriter(new FileWriter("bench.csv"))) {
//       out.write("n,scale,factor,factorScaled,benchFP_ns_avg,benchBD_ns_avg,absError_avg,relError_avg\n");
//       for (BenchElem e : benchResult) {
//         out.write(String.format(
//             "%d,%s,%.3f,%s,%d,%d,%s,%s\n",
//             e.n,
//             e.scale,
//             e.factor,
//             e.factorScaled,
//             e.avgBI,
//             e.avgBD,
//             e.avgAbs.toPlainString(),
//             e.avgRel.toPlainString()
//         ));
//       }
//     } catch (IOException ex) {
//       ex.printStackTrace();
//     }
//   }
//
//   public static class BenchElem {
//     public final int n;
//     public final BigInteger scale;
//     public final double factor;
//     public final BigInteger factorScaled;
//     public final long avgBI;         // avg fixed-point ns
//     public final long avgBD;         // avg BigDecimal ns
//     public final BigDecimal avgAbs;  // avg absolute error
//     public final BigDecimal avgRel;  // avg relative error
//
//     public BenchElem(int n, BigInteger scale, double factor, BigInteger factorScaled,
//                      long avgBI, long avgBD, BigDecimal avgAbs, BigDecimal avgRel) {
//       this.n            = n;
//       this.scale        = scale;
//       this.factor       = factor;
//       this.factorScaled = factorScaled;
//       this.avgBI        = avgBI;
//       this.avgBD        = avgBD;
//       this.avgAbs       = avgAbs;
//       this.avgRel       = avgRel;
//     }
//   }
// }
// package co.rsk.fasterblocks;

// package co.rsk.fasterblocks;
//
// import org.junit.jupiter.api.Test;
//
// import java.io.BufferedWriter;
// import java.io.FileWriter;
// import java.io.IOException;
// import java.math.BigDecimal;
// import java.math.BigInteger;
// import java.math.RoundingMode;
//
// public class DifficultyUpdateBenchmark {
//
//   private static final int MAX_SCALE = 100;
//   private static final int REPEATS   = 100;
//   private static final double FACTOR = 1.005; // 1 + ALPHA
//
//   @Test
//   public void runBenchTest() {
//     // Preparamos arrays para acumular los REPEATS resultados por cada escala n
//     long[][]   timesFP  = new long[REPEATS][MAX_SCALE+1];
//     long[][]   timesBD  = new long[REPEATS][MAX_SCALE+1];
//     BigDecimal[][] errors = new BigDecimal[REPEATS][MAX_SCALE+1];
//
//     // Outer: repetir el benchmark REPEATS veces
//     for (int run = 0; run < REPEATS; run++) {
//       for (int n = 0; n <= MAX_SCALE; n++) {
//         // Preparamos los parámetros de esta escala
//         double powerOfTwo   = Math.pow(2, n);
//         BigInteger scale    = BigInteger.valueOf((long) powerOfTwo);
//         BigInteger factorScaled = BigInteger.valueOf((long) Math.floor(FACTOR * powerOfTwo));
//         BigInteger difficultyBI = new BigInteger("d2a47da04a3b12c8", 16);
//         BigDecimal difficultyBD= new BigDecimal("15178394771539038920");
//
//         // Fixed-point timing
//         long t0 = System.nanoTime();
//         BigInteger resultFP = difficultyBI.multiply(factorScaled).shiftRight(n);
//         long t1 = System.nanoTime();
//         timesFP[run][n] = t1 - t0;
//
//         // BigDecimal timing
//         t0 = System.nanoTime();
//         BigDecimal resultBD = difficultyBD.multiply(BigDecimal.valueOf(FACTOR));
//         t1 = System.nanoTime();
//         timesBD[run][n] = t1 - t0;
//
//         // Error calculation
//         BigDecimal absError = resultBD.subtract(new BigDecimal(resultFP)).abs();
//         BigDecimal relError = absError
//             .divide(resultBD, 30, RoundingMode.HALF_UP)
//             .divide(BigDecimal.valueOf(0.005), 30, RoundingMode.HALF_UP)
//             .multiply(BigDecimal.valueOf(100));
//         errors[run][n] = relError;
//       }
//     }
//
//     long[]   avgFP   = new long[MAX_SCALE+1];
//     long[]   avgBD   = new long[MAX_SCALE+1];
//     BigDecimal[] avgErr = new BigDecimal[MAX_SCALE+1];
//
//     for (int n = 0; n <= MAX_SCALE; n++) {
//       long sumFP = 0, sumBD = 0;
//       BigDecimal sumErr = BigDecimal.ZERO;
//
//       for (int run = 0; run < REPEATS; run++) {
//         sumFP += timesFP[run][n];
//         sumBD += timesBD[run][n];
//         sumErr = sumErr.add(errors[run][n]);
//       }
//
//       avgFP[n]   = sumFP / REPEATS;
//       avgBD[n]   = sumBD / REPEATS;
//       avgErr[n]  = sumErr.divide(BigDecimal.valueOf(REPEATS), 30, RoundingMode.HALF_UP);
//     }
//
//     try (BufferedWriter out = new BufferedWriter(new FileWriter("bench.csv"))) {
//       out.write("n,scale,benchFP_ns_avg,benchBD_ns_avg,relError_avg\n");
//       for (int n = 0; n <= MAX_SCALE; n++) {
//         BigInteger scale = BigInteger.valueOf((long)Math.pow(2, n));
//         out.write(String.format(
//           "%d,%s,%d,%d,%s\n",
//           n,
//           new BigDecimal(scale).toPlainString(),
//           avgFP[n],
//           avgBD[n],
//           avgErr[n].toPlainString()
//         ));
//       }
//     } catch (IOException e) {
//       e.printStackTrace();
//     }
//
//     System.out.println("Averaged benchmarks written to bench.csv");
//   }
// }

