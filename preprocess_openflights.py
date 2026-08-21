"""
    python3 preprocess_openflights.py
"""

import csv
import math
import random

TOP_N_AIRPORTS = 30
MAX_DAYS = 7
RANDOM_SEED = 42
DAILY_COST_RANGE = (50, 170)   # synthetic
PREFERENCE_RANGE = (1, 10)     # synthetic
BASE_FARE = 40.0
COST_PER_KM = 0.09
DAY_VARIATION = 0.15

random.seed(RANDOM_SEED)

AIRPORTS_FILE = "Full_Merge_of_All_Unique_Airports.csv"
ROUTES_FILE = "Full_Merge_of_All_Unique_Routes.csv"


def haversine_km(lat1, lon1, lat2, lon2):
    R = 6371.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlambda = math.radians(lon2 - lon1)
    a = math.sin(dphi / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dlambda / 2) ** 2
    return 2 * R * math.asin(math.sqrt(a))


def load_airports(path):
    airports = {}
    with open(path, newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            iata = row["ID"].strip()
            if not iata or iata == "\\N":
                continue
            try:
                lat, lon = float(row["Latitude"]), float(row["Longitude"])
            except ValueError:
                continue
            airports[iata] = {"name": row["Label"].strip(), "lat": lat, "lon": lon}
    return airports


def load_routes(path, airports):
    routes = set()
    with open(path, newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            dep, dest = row["Departure"].strip(), row["Destination"].strip()
            if dep in airports and dest in airports and dep != dest:
                routes.add((dep, dest))
    return routes


def rank_top_airports(routes, n):
    degree = {}
    for dep, dest in routes:
        degree[dep] = degree.get(dep, 0) + 1
        degree[dest] = degree.get(dest, 0) + 1
    ranked = sorted(degree.items(), key=lambda kv: kv[1], reverse=True)
    return [iata for iata, _ in ranked[:n]]


def price_for_route(distance_km, dow, route_seed):
    base = BASE_FARE + COST_PER_KM * distance_km
    rng = random.Random(route_seed * 7 + dow)  # deterministic per (route, day)
    multiplier = 1.0 + rng.uniform(-DAY_VARIATION, DAY_VARIATION)
    return round(base * multiplier, 2)


def main():
    import sys
    top_n = int(sys.argv[1]) if len(sys.argv) > 1 else TOP_N_AIRPORTS
    out_dir = sys.argv[2] if len(sys.argv) > 2 else "."
    import os
    os.makedirs(out_dir, exist_ok=True)
    cities_path = os.path.join(out_dir, "cities.csv")
    flights_path = os.path.join(out_dir, "flights.csv")

    airports = load_airports(AIRPORTS_FILE)
    print(f"Loaded {len(airports)} airports with coordinates")

    all_routes = load_routes(ROUTES_FILE, airports)
    print(f"Loaded {len(all_routes)} directed routes with both endpoints resolvable")

    top_airports = rank_top_airports(all_routes, top_n)
    top_set = set(top_airports)
    print(f"Selected top {len(top_airports)} airports by route degree")

    kept_routes = [(d, a) for (d, a) in all_routes if d in top_set and a in top_set]
    print(f"{len(kept_routes)} directed routes exist among the selected airports")

    # index 0 = home/hub node = single busiest airport overall
    ordered = list(top_airports)
    index_of = {iata: i for i, iata in enumerate(ordered)}

    with open(cities_path, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["index", "name", "dailyCost", "preference", "isReal"])
        for iata in ordered:
            idx = index_of[iata]
            w.writerow([
                idx,
                airports[iata]["name"].replace(",", ""),
                round(random.uniform(*DAILY_COST_RANGE), 1),
                round(random.uniform(*PREFERENCE_RANGE), 1),
                1,
            ])

    flight_rows = []
    route_id = 0
    for dep, dest in kept_routes:
        route_id += 1
        a1, a2 = airports[dep], airports[dest]
        dist = haversine_km(a1["lat"], a1["lon"], a2["lat"], a2["lon"])
        oi, di = index_of[dep], index_of[dest]
        for dow in range(MAX_DAYS):
            price = price_for_route(dist, dow, route_id)
            flight_rows.append((oi, di, dow, price, 1))


    covered = {(o, d) for (o, d, dow, p, r) in flight_rows}
    n = len(ordered)
    gap_fill = 0
    for i in range(n):
        for j in range(n):
            if i == j or (i, j) in covered:
                continue
            a1, a2 = airports[ordered[i]], airports[ordered[j]]
            dist = haversine_km(a1["lat"], a1["lon"], a2["lat"], a2["lon"])
            for dow in range(MAX_DAYS):
                price = price_for_route(dist, dow, 100000 + i * n + j)
                flight_rows.append((i, j, dow, price, 0))
            gap_fill += 1

    with open(flights_path, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["originIndex", "destIndex", "day", "price", "isReal"])
        for row in flight_rows:
            w.writerow(row)

    n_real = sum(1 for r in flight_rows if r[4] == 1)
    n_gap = sum(1 for r in flight_rows if r[4] == 0)
    print(f"Wrote {cities_path} ({len(ordered)} airports)")
    print(f"Wrote {flights_path} ({len(flight_rows)} rows: {n_real} from real routes, "
          f"{n_gap} distance-priced gap-fills covering {gap_fill} missing pairs)")


if __name__ == "__main__":
    main()
