"""
check_budget_calibration.py

Reusable version of the Monte Carlo check used to derive TOTAL_BUDGET=5000 /
DAILY_BUDGET=1500 from the N=100 dataset. Run this on ANY cities.csv/flights.csv
BEFORE committing to a full batch run on that dataset, to confirm the budget
still makes sense at that scale - or retroactively, to validate a dataset you
already ran a batch on.

What to look for:
  - If your TOTAL_BUDGET sits between roughly p50 and p75 of the simulated
    distribution, it's in the "sometimes binds, doesn't dominate" zone -
    the same target used for the original 5000/1500 calibration.
  - If TOTAL_BUDGET is below p25, most tours are likely infeasible (the
    1200/700 mistake from earlier in this project).
  - If TOTAL_BUDGET is above p90, the budget likely never binds at all
    (the original 8000/1500 issue).

Usage:
    python3 check_budget_calibration.py <cities_csv> <flights_csv> [total_budget] [daily_budget]

Example:
    python3 check_budget_calibration.py data/scale_200/cities.csv data/scale_200/flights.csv 5000 1500
"""

import csv
import random
import sys

TOTAL_BUDGET = float(sys.argv[3]) if len(sys.argv) > 3 else 5000
DAILY_BUDGET = float(sys.argv[4]) if len(sys.argv) > 4 else 1500
N_SAMPLES = 3000


def main():
    if len(sys.argv) < 3:
        print("Usage: python3 check_budget_calibration.py <cities_csv> <flights_csv> [total_budget] [daily_budget]")
        sys.exit(1)

    cities_csv, flights_csv = sys.argv[1], sys.argv[2]

    cities = {}
    with open(cities_csv, newline="") as f:
        for row in csv.DictReader(f):
            cities[int(row["index"])] = float(row["dailyCost"])

    flights = {}
    leg_prices = []
    with open(flights_csv, newline="") as f:
        for row in csv.DictReader(f):
            key = (int(row["originIndex"]), int(row["destIndex"]), int(row["day"]))
            price = float(row["price"])
            flights[key] = price
            leg_prices.append(price)

    leg_prices.sort()
    n_legs = len(leg_prices)

    random.seed(1)
    city_ids = [i for i in cities if i != 0]

    def simulate_tour():
        n = random.randint(2, 8)
        visited = random.sample(city_ids, n)
        days = random.sample(range(7), n) if n <= 7 else [random.randint(0, 6) for _ in range(n)]
        visited_sorted = [x for _, x in sorted(zip(days, visited))]
        days_sorted = sorted(days) if n <= 7 else days

        total = 0.0
        max_leg = 0.0
        prev = 0
        for city, day in zip(visited_sorted, days_sorted):
            leg = flights.get((prev, city, day), sum(leg_prices) / n_legs)  # fallback: mean price
            total += leg + cities[city]
            max_leg = max(max_leg, leg)
            prev = city
        final_leg = flights.get((prev, 0, days_sorted[-1]), sum(leg_prices) / n_legs)
        total += final_leg
        max_leg = max(max_leg, final_leg)
        return total, max_leg

    totals = []
    max_legs = []
    for _ in range(N_SAMPLES):
        t, m = simulate_tour()
        totals.append(t)
        max_legs.append(m)
    totals.sort()
    max_legs.sort()

    n = len(totals)
    print(f"Dataset: {cities_csv} / {flights_csv} ({len(cities)} cities)")
    print(f"\nSimulated TOTAL tour cost distribution ({N_SAMPLES} random 2-8 city tours):")
    for pct in [0.1, 0.25, 0.5, 0.75, 0.9]:
        print(f"  p{int(pct*100)}: {totals[int(n*pct)]:.0f}")

    print(f"\nSimulated single-LEG price distribution:")
    for pct in [0.5, 0.75, 0.9]:
        print(f"  p{int(pct*100)}: {max_legs[int(n*pct)]:.0f}")

    # where does the current budget fall?
    below = sum(1 for t in totals if t < TOTAL_BUDGET)
    pct_below = 100 * below / n
    print(f"\nTOTAL_BUDGET={TOTAL_BUDGET:.0f} sits at approximately the "
          f"{pct_below:.0f}th percentile of simulated tour costs.")
    if pct_below < 25:
        print("  -> WARNING: budget likely too tight (below p25) - most tours may be infeasible.")
    elif pct_below > 90:
        print("  -> WARNING: budget likely too loose (above p90) - may rarely/never bind.")
    else:
        print("  -> Looks reasonably calibrated (roughly p25-p90 range).")

    below_leg = sum(1 for m in max_legs if m < DAILY_BUDGET)
    pct_below_leg = 100 * below_leg / n
    print(f"\nDAILY_BUDGET={DAILY_BUDGET:.0f} sits at approximately the "
          f"{pct_below_leg:.0f}th percentile of simulated max-single-leg prices.")


if __name__ == "__main__":
    main()
