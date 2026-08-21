"""

Extends aggregate_results.py (which only looks at the FINAL generation) to
look at the FULL per-generation trajectory of every run, so you can answer
"when/how does mechanism X help" rather than only "does it help by the end".


Outputs:
  results/trajectories_summary.csv          - per (config factor, on/off,
                                                generation) mean+std for every metric
  results/trajectory_<mechanism>_<metric>.png - one plot per mechanism per metric

Usage:
    pip install pandas matplotlib
    python3 aggregate_trajectories.py
"""

import math
import os
import pandas as pd
import matplotlib.pyplot as plt

import sys
RESULTS_DIR = sys.argv[1] if len(sys.argv) > 1 else "results"
SHAPE_FILTER = sys.argv[2] if len(sys.argv) > 2 else "linear"
BUDGET_FILTER = sys.argv[3] if len(sys.argv) > 3 else "current"
TAG = f"{SHAPE_FILTER}_{BUDGET_FILTER}"
INDEX_CSV = os.path.join(RESULTS_DIR, "run_index.csv")

MECHANISMS = ["useCrowdingDistance", "preserveExtremePoints", "removeDuplicateObjectiveSolutions",
              "useAdaptiveMutation", "useRandomImmigrants", "useDiscreteObjectives"]
METRICS = ["pareto_size", "hypervolume", "spacing", "cost_range", "preference_range"]


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
        print("(No shapeLabel column - older run_index.csv, analyzing all rows as-is.)")

    if "budgetLabel" in index.columns:
        available_b = index["budgetLabel"].unique()
        if BUDGET_FILTER not in available_b:
            print(f"Budget '{BUDGET_FILTER}' not found. Available budgets: {list(available_b)}")
            return
        index = index[index["budgetLabel"] == BUDGET_FILTER].reset_index(drop=True)
        print(f"Filtered to budget='{BUDGET_FILTER}': {len(index)} runs")
    else:
        print("(No budgetLabel column - older run_index.csv, analyzing all rows as-is.)")

    print(f"Loading full per-generation logs for {len(index)} runs (this can take a while)...")


    all_data = []
    global_max_cost = 0.0
    global_cost_range = 0.0
    global_pref_range = 0.0

    raw_logs = {}
    for _, run in index.iterrows():
        pareto = pd.read_csv(run["paretoLogPath"])
        raw_logs[run["paretoLogPath"]] = pareto
        if len(pareto):
            global_max_cost = max(global_max_cost, pareto["cost"].max())
            for g in pareto["generation"].unique():
                front = pareto[pareto["generation"] == g]
                if len(front) >= 2:
                    global_cost_range = max(global_cost_range, front["cost"].max() - front["cost"].min())
                    global_pref_range = max(global_pref_range, front["preference"].max() - front["preference"].min())

    global_ref_cost = global_max_cost * 1.1
    print(f"Global hypervolume reference cost: {global_ref_cost:.2f}")
    print(f"Global cost range (for spacing normalization): {global_cost_range:.2f}")
    print(f"Global preference range (for spacing normalization): {global_pref_range:.2f}")

    rows = []
    for _, run in index.iterrows():
        pareto = raw_logs[run["paretoLogPath"]]
        for g in sorted(pareto["generation"].unique()):
            front = pareto[pareto["generation"] == g]
            rows.append({
                "configLabel": run["configLabel"],
                "useCrowdingDistance": run["useCrowdingDistance"],
                "preserveExtremePoints": run["preserveExtremePoints"],
                "removeDuplicateObjectiveSolutions": run["removeDuplicateObjectiveSolutions"],
                "useAdaptiveMutation": run["useAdaptiveMutation"],
                "useRandomImmigrants": run["useRandomImmigrants"],
                "useDiscreteObjectives": run["useDiscreteObjectives"] if "useDiscreteObjectives" in run else False,
                "seed": run["seed"],
                "generation": g,
                "pareto_size": len(front),
                "hypervolume": hypervolume_2d(front, global_ref_cost, 0.0),
                "spacing": spacing_metric_normalized(front, global_cost_range, global_pref_range),
                "cost_range": (front["cost"].max() - front["cost"].min()) if len(front) else 0.0,
                "preference_range": (front["preference"].max() - front["preference"].min()) if len(front) else 0.0,
            })

    full = pd.DataFrame(rows)
    print(f"Computed per-generation metrics for {len(full)} (run, generation) pairs")

    summary_rows = []
    for mech in MECHANISMS:
        for on_off in [True, False]:
            subset = full[full[mech] == on_off]
            grouped = subset.groupby("generation")[METRICS].agg(["mean", "std"])
            for g, vals in grouped.iterrows():
                row = {"mechanism": mech, "on": on_off, "generation": g}
                for metric in METRICS:
                    row[f"{metric}_mean"] = vals[(metric, "mean")]
                    row[f"{metric}_std"] = vals[(metric, "std")]
                summary_rows.append(row)

    traj_summary = pd.DataFrame(summary_rows)
    traj_summary.to_csv(os.path.join(RESULTS_DIR, f"trajectories_summary_{TAG}.csv"), index=False)
    print(f"Wrote {RESULTS_DIR}/trajectories_summary_{TAG}.csv")

    for mech in MECHANISMS:
        for metric in METRICS:
            plt.figure(figsize=(8, 6))
            for on_off, color, label in [(True, "#2a78d6", "ON"), (False, "#e34948", "OFF")]:
                sub = traj_summary[(traj_summary["mechanism"] == mech) & (traj_summary["on"] == on_off)]
                sub = sub.sort_values("generation")
                mean = sub[f"{metric}_mean"]
                std = sub[f"{metric}_std"].fillna(0)
                plt.plot(sub["generation"], mean, label=label, color=color)
                plt.fill_between(sub["generation"], mean - std, mean + std, color=color, alpha=0.15)
            plt.xlabel("Generation")
            plt.ylabel(metric)
            plt.title(f"{metric} over generations: {mech} ON vs OFF\n(shaded = ±1 std across 80 seeds/runs)")
            plt.legend()
            plt.grid(True)
            plt.tight_layout()
            fname = os.path.join(RESULTS_DIR, f"trajectory_{TAG}_{mech}_{metric}.png")
            plt.savefig(fname)
            plt.close()

    print(f"Wrote trajectory plots to {RESULTS_DIR}/")


if __name__ == "__main__":
    main()
