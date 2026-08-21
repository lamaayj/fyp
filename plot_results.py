import os
import math
import pandas as pd
import matplotlib.pyplot as plt

POP_CSV = "population_log.csv"
PARETO_CSV = "pareto_log.csv"
OUT_DIR = "plots"

os.makedirs(OUT_DIR, exist_ok=True)

pop = pd.read_csv(POP_CSV)
pareto = pd.read_csv(PARETO_CSV)

# ---------- Plot 1: final Pareto front ----------
final_gen = pareto["generation"].max()
final_front = pareto[pareto["generation"] == final_gen].copy()

plt.figure(figsize=(8, 6))
plt.scatter(final_front["cost"], final_front["preference"])
for _, row in final_front.iterrows():
    plt.annotate(row["index"], (row["cost"], row["preference"]))
plt.xlabel("Cost")
plt.ylabel("Preference")
plt.title(f"Final Pareto Front (Generation {final_gen})")
plt.grid(True)
plt.tight_layout()
plt.savefig(os.path.join(OUT_DIR, "final_pareto_front.png"))
plt.close()

# ---------- Plot 2: Pareto front evolution ----------
gens = sorted(pareto["generation"].unique())
plt.figure(figsize=(8, 6))
for g in gens:
    front = pareto[pareto["generation"] == g]
    plt.scatter(front["cost"], front["preference"], alpha=0.35)
plt.xlabel("Cost")
plt.ylabel("Preference")
plt.title("Pareto Front Evolution")
plt.grid(True)
plt.tight_layout()
plt.savefig(os.path.join(OUT_DIR, "pareto_evolution.png"))
plt.close()

# ---------- Metrics helpers ----------
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


GLOBAL_REF_COST = pareto["cost"].max() * 1.1
GLOBAL_REF_PREF = 0.0
print(f"Using fixed hypervolume reference point: cost={GLOBAL_REF_COST:.2f}, preference={GLOBAL_REF_PREF}")


GLOBAL_COST_RANGE = 0.0
GLOBAL_PREF_RANGE = 0.0
for g in gens:
    front = pareto[pareto["generation"] == g]
    if len(front) >= 2:
        GLOBAL_COST_RANGE = max(GLOBAL_COST_RANGE, front["cost"].max() - front["cost"].min())
        GLOBAL_PREF_RANGE = max(GLOBAL_PREF_RANGE, front["preference"].max() - front["preference"].min())
print(f"Using fixed spacing normalization ranges: cost_range={GLOBAL_COST_RANGE:.2f}, "
      f"preference_range={GLOBAL_PREF_RANGE:.2f}")

metrics = []
for g in gens:
    front = pareto[pareto["generation"] == g].copy()
    metrics.append({
        "generation": g,
        "pareto_size": len(front),
        "min_cost": front["cost"].min() if len(front) else 0.0,
        "max_preference": front["preference"].max() if len(front) else 0.0,
        "cost_range": (front["cost"].max() - front["cost"].min()) if len(front) else 0.0,
        "preference_range": (front["preference"].max() - front["preference"].min()) if len(front) else 0.0,
        "spacing": spacing_metric_normalized(front, GLOBAL_COST_RANGE, GLOBAL_PREF_RANGE),
        "hypervolume": hypervolume_2d(front, GLOBAL_REF_COST, GLOBAL_REF_PREF),
        "unique_objective_pairs": len(front[["cost", "preference"]].drop_duplicates())
    })

metrics_df = pd.DataFrame(metrics)
metrics_df.to_csv(os.path.join(OUT_DIR, "metrics_summary.csv"), index=False)

plt.figure(figsize=(8, 6))
plt.plot(metrics_df["generation"], metrics_df["pareto_size"])
plt.xlabel("Generation")
plt.ylabel("Number of Pareto Solutions")
plt.title("Pareto Front Size Over Generations")
plt.grid(True)
plt.tight_layout()
plt.savefig(os.path.join(OUT_DIR, "pareto_size_over_time.png"))
plt.close()

plt.figure(figsize=(8, 6))
plt.plot(metrics_df["generation"], metrics_df["spacing"])
plt.xlabel("Generation")
plt.ylabel("Spacing")
plt.title("Spacing Metric Over Generations")
plt.grid(True)
plt.tight_layout()
plt.savefig(os.path.join(OUT_DIR, "spacing_over_time.png"))
plt.close()

plt.figure(figsize=(8, 6))
plt.plot(metrics_df["generation"], metrics_df["hypervolume"])
plt.xlabel("Generation")
plt.ylabel("Hypervolume")
plt.title("Hypervolume Over Generations")
plt.grid(True)
plt.tight_layout()
plt.savefig(os.path.join(OUT_DIR, "hypervolume_over_time.png"))
plt.close()

plt.figure(figsize=(8, 6))
plt.plot(metrics_df["generation"], metrics_df["cost_range"], label="Cost range")
plt.plot(metrics_df["generation"], metrics_df["preference_range"], label="Preference range")
plt.xlabel("Generation")
plt.ylabel("Range")
plt.title("Objective Spread Over Generations")
plt.legend()
plt.grid(True)
plt.tight_layout()
plt.savefig(os.path.join(OUT_DIR, "objective_ranges.png"))
plt.close()

print("Plots saved in:", OUT_DIR)
print(metrics_df.tail())
