#!/usr/bin/env python3
import pandas as pd
import matplotlib.pyplot as plt


def main():
    # Carga los datos
    df = pd.read_csv("bench.csv")

    # Gráfico de tiempo consumido (ns) vs escala n, con tiempos de punto fijo y BigDecimal
    plt.figure()
    plt.scatter(df["n"], df["benchFP_ns_avg"], label="Fixed-Point", marker="o")
    plt.scatter(df["n"], df["benchBD_ns_avg"], label="BigDecimal", marker="x")
    plt.xlabel("Scale (n)")
    plt.ylabel("Consumed Time (ns)")
    plt.title("Benchmark Time per Scale")
    plt.legend()
    plt.tight_layout()
    plt.savefig("time_per_scale.png")
    plt.close()

    # Gráfico de error relativo (%) vs escala n
    plt.figure()
    plt.scatter(df["n"], df["relError_avg"])
    plt.xlabel("Scale (n)")
    plt.ylabel("Relative Error (%)")
    plt.title("Relative Error per Scale")
    plt.tight_layout()
    plt.savefig("error_per_scale.png")
    plt.close()


if __name__ == "__main__":
    main()

