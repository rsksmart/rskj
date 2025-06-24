package co.rsk.fasterblocks;

import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DifficultyUpdateBenchmark {

  @Test
  public void runBenchTest() {
    final double ALPHA = 0.005;
    final double FACTOR = 1 + ALPHA; // 1 + ALPHA
    // final double FACTOR = 1 - ALPHA; // 1 - ALPHA
    final int ROUNDS = 100;

    List<BenchElem> benchResult = new ArrayList<>();
    for (int j = 0; j <= ROUNDS; j++) {
      for (int n = 0; n <= 100; n++) {
        final double powerOfTwo = Math.pow(2, n);
        final BigInteger scale = BigInteger.valueOf((long) powerOfTwo);
        final BigInteger factorScaled = BigInteger.valueOf((long) Math.floor(FACTOR * powerOfTwo)); // 1 + 0.005 = 1.005
                                                                                                    // =>
        BigInteger difficultyBI = new BigInteger("d2a47da04a3b12c8", 16);
        BigDecimal difficultyBD = new BigDecimal("15178394771539038920");

        assertEquals(difficultyBI, difficultyBD.toBigInteger());

        // benchmark fixed point
        long start = System.nanoTime();
        BigInteger resultBI = difficultyBI.multiply(factorScaled).divide(scale);
        // BigInteger resultBI = difficultyBI.multiply(factorScaled).shiftRight(n);
        long end = System.nanoTime();
        long benchDifficultyBI = end - start;

        // benchmark BigDecimal
        start = System.nanoTime();
        BigDecimal resultBD = difficultyBD.multiply(BigDecimal.valueOf(FACTOR));
        end = System.nanoTime();
        long benchDifficultyBD = end - start;

        // calculate absolute and relative errors
        BigDecimal absError = resultBD.subtract(
            new BigDecimal(resultBI)).abs();
        BigDecimal relError = absError
            .divide(resultBD, 16, RoundingMode.HALF_UP)
            .divide(new BigDecimal(ALPHA), RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));

        benchResult.add(new BenchElem(difficultyBI, difficultyBD,
            benchDifficultyBI, benchDifficultyBD, absError, relError,
            n, FACTOR, factorScaled));
      }
    }

    toCsv(doAverage(benchResult, ROUNDS), false);
    toCsv(doMedian(benchResult), true);
  }

  private void toCsv(List<BenchElem> benchResult, boolean isMedian) {
    String fileName = isMedian ? "bench_med.csv" : "bench_avg.csv";
    try (BufferedWriter out = new BufferedWriter(new FileWriter(fileName))) {
      String colAvg = "n,factor,factorScaled,benchFP_ns_avg,benchBD_ns_avg,absError_avg,relError_avg\n";
      String colMed = "n,factor,factorScaled,benchFP_ns_med,benchBD_ns_med,absError_med,relError_med\n";
      out.write(isMedian ? colMed : colAvg);
      for (int i = 0; i < benchResult.size(); i++) {
        BenchElem e = benchResult.get(i);
        out.write(String.format(
            "%d,%s,%s,%d,%d,%s,%s\n",
            e.n,
            Double.valueOf(e.factor).toString(),
            e.factorScaled.toString(),
            e.benchDifficultyBI,
            e.benchDifficultyBD,
            e.absError.toPlainString(),
            e.relError.toPlainString()));
      }
    } catch (IOException ex) {
      System.err.println("csv print failed");
      ex.printStackTrace();
    }
  }

  private List<BenchElem> doAverage(List<BenchElem> benchElems, int rounds) {
    Map<Integer, BenchElem> map = new HashMap<>();
    for (BenchElem elem : benchElems) {
      BenchElem avgElem = map.getOrDefault(Integer.valueOf(elem.n),
          BenchElem.neutral(elem.n, elem.factor, elem.factorScaled));
      avgElem.accumulate(elem);
      map.put(Integer.valueOf(avgElem.n), avgElem);
    }

    List<BenchElem> result = map.values()
        .stream()
        .map(e -> e.toAvgElem(rounds))
        .collect(Collectors.toList());

    return result;
  }

  private List<BenchElem> doMedian(List<BenchElem> benchElems) {
    // Agrupamos todos los BenchElem por valor de n
    Map<Integer, List<BenchElem>> groups = new HashMap<>();
    for (BenchElem elem : benchElems) {
      groups.computeIfAbsent(elem.n, k -> new ArrayList<>()).add(elem);
    }
    // Para cada grupo, calculamos la mediana
    return groups.values().stream()
        .map(BenchElem::toMedianElem)
        .collect(Collectors.toList());
  }

  public static class BenchElem implements Comparable<BenchElem> {
    public BigInteger difficultyBI;
    public BigDecimal difficultyBD;
    public long benchDifficultyBI;
    public long benchDifficultyBD;
    public BigDecimal absError;
    public BigDecimal relError; // relative to selected update factor => factor = 1 + ALPHA
    public int n;
    public double factor;
    public BigInteger factorScaled;

    public BenchElem(BigInteger difficultyBI, BigDecimal difficultyBD,
        long benchDifficultyBI, long benchDifficultyBD,
        BigDecimal absError, BigDecimal relError, int n,
        double factor, BigInteger factorScaled) {
      this.difficultyBI = difficultyBI;
      this.difficultyBD = difficultyBD;
      this.benchDifficultyBI = benchDifficultyBI;
      this.benchDifficultyBD = benchDifficultyBD;
      this.absError = absError;
      this.relError = relError;
      this.n = n;
      this.factor = factor;
      this.factorScaled = factorScaled;
    }

    public static BenchElem toMedianElem(List<BenchElem> elems) {
      int size = elems.size();
      int mid = size / 2;

      List<BigInteger> bis = elems.stream()
          .map(e -> e.difficultyBI)
          .sorted(Comparator.naturalOrder())
          .collect(Collectors.toList());
      BigInteger medianBI = bis.get(mid);

      List<BigDecimal> bds = elems.stream()
          .map(e -> e.difficultyBD)
          .sorted(Comparator.naturalOrder())
          .collect(Collectors.toList());
      BigDecimal medianBD = bds.get(mid);

      List<Long> fpTimes = elems.stream()
          .map(e -> e.benchDifficultyBI)
          .sorted()
          .collect(Collectors.toList());
      long medianFP = fpTimes.get(mid);

      List<Long> bdTimes = elems.stream()
          .map(e -> e.benchDifficultyBD)
          .sorted()
          .collect(Collectors.toList());
      long medianBDt = bdTimes.get(mid);

      List<BigDecimal> abs = elems.stream()
          .map(e -> e.absError)
          .sorted(Comparator.naturalOrder())
          .collect(Collectors.toList());
      BigDecimal medianAbs = abs.get(mid);

      List<BigDecimal> rel = elems.stream()
          .map(e -> e.relError)
          .sorted(Comparator.naturalOrder())
          .collect(Collectors.toList());
      BigDecimal medianRel = rel.get(mid);

      BenchElem ref = elems.get(0);
      return new BenchElem(
          medianBI, medianBD,
          medianFP, medianBDt,
          medianAbs, medianRel,
          ref.n, ref.factor, ref.factorScaled);
    }

    public BenchElem toAvgElem(int rounds) {
      BenchElem avgElem = BenchElem.neutral(this.n, this.factor, this.factorScaled);

      avgElem.difficultyBI = this.difficultyBI.divide(BigInteger.valueOf(rounds));
      avgElem.difficultyBD = this.difficultyBD.divide(BigDecimal.valueOf(rounds));
      avgElem.benchDifficultyBI = this.benchDifficultyBI / rounds;
      avgElem.benchDifficultyBD = this.benchDifficultyBD / rounds;
      avgElem.absError = this.absError.divide(BigDecimal.valueOf(rounds));
      avgElem.relError = this.relError.divide(BigDecimal.valueOf(rounds));

      return avgElem;
    }

    public void accumulate(BenchElem elem) {
      if (this.n != elem.n || this.factor != elem.factor
          || !this.factorScaled.equals(elem.factorScaled)) {
        throw new IllegalArgumentException("elem should have same n, factor and factorScaled");
      }

      this.difficultyBI = this.difficultyBI.add(elem.difficultyBI);
      this.difficultyBD = this.difficultyBD.add(elem.difficultyBD);
      this.benchDifficultyBI += elem.benchDifficultyBI;
      this.benchDifficultyBD += elem.benchDifficultyBD;
      this.absError = this.absError.add(elem.absError);
      this.relError = this.relError.add(elem.relError);
    }

    public static BenchElem neutral(int n, double factor, BigInteger factorScaled) {
      return new BenchElem(BigInteger.ZERO, BigDecimal.ZERO,
          0, 0, BigDecimal.ZERO, BigDecimal.ZERO,
          n, factor, factorScaled);
    }

    @Override
    public int compareTo(BenchElem o) {
      BenchElem other = (BenchElem) o;
      return Long.valueOf(benchDifficultyBI)
          .compareTo(other.benchDifficultyBI);
    }
  }
}
