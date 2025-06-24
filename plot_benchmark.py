#!/usr/bin/env python3
import pandas as pd
import matplotlib.pyplot as plt


def main():
    df = pd.read_csv("bench_avg.csv")

    plt.figure()
    plt.scatter(df["n"], df["benchFP_ns_avg"], label="Fixed-Point", marker="o")
    plt.scatter(df["n"], df["benchBD_ns_avg"], label="BigDecimal", marker="x")
    plt.xlabel("Scale (n)")
    plt.ylabel("Consumed Time (ns)")
    plt.title("Average Benchmark Time per Scale")
    plt.legend()
    plt.tight_layout()
    plt.savefig("time_per_scale_avg.png")
    plt.close()

    plt.figure()
    plt.scatter(df["n"], df["relError_avg"])
    plt.xlabel("Scale (n)")
    plt.ylabel("Relative Error (%)")
    plt.title("Average Relative Error per Scale")
    plt.tight_layout()
    plt.savefig("error_per_scale_avg.png")
    plt.close()

    df = pd.read_csv("bench_med.csv")

    plt.figure()
    plt.scatter(df["n"], df["benchFP_ns_med"], label="Fixed-Point", marker="o")
    plt.scatter(df["n"], df["benchBD_ns_med"], label="BigDecimal", marker="x")
    plt.xlabel("Scale (n)")
    plt.ylabel("Median Consumed Time (ns)")
    plt.title("Median Benchmark Time per Scale")
    plt.legend()
    plt.tight_layout()
    plt.savefig("time_per_scale_med.png")
    plt.close()

    plt.figure()
    plt.scatter(df["n"], df["relError_med"])
    plt.xlabel("Scale (n)")
    plt.ylabel("Median Relative Error (%)")
    plt.title("Median Relative Error per Scale")
    plt.tight_layout()
    plt.savefig("error_per_scale_med.png")
    plt.close()


if __name__ == "__main__":
    main()
