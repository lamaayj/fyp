# Diversity Preservation in Multi-Objective Evolutionary Tour Planning

**Research question:** How do diversity preservation mechanisms, search-space scale, and objective-space geometry jointly affect the spread, quality, and size of Pareto-optimal solution sets in constrained tour planning?

This project implements an NSGA-II-based multi-objective evolutionary algorithm for constrained multi-city tour planning (minimising cost, maximising destination preference), and systematically studies how six diversity-preservation and objective-shaping mechanisms affect the resulting Pareto front, across five search-space scales and six objective-space geometries.

This is a revised submission. The original version used a small, fully synthetic dataset (25 cities, max tour length 3) and found no measurable effect of diversity preservation — later diagnosed as most likely an artefact of an under-scaled search space rather than a genuine absence of effect. This revision replaces the dataset with real-world data and tests that diagnosis directly.

---

## Quick summary of key findings

- **`useCrowdingDistance`** shows a real, statistically significant effect that strengthens with search-space scale (1/5 significant metrics at N=30, up to 5/5 at N=120) and varies with objective-space geometry — directly supporting the diagnosis of the original project's null result.
- **`preserveExtremePoints`** shows **no significant effect on any metric across 11 independent conditions** (5 scales, 6 objective shapes) — a consistently replicated negative result, most likely explained by redundancy with `useCrowdingDistance`'s existing extreme-point protection (see Report Section 7).
- **`removeDuplicateObjectiveSolutions`** is null under continuous objectives (empirically verified: zero duplicate `(cost, preference)` pairs found in a real run) but becomes significant once objectives are discretized — and the *strength* of this interaction follows a dose-response pattern governed by the transformed objective's numeric range (Report Section 6.3, Table 5).
- **`useAdaptiveMutation`** and **`useRandomImmigrants`** (restorative mechanisms) show real but generally weaker, more scale/shape-conditional effects than the selection-based mechanisms.
- **`useDiscreteObjectives`** has a large, mostly shape-independent main effect on its own, operating through a mechanism distinct from its interaction with duplicate removal.

Full results, tables, and statistical detail are in `report/FYP_Report_Draft_v2.docx`.

---

## Repository structure

```
src/main/java/          Core algorithm (see "Core classes" below)
src/data/                Generated datasets (cities.csv, flights.csv per scale)
preprocess_openflights.py    Builds cities.csv/flights.csv from raw OpenFlights data
merge_cost_of_living_v2.py   Merges real Numbeo cost-of-living data into cities.csv
airport_city_mapping_100.py  Airport -> (city, country) lookup table for the merge above
check_budget_calibration.py  Monte Carlo tool to sanity-check TOTAL_BUDGET/DAILY_BUDGET
BatchRunner.java             Runs the full factorial experiment sweep
aggregate_results.py         Statistical analysis (Mann-Whitney U) + boxplots
aggregate_trajectories.py    Per-generation convergence analysis across seeds
aggregate_interactions.py    Factorial interaction (pairwise) analysis
results*/                    Output of each experiment batch (gitignored - regenerate via BatchRunner)
report/                       Report drafts
```

### Core classes (`src/main/java/`)

| Class | Role |
|---|---|
| `City`, `Visit` | A destination and a scheduled visit to it |
| `Individual` | One candidate tour; holds cost/preference/penalty and NSGA-II bookkeeping |
| `FlightMatrix` | Sparse lookup of flight prices by (origin, destination, day) |
| `TourEvaluator` | Computes cost/preference/penalty for a tour |
| `NonDominatedSorting`, `CrowdingDistance` | Core NSGA-II ranking mechanics |
| `GeneticOperators` | Crossover, mutation, repair |
| `TournamentSelection` | Parent selection |
| `Population` | Holds individuals; applies the 3 selection-based mechanisms during next-generation selection |
| `NsgaII` | Main evolutionary loop; implements the 2 restorative mechanisms |
| `Config` | Every tunable parameter, including all 6 mechanism flags |
| `Main` | Single interactive run |
| `BatchRunner` | Full factorial experiment sweep (see below) |

---

## Setup

**Requirements:** Java (any recent JDK), Python 3 with `pandas`, `scipy`, `matplotlib`, `numpy`.

**1. Get the raw data files** (not included in the repo — large, sourced externally):
- `Full_Merge_of_All_Unique_Airports.csv`, `Full_Merge_of_All_Unique_Routes.csv` — OpenFlights-derived airport/route data
- `cost-of-living.csv` — Numbeo-derived cost-of-living data (columns `x1`-`x55`, see comments in `merge_cost_of_living_v2.py` for which columns are used and why)

**2. Generate a dataset at your chosen scale:**
```bash
python3 preprocess_openflights.py 100 data/scale_100
```
This selects the top-N airports by real route connectivity, prices routes by great-circle distance where no real fare data exists, and writes `cities.csv`/`flights.csv`.

**3. (Optional) Merge in real cost-of-living data:**
```bash
python3 merge_cost_of_living_v2.py data/scale_100/cities.csv data/scale_100/cities_realcost.csv
```
Without this step, `dailyCost` is synthetic (random, documented as a limitation — see report). Destination `preference` is always synthetic; no dataset of genuine traveller preference was identified.

**4. Compile:**
```bash
javac -d out main/java/*.java
```

---

## Running experiments

**Single interactive run** (prints the final Pareto front to console):
```bash
java -cp out main.java.Main
```

**Full factorial batch sweep** — all 6 mechanisms (2⁶ = 64 configs) × 30 seeds, optionally crossed with 6 objective-space shapes:
```bash
# linear objective shape only (default, 64 configs x 30 seeds = 1,920 runs)
java -cp out main.java.BatchRunner data/scale_100/cities.csv data/scale_100/flights.csv results_n100

# all 6 objective shapes (384 configs x 30 seeds = 11,520 runs)
java -cp out main.java.BatchRunner data/scale_100/cities.csv data/scale_100/flights.csv results_n100_allshapes all
```

**Analyze results:**
```bash
python3 aggregate_results.py results_n100                  # marginalised ON/OFF stats + boxplots
python3 aggregate_results.py results_n100_allshapes linear  # for the "all" sweep, pick one shape at a time
python3 aggregate_trajectories.py results_n100              # per-generation convergence, all seeds
python3 aggregate_interactions.py results_n100/results_summary.csv   # pairwise interaction effects
```

**Sanity-check budget calibration for a new dataset before running a full sweep:**
```bash
python3 check_budget_calibration.py data/scale_100/cities.csv data/scale_100/flights.csv 5000 1500
```

---

## The six mechanisms (`Config.java` flags)

| Flag | Category | What it does |
|---|---|---|
| `useCrowdingDistance` | Selection-based | Prefers spread-out individuals when a front must be truncated |
| `preserveExtremePoints` | Selection-based | Forces the min-cost and max-preference individuals into the next generation |
| `removeDuplicateObjectiveSolutions` | Selection-based | Discards individuals whose (cost, preference) pair exactly duplicates one already kept |
| `useAdaptiveMutation` | Restorative | Boosts mutation rates when unique-objective count stagnates |
| `useRandomImmigrants` | Restorative | Replaces the worst-ranked individuals each generation with fresh random tours |
| `useDiscreteObjectives` | Objective-shaping | Rounds cost/preference to whole numbers before evaluation |

`costSlope`/`preferenceSlope`/`useNonlinearCost`/`useNonlinearPreference` reshape the objective trade-off geometry (see `BatchRunner`'s `ObjectiveShape` conditions) without changing which underlying tours are truly Pareto-optimal — used to test whether mechanism effects depend on trade-off shape, not just search-space size.

---

## Known limitations (see report Section 8 for full detail)

- Destination `preference` is synthetic throughout — no real dataset for this was found.
- Real flight-route connectivity decreases as city count increases (80.3% real at N=30, down to 27.1% at N=200); the remainder is priced by distance, not observed fares.
- The objective-shape sweep was run at a single scale; a combined scale×shape interaction was not tested.
- Restorative mechanism strengths (`randomImmigrantRate`, `mutationBoostFactor`) were fixed, not swept.

## Methodological corrections made during development

Documented explicitly (not hidden) because they materially affect result validity:
1. **Hypervolume reference point** — originally recalculated per generation, which made an improving front appear to get worse; fixed to one value held fixed across every generation/run being compared.
2. **Spacing metric** — originally summed raw cost and preference differences despite a ~65-85x scale mismatch between them; fixed by normalizing each term by its own range.
3. **Budget calibration** — went through two rounds of correction (an initial value that never bound, then an over-correction that made ~90% of tours infeasible) before being validated both via Monte Carlo simulation and direct inspection of real per-generation infeasibility rates.
