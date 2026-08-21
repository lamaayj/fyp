"""

Reads results/run_index.csv (written by BatchRunner.java) plus every run's
pareto_log.csv, and produces:

  results/results_summary.csv   - one row per run: config flags, seed, final
                                   pareto_size, hypervolume, spacing, cost_range,
                                   preference_range
  results/stats_tests.txt       - Mann-Whitney U test for each diversity
                                   mechanism (on vs off), marginalised over the
                                   other two factors
  results/boxplot_<metric>.png  - one boxplot per metric, grouped by each
                                   mechanism's on/off setting

Usage:
    pip install pandas scipy matplotlib --break-system-packages
    python3 aggregate_results.py
"""

import math
import os
import pandas as pd
from scipy.stats import mannwhitneyu
import matplotlib.pyplot as plt

import sys
RESULTS_DIR = sys.argv[1] if len(sys.argv) > 1 else "results"

SHAPE_FILTER = sys.argv[2] if len(sys.argv) > 2 else "linear"
BUDGET_FILTER = sys.argv[3] if len(sys.argv) > 3 else "current"
TAG = f"{SHAPE_FILTER}_{BUDGET_FILTER}"
INDEX_CSV = os.path.join(RESULTS_DIR, "run_index.csv")


def spacing_metric_normalized(front_df, cost_range, pref_range):

    if len(front_df) < 2 or cost_range == 0 or pref_range == 0:
        return 0.0
    points = list(zip(front_df["cost"], front_df["preference"]))
    dists = []
    for i, p in enumerate(points):
        nearest = None
        for j, q in enumerate(points):
            if i == j:
                continue
            d = abs(p[0] - q[0]) / cost_range + abs(p[1] - q[1]) / pref_range
            if nearest is None or d < nearest:
                nearest = d
        dists.append(nearest)
    mean_d = sum(dists) / len(dists)
    return math.sqrt(sum((d - mean_d) ** 2 for d in dists) / len(dists))


def hypervolume_2d(front_df, ref_cost, ref_pref=0.0):
    if len(front_df) == 0:
        return 0.0
    front = front_df.sort_values("cost", ascending=False)
    hv = 0.0
    prev_cost = ref_cost
    for _, row in front.iterrows():
        width = prev_cost - row["cost"]
        height = max(0.0, row["preference"] - ref_pref)
        hv += width * height
        prev_cost = row["cost"]
    return hv


def main():
    index = pd.read_csv(INDEX_CSV)
    print(f"Found {len(index)} runs in {INDEX_CSV}")

    if "shapeLabel" in index.columns:
        available = index["shapeLabel"].unique()
        if SHAPE_FILTER not in available:
            print(f"Shape '{SHAPE_FILTER}' not found. Available shapes: {list(available)}")
            return
        index = index[index["shapeLabel"] == SHAPE_FILTER].reset_index(drop=True)
        print(f"Filtered to shape='{SHAPE_FILTER}': {len(index)} runs")
    else:
        print("(No shapeLabel column - this is an older run_index.csv, analyzing all rows as-is.)")

    if "budgetLabel" in index.columns:
        available_b = index["budgetLabel"].unique()
        if BUDGET_FILTER not in available_b:
            print(f"Budget '{BUDGET_FILTER}' not found. Available budgets: {list(available_b)}")
            return
        index = index[index["budgetLabel"] == BUDGET_FILTER].reset_index(drop=True)
        print(f"Filtered to budget='{BUDGET_FILTER}': {len(index)} runs")
    else:
        print("(No budgetLabel column - this is an older run_index.csv, analyzing all rows as-is.)")

    final_fronts = {}
    max_cost_seen = 0.0
    global_cost_range = 0.0
    global_pref_range = 0.0
    for _, run in index.iterrows():
        pareto = pd.read_csv(run["paretoLogPath"])
        final_gen = pareto["generation"].max()
        front = pareto[pareto["generation"] == final_gen]
        final_fronts[run["paretoLogPath"]] = front
        if len(front):
            max_cost_seen = max(max_cost_seen, front["cost"].max())
        if len(front) >= 2:
            global_cost_range = max(global_cost_range, front["cost"].max() - front["cost"].min())
            global_pref_range = max(global_pref_range, front["preference"].max() - front["preference"].min())

    global_ref_cost = max_cost_seen * 1.1
    global_ref_pref = 0.0
    print(f"Shared hypervolume reference point (all {len(index)} runs): "
          f"cost={global_ref_cost:.2f}, preference={global_ref_pref}")
    print(f"Shared spacing normalization ranges: cost_range={global_cost_range:.2f}, "
          f"preference_range={global_pref_range:.2f}")

    rows = []
    for _, run in index.iterrows():
        front = final_fronts[run["paretoLogPath"]]
        rows.append({
            "configLabel": run["configLabel"],
            "useCrowdingDistance": run["useCrowdingDistance"],
            "preserveExtremePoints": run["preserveExtremePoints"],
            "removeDuplicateObjectiveSolutions": run["removeDuplicateObjectiveSolutions"],
            "useAdaptiveMutation": run["useAdaptiveMutation"],
            "useRandomImmigrants": run["useRandomImmigrants"],
            "useDiscreteObjectives": run["useDiscreteObjectives"] if "useDiscreteObjectives" in run else False,
            "seed": run["seed"],
            "pareto_size": len(front),
            "hypervolume": hypervolume_2d(front, global_ref_cost, global_ref_pref),
            "spacing": spacing_metric_normalized(front, global_cost_range, global_pref_range),
            "cost_range": (front["cost"].max() - front["cost"].min()) if len(front) else 0.0,
            "preference_range": (front["preference"].max() - front["preference"].min()) if len(front) else 0.0,
        })

    summary = pd.DataFrame(rows)
    summary.to_csv(os.path.join(RESULTS_DIR, f"results_summary_{TAG}.csv"), index=False)
    print(f"Wrote {RESULTS_DIR}/results_summary_{TAG}.csv ({len(summary)} rows)")

    metrics = ["pareto_size", "hypervolume", "spacing", "cost_range", "preference_range"]
    mechanisms = ["useCrowdingDistance", "preserveExtremePoints", "removeDuplicateObjectiveSolutions",
                  "useAdaptiveMutation", "useRandomImmigrants", "useDiscreteObjectives"]

    with open(os.path.join(RESULTS_DIR, f"stats_tests_{TAG}.txt"), "w") as out:
        for mech in mechanisms:
            out.write(f"\n===== {mech}: ON vs OFF (Mann-Whitney U, marginalised over other factors) =====\n")
            print(f"\n===== {mech}: ON vs OFF =====")
            on_group = summary[summary[mech] == True]
            off_group = summary[summary[mech] == False]
            for metric in metrics:
                stat, p = mannwhitneyu(on_group[metric], off_group[metric], alternative="two-sided")
                line = (f"{metric:20s} | ON  mean={on_group[metric].mean():10.2f}  "
                        f"OFF mean={off_group[metric].mean():10.2f}  "
                        f"U={stat:.1f}  p={p:.4f}"
                        f"{'  *** significant (p<0.05)' if p < 0.05 else ''}")
                out.write(line + "\n")
                print(line)

    with open(os.path.join(RESULTS_DIR, f"stats_tests_{TAG}.txt"), "a") as out:
        out.write(f"\n\n===== INTERACTION CHECK: removeDuplicateObjectiveSolutions WITHIN useDiscreteObjectives =====\n")
        print(f"\n===== INTERACTION CHECK: removeDuplicateObjectiveSolutions WITHIN useDiscreteObjectives =====")
        for discrete_val in [False, True]:
            subset = summary[summary["useDiscreteObjectives"] == discrete_val]
            on_group = subset[subset["removeDuplicateObjectiveSolutions"] == True]
            off_group = subset[subset["removeDuplicateObjectiveSolutions"] == False]
            header = f"-- useDiscreteObjectives={discrete_val} --"
            out.write(header + "\n")
            print(header)
            for metric in metrics:
                stat, p = mannwhitneyu(on_group[metric], off_group[metric], alternative="two-sided")
                line = (f"{metric:20s} | ON  mean={on_group[metric].mean():10.2f}  "
                        f"OFF mean={off_group[metric].mean():10.2f}  p={p:.4f}"
                        f"{'  *** significant (p<0.05)' if p < 0.05 else ''}")
                out.write(line + "\n")
                print(line)

        out.write(f"\n\n===== INTERACTION CHECK: preserveExtremePoints WITHIN useCrowdingDistance =====\n")
        print(f"\n===== INTERACTION CHECK: preserveExtremePoints WITHIN useCrowdingDistance =====")
        for crowding_val in [False, True]:
            subset2 = summary[summary["useCrowdingDistance"] == crowding_val]
            on_group2 = subset2[subset2["preserveExtremePoints"] == True]
            off_group2 = subset2[subset2["preserveExtremePoints"] == False]
            header2 = f"-- useCrowdingDistance={crowding_val} --"
            out.write(header2 + "\n")
            print(header2)
            for metric in metrics:
                stat, p = mannwhitneyu(on_group2[metric], off_group2[metric], alternative="two-sided")
                line2 = (f"{metric:20s} | ON  mean={on_group2[metric].mean():10.2f}  "
                        f"OFF mean={off_group2[metric].mean():10.2f}  p={p:.4f}"
                        f"{'  *** significant (p<0.05)' if p < 0.05 else ''}")
                out.write(line2 + "\n")
                print(line2)

    for mech in mechanisms:
        for metric in metrics:
            plt.figure(figsize=(6, 5))
            groups = [summary[summary[mech] == False][metric], summary[summary[mech] == True][metric]]
            plt.boxplot(groups, labels=["OFF", "ON"])
            plt.title(f"{metric} by {mech}")
            plt.ylabel(metric)
            plt.grid(True, axis="y")
            plt.tight_layout()
            fname = os.path.join(RESULTS_DIR, f"boxplot_{TAG}_{mech}_{metric}.png")
            plt.savefig(fname)
            plt.close()

    print(f"\nBoxplots and stats_tests_{TAG}.txt written to {RESULTS_DIR}/")


if __name__ == "__main__":
    main()
