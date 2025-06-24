# import pandas as pd
# import matplotlib.pyplot as plt
#
# # Constants for weighting
# ERROR_WEIGHT_CONST = 2
# PERFORMANCE_WEIGHT_CONST = 1
#
# # 1. Load the benchmark CSV (must be in the same directory)
# df = pd.read_csv('bench.csv')
# df = df.iloc[1:].reset_index(drop=True)  # drop the first noisy element
#
# # 2. Compute weight and sort
# df['weight'] = ERROR_WEIGHT_CONST * df['relError'] + PERFORMANCE_WEIGHT_CONST * df['benchFP_ns']
# df_sorted = df.sort_values('weight').reset_index(drop=True)
#
# # 3. Chart 1: Fixed-Point vs BigDecimal computation time per scale (scatter)
# fig1, ax1 = plt.subplots(figsize=(8, 5))
# ax1.scatter(df['n'], df['benchFP_ns'], marker='o', label='Fixed-Point Time (ns)')
# ax1.scatter(df['n'], df['benchBD_ns'], marker='s', label='BigDecimal Time (ns)', color='C1')
# ax1.set_xlabel('Power of 2 (n)')
# ax1.set_ylabel('Execution Time (ns)')
# ax1.set_title('Fixed-Point vs BigDecimal Computation Time per Scale')
# ax1.legend()
# fig1.tight_layout()
# fig1.savefig('fp_vs_bigdecimal_time_per_scale.png')
# plt.close(fig1)
#
# # 4. Chart 2: Relative error per scale (scatter)
# fig2, ax2 = plt.subplots(figsize=(8, 5))
# ax2.scatter(df['n'], df['relError'], marker='o', color='C1')
# ax2.set_xlabel('Power of 2 (n)')
# ax2.set_ylabel('Relative Error (%)')
# ax2.set_title('Relative Error per Scale')
# fig2.tight_layout()
# fig2.savefig('rel_error_per_scale.png')
# plt.close(fig2)
#
# # 5. Write the weighted ranking table to a text file
# with open('benchmark_rankings.txt', 'w') as f:
#     f.write(f"{'rank':<5} {'scale':<6} {'fpNs':<8} {'decimalNs':<10} {'relError':<12} {'weight'}\n")
#     for idx, row in df_sorted.iterrows():
#         rank = idx + 1
#         f.write(
#             f"{rank:<5} "
#             f"{int(row['n']):<6} "
#             f"{int(row['benchFP_ns']):<8} "
#             f"{int(row['benchBD_ns']):<10} "
#             f"{row['relError']:<12.6f} "
#             f"{row['weight']:.2f}\n"
#         )
#
# # 6. Success message
# print("Charts saved as 'fp_vs_bigdecimal_time_per_scale.png' and 'rel_error_per_scale.png'; rankings saved to 'benchmark_rankings.txt'.")

import pandas as pd
import matplotlib.pyplot as plt

# Constants for weighting
ERROR_WEIGHT_CONST = 2
PERFORMANCE_WEIGHT_CONST = 1

# 1. Load the averaged benchmark CSV (must be in the same directory)
df = pd.read_csv('bench.csv')
df = df.iloc[1:].reset_index(drop=True)  # drop the first noisy element

# 2. Compute weight and sort
df['weight'] = ERROR_WEIGHT_CONST * df['relError_avg'] + PERFORMANCE_WEIGHT_CONST * df['benchFP_ns_avg']
df_sorted = df.sort_values('weight').reset_index(drop=True)

# 3. Chart 1: Fixed-Point vs BigDecimal average computation time per scale (scatter)
fig1, ax1 = plt.subplots(figsize=(8, 5))
ax1.scatter(df['n'], df['benchFP_ns_avg'], marker='o', label='Fixed-Point Time Avg (ns)')
ax1.scatter(df['n'], df['benchBD_ns_avg'], marker='s', label='BigDecimal Time Avg (ns)', color='C1')
ax1.set_xlabel('Power of 2 (n)')
ax1.set_ylabel('Avg Execution Time (ns)')
ax1.set_title('Fixed-Point vs BigDecimal Average Computation Time per Scale')
ax1.legend()
fig1.tight_layout()
fig1.savefig('fp_vs_bigdecimal_time_per_scale.png')
plt.close(fig1)

# 4. Chart 2: Average relative error per scale (scatter)
fig2, ax2 = plt.subplots(figsize=(8, 5))
ax2.scatter(df['n'], df['relError_avg'], marker='o', color='C1')
ax2.set_xlabel('Power of 2 (n)')
ax2.set_ylabel('Average Relative Error (%)')
ax2.set_title('Average Relative Error per Scale')
fig2.tight_layout()
fig2.savefig('rel_error_per_scale.png')
plt.close(fig2)

# 5. Write the weighted ranking table to a text file
with open('benchmark_rankings.txt', 'w') as f:
    f.write(f"{'rank':<5} {'scale':<6} {'fpNs_avg':<10} {'decimalNs_avg':<14} {'relError_avg':<14} {'weight'}\n")
    for idx, row in df_sorted.iterrows():
        rank = idx + 1
        f.write(
            f"{rank:<5} "
            f"{int(row['n']):<6} "
            f"{int(row['benchFP_ns_avg']):<10} "
            f"{int(row['benchBD_ns_avg']):<14} "
            f"{row['relError_avg']:<14.6f} "
            f"{row['weight']:.2f}\n"
        )

# 6. Success message
print("Charts saved as 'fp_vs_bigdecimal_time_per_scale.png' and 'rel_error_per_scale.png'; rankings saved to 'benchmark_rankings.txt'.")

