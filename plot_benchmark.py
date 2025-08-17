#!/usr/bin/env python3
import pandas as pd
import matplotlib.pyplot as plt


def main():
    # Open the output text file
    with open("results.txt", "w") as out:
        # --- Average benchmarks ---
        df_avg = pd.read_csv("bench_avg.csv")
        # plots...
        plt.figure()
        plt.scatter(
            df_avg["n"], df_avg["benchFP_ns_avg"], label="Fixed-Point", marker="o"
        )
        plt.scatter(
            df_avg["n"], df_avg["benchBD_ns_avg"], label="BigDecimal", marker="x"
        )
        plt.xlabel("Scale (n)")
        plt.ylabel("Consumed Time (ns)")
        plt.title("Average Benchmark Time per Scale")
        plt.legend()
        plt.tight_layout()
        plt.savefig("time_per_scale_avg.png")
        plt.close()

        plt.figure()
        plt.scatter(df_avg["n"].iloc[10:], df_avg["relError_avg"].iloc[10:], marker=".")
        plt.xlabel("Scale (n)")
        plt.ylabel("Relative Error (%)")
        plt.title("Average Relative Error per Scale")
        plt.tight_layout()
        plt.savefig("error_per_scale_avg.png")
        plt.close()

        # prepare avg table
        df_avg_print = df_avg.assign(scale=lambda d: 2 ** d["n"])[
            ["n", "scale", "relError_avg", "benchFP_ns_avg", "benchBD_ns_avg"]
        ]
        avg_str = df_avg_print.to_string(index=False, float_format="%.12f")

        header_avg = "=== Average Benchmarks ===\n"
        print(header_avg + avg_str + "\n")
        out.write(header_avg)
        out.write(avg_str + "\n\n")

        # --- Median benchmarks ---
        df_med = pd.read_csv("bench_med.csv")
        # plots...
        plt.figure()
        plt.scatter(
            df_med["n"], df_med["benchFP_ns_med"], label="Fixed-Point", marker="o"
        )
        plt.scatter(
            df_med["n"], df_med["benchBD_ns_med"], label="BigDecimal", marker="x"
        )
        plt.xlabel("Scale (n)")
        plt.ylabel("Median Consumed Time (ns)")
        plt.title("Median Benchmark Time per Scale")
        plt.legend()
        plt.tight_layout()
        plt.savefig("time_per_scale_med.png")
        plt.close()

        plt.figure()
        plt.scatter(df_med["n"].iloc[10:], df_med["relError_med"].iloc[10:], marker=".")
        plt.xlabel("Scale (n)")
        plt.ylabel("Median Relative Error (%)")
        plt.title("Median Relative Error per Scale")
        plt.tight_layout()
        plt.savefig("error_per_scale_med.png")
        plt.close()

        # prepare med table
        df_med_print = df_med.assign(scale=lambda d: 2 ** d["n"])[
            ["n", "scale", "relError_med", "benchFP_ns_med", "benchBD_ns_med"]
        ]
        med_str = df_med_print.to_string(index=False, float_format="%.12f")

        header_med = "=== Median Benchmarks ===\n"
        print(header_med + med_str)
        out.write(header_med)
        out.write(med_str)


if __name__ == "__main__":
    main()
