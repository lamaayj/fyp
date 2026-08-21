package main.java;

import java.util.*;

public class GeneticOperators {

    public static Individual crossover(Individual p1, Individual p2, List<City> cities, int maxDays, Config config) {
        List<Visit> childTour = new ArrayList<>();
        Set<Integer> usedCityIds = new HashSet<>();

        int split1 = GlobalRandom.rand.nextInt(p1.getTour().size() + 1);

        for (int i = 0; i < split1; i++) {
            Visit v = cloneVisit(p1.getTour().get(i));
            childTour.add(v);
            usedCityIds.add(v.getCity().getIndex());
        }

        for (Visit v : p2.getTour()) {
            if (!usedCityIds.contains(v.getCity().getIndex())) {
                childTour.add(cloneVisit(v));
                usedCityIds.add(v.getCity().getIndex());
            }
        }

        childTour.sort(Comparator.comparingInt(Visit::getArrivalDay));
        Individual child = new Individual(childTour);
        repairUniqueDays(child, cities, maxDays, config);
        return child;
    }

    public static void mutate(Individual ind, List<City> cities, int maxDays, Config config) {
        List<Visit> tour = ind.getTour();

        if (tour.isEmpty()) return;

        boolean changed = false;

        if (GlobalRandom.rand.nextDouble() < config.mutationRate) {
            Set<Integer> usedCityIds = new HashSet<>();
            for (Visit v : tour) {
                usedCityIds.add(v.getCity().getIndex());
            }

            List<City> available = new ArrayList<>();
            for (City c : cities) {
                if (!usedCityIds.contains(c.getIndex())) {
                    available.add(c);
                }
            }

            if (!available.isEmpty()) {
                int idx = GlobalRandom.rand.nextInt(tour.size());
                Visit oldVisit = tour.get(idx);
                City newCity = available.get(GlobalRandom.rand.nextInt(available.size()));
                tour.set(idx, new Visit(newCity, oldVisit.getArrivalDay()));
                changed = true;
            }
        }

        if (GlobalRandom.rand.nextDouble() < config.changeDayMutationRate) {
            Visit v = tour.get(GlobalRandom.rand.nextInt(tour.size()));
            Set<Integer> usedDays = new HashSet<>();

            for (Visit visit : tour) {
                if (visit != v) {
                    usedDays.add(visit.getArrivalDay());
                }
            }

            List<Integer> freeDays = new ArrayList<>();
            for (int day = 0; day < maxDays; day++) {
                if (!usedDays.contains(day)) {
                    freeDays.add(day);
                }
            }

            if (!freeDays.isEmpty()) {
                v.setArrivalDay(freeDays.get(GlobalRandom.rand.nextInt(freeDays.size())));
                changed = true;
            }
        }

        if (GlobalRandom.rand.nextDouble() < config.addCityMutationRate && tour.size() < config.maxTourLength) {
            List<City> available = new ArrayList<>();
            Set<Integer> used = new HashSet<>();

            for (Visit v : tour) {
                used.add(v.getCity().getIndex());
            }

            for (City c : cities) {
                if (!used.contains(c.getIndex())) {
                    available.add(c);
                }
            }

            if (!available.isEmpty()) {
                Set<Integer> usedDays = new HashSet<>();
                for (Visit v : tour) {
                    usedDays.add(v.getArrivalDay());
                }

                List<Integer> freeDays = new ArrayList<>();
                for (int day = 0; day < maxDays; day++) {
                    if (!usedDays.contains(day)) {
                        freeDays.add(day);
                    }
                }

                if (!freeDays.isEmpty()) {
                    City c = available.get(GlobalRandom.rand.nextInt(available.size()));
                    int day = freeDays.get(GlobalRandom.rand.nextInt(freeDays.size()));
                    tour.add(new Visit(c, day));
                    changed = true;
                }
            }
        }

        if (GlobalRandom.rand.nextDouble() < config.removeCityMutationRate && tour.size() > config.minTourLength) {
            tour.remove(GlobalRandom.rand.nextInt(tour.size()));
            changed = true;
        }

        if (!changed && !tour.isEmpty()) {
            Visit v = tour.get(GlobalRandom.rand.nextInt(tour.size()));

            Set<Integer> usedDays = new HashSet<>();
            for (Visit visit : tour) {
                if (visit != v) {
                    usedDays.add(visit.getArrivalDay());
                }
            }

            List<Integer> freeDays = new ArrayList<>();
            for (int day = 0; day < maxDays; day++) {
                if (!usedDays.contains(day)) {
                    freeDays.add(day);
                }
            }

            if (!freeDays.isEmpty()) {
                v.setArrivalDay(freeDays.get(GlobalRandom.rand.nextInt(freeDays.size())));
            }
        }

        tour.sort(Comparator.comparingInt(Visit::getArrivalDay));
        repairUniqueDays(ind, cities, maxDays, config);
    }

    public static void mutateStrong(Individual ind, List<City> cities, int maxDays, Config config) {
        mutate(ind, cities, maxDays, config);
        mutate(ind, cities, maxDays, config);

        List<Visit> tour = ind.getTour();
        if (!tour.isEmpty()) {
            Visit v = tour.get(GlobalRandom.rand.nextInt(tour.size()));

            Set<Integer> usedDays = new HashSet<>();
            for (Visit visit : tour) {
                if (visit != v) {
                    usedDays.add(visit.getArrivalDay());
                }
            }

            List<Integer> freeDays = new ArrayList<>();
            for (int day = 0; day < maxDays; day++) {
                if (!usedDays.contains(day)) {
                    freeDays.add(day);
                }
            }

            if (!freeDays.isEmpty()) {
                v.setArrivalDay(freeDays.get(GlobalRandom.rand.nextInt(freeDays.size())));
            }
        }

        tour.sort(Comparator.comparingInt(Visit::getArrivalDay));
        repairUniqueDays(ind, cities, maxDays, config);
    }

    private static void repairUniqueDays(Individual ind, List<City> cities, int maxDays, Config config) {
        List<Visit> tour = ind.getTour();

        while (tour.size() > maxDays) {
            tour.remove(GlobalRandom.rand.nextInt(tour.size()));
        }

        Set<Integer> usedDays = new HashSet<>();
        List<Visit> toFix = new ArrayList<>();

        for (Visit v : tour) {
            if (usedDays.contains(v.getArrivalDay())) {
                toFix.add(v);
            } else {
                usedDays.add(v.getArrivalDay());
            }
        }

        List<Integer> freeDays = new ArrayList<>();
        for (int day = 0; day < maxDays; day++) {
            if (!usedDays.contains(day)) {
                freeDays.add(day);
            }
        }

        Collections.shuffle(freeDays, GlobalRandom.rand);

        for (int i = 0; i < toFix.size() && i < freeDays.size(); i++) {
            toFix.get(i).setArrivalDay(freeDays.get(i));
        }

        Set<Integer> finalDays = new HashSet<>();
        Iterator<Visit> it = tour.iterator();
        while (it.hasNext()) {
            Visit v = it.next();
            if (finalDays.contains(v.getArrivalDay())) {
                it.remove();
            } else {
                finalDays.add(v.getArrivalDay());
            }
        }

        while (tour.size() > config.maxTourLength) {
            tour.remove(tour.size() - 1);
        }

        while (tour.size() < config.minTourLength) {
            Set<Integer> usedCityIds = new HashSet<>();
            Set<Integer> currentUsedDays = new HashSet<>();

            for (Visit v : tour) {
                usedCityIds.add(v.getCity().getIndex());
                currentUsedDays.add(v.getArrivalDay());
            }

            List<City> availableCities = new ArrayList<>();
            for (City c : cities) {
                if (!usedCityIds.contains(c.getIndex())) {
                    availableCities.add(c);
                }
            }

            List<Integer> currentFreeDays = new ArrayList<>();
            for (int d = 0; d < maxDays; d++) {
                if (!currentUsedDays.contains(d)) {
                    currentFreeDays.add(d);
                }
            }

            if (availableCities.isEmpty() || currentFreeDays.isEmpty()) {
                break;
            }

            City c = availableCities.get(GlobalRandom.rand.nextInt(availableCities.size()));
            int d = currentFreeDays.get(GlobalRandom.rand.nextInt(currentFreeDays.size()));
            tour.add(new Visit(c, d));
        }

        tour.sort(Comparator.comparingInt(Visit::getArrivalDay));
    }

    private static Visit cloneVisit(Visit v) {
        return new Visit(v.getCity(), v.getArrivalDay());
    }
}