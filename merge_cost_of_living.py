"""

python3 merge_cost_of_living_v2.py <input_cities_csv> <output_cities_csv>


python3 merge_cost_of_living_v2.py data/100/cities.csv data/scale_100/cities_realcost.csv
"""

import csv
import sys
from airport_city_mapping_100 import AIRPORT_TO_CITY_100

COST_OF_LIVING_CSV = "cost-of-living.csv"

ACCOMMODATION_MULTIPLIER = 1.5
MEALS_PER_DAY = 3
TRANSPORT_TRIPS_PER_DAY = 2

REQUIRED_FIELDS = ["x1", "x28", "x48"]


def normalize(name):
    return "".join(c for c in name if c.isascii()).strip()


NORMALIZED_MAPPING = {normalize(k): v for k, v in AIRPORT_TO_CITY_100.items()}


def row_is_complete(row):

    for field in REQUIRED_FIELDS:
        val = row.get(field, "").strip()
        if val == "":
            return False
        try:
            float(val)
        except ValueError:
            return False
    return True


def load_cost_of_living_lookup():
    with open(COST_OF_LIVING_CSV, newline="", encoding="utf-8") as f:
        all_rows = list(csv.DictReader(f))

    lookup = {}
    incomplete_skipped = []
    for city, country in set(AIRPORT_TO_CITY_100.values()):
        candidates = [r for r in all_rows if r["city"] == city]
        if not candidates:
            continue
        country_matches = [r for r in candidates
                            if country.lower() in r["country"].lower()
                            or r["country"].lower() in country.lower()]
        pool = country_matches if country_matches else candidates


        complete_pool = [r for r in pool if row_is_complete(r)]
        if not complete_pool:
            incomplete_skipped.append((city, country))
            continue

        best = max(complete_pool, key=lambda r: float(r["data_quality"]))
        lookup[(city, country)] = best

    if incomplete_skipped:
        print(f"Note: {len(incomplete_skipped)} matched cities had incomplete "
              f"price data (missing x1/x28/x48) and were skipped, falling back "
              f"to synthetic dailyCost for those:")
        for c, co in incomplete_skipped:
            print("  -", c, co)

    return lookup


def daily_cost_for(row):
    x1 = float(row["x1"])
    x28 = float(row["x28"])
    x48 = float(row["x48"])
    return round(MEALS_PER_DAY * x1 + TRANSPORT_TRIPS_PER_DAY * x28
                 + (x48 / 30.0) * ACCOMMODATION_MULTIPLIER, 2)


def main():
    if len(sys.argv) < 3:
        print("Usage: python3 merge_cost_of_living_v2.py <input_cities_csv> <output_cities_csv>")
        sys.exit(1)

    input_csv, output_csv = sys.argv[1], sys.argv[2]

    col_lookup = load_cost_of_living_lookup()

    with open(input_csv, newline="", encoding="utf-8") as f:
        cities = list(csv.DictReader(f))

    updated = 0
    fallback_names = []
    with open(output_csv, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["index", "name", "dailyCost", "preference", "isReal", "dailyCostSource"])
        for row in cities:
            name = row["name"]
            city_country = NORMALIZED_MAPPING.get(normalize(name))
            if city_country and city_country in col_lookup:
                new_cost = daily_cost_for(col_lookup[city_country])
                source = "numbeo_real"
                updated += 1
            else:
                new_cost = row["dailyCost"]
                source = "synthetic_fallback"
                fallback_names.append(name)

            w.writerow([row["index"], name, new_cost, row["preference"], row["isReal"], source])

    print(f"Wrote {output_csv}: {updated}/{len(cities)} cities updated with real Numbeo-derived costs")
    if fallback_names:
        print(f"{len(fallback_names)} cities kept synthetic dailyCost (no real/complete match):")
        for n in fallback_names:
            print("  -", n)


if __name__ == "__main__":
    main()
