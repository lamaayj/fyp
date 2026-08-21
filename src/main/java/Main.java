package main.java;

import java.util.List;

public class Main {

    public static void main(String[] args) {

        int populationSize = 100;
        int generations = 150;
        int maxDays = 7;


        double totalBudget = 5000;
        double dailyBudget = 1500;

        String citiesCsv = "data/cities.csv";
        String flightsCsv = "data/flights.csv";

        List<City> cities = CityData.load(citiesCsv);
        int numCities = cities.size();

        FlightMatrix fm = new FlightMatrix(numCities + 1, maxDays);
        FlightData.load(fm, flightsCsv);

        TourEvaluator evaluator = new TourEvaluator(totalBudget, dailyBudget, fm);

        Config config = new Config();
        config.useCrowdingDistance = true;
        config.preserveExtremePoints = false;
        config.removeDuplicateObjectiveSolutions = false;
        config.useDiscreteObjectives = true;
        config.useNonlinearCost = false;
        config.useNonlinearPreference = false;
        config.costSlope = 1.0;
        config.preferenceSlope = 1.0;
        config.minTourLength = 2;
        config.maxTourLength = 8;

        GlobalRandom.setSeed(40);

        NsgaII nsga = new NsgaII(populationSize, generations, cities, maxDays, evaluator, config);
        List<Individual> result = nsga.run();

        System.out.println("\nFinal Pareto Front:");
        for (Individual ind : result) {
            System.out.print("Cost: " + ind.getCost() +
                    " Preference: " + ind.getPreference() +
                    " Penalty: " + ind.getPenalty() +
                    " Route: ");
            for (Visit v : ind.getTour()) {
                System.out.print(v.getCity().getName() + "(Day " + v.getArrivalDay() + ") ");
            }
            System.out.println();
        }
    }
}
