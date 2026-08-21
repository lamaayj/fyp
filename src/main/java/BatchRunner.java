package main.java;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * Run with:
 *   java -cp out main.java.BatchRunner <citiesCsv> <flightsCsv> <resultsDir> [shape] [budget]
 *
 * Examples:
 *   java -cp out main.java.BatchRunner data/cities.csv data/flights.csv results_n200                       (linear, current budget - 64 configs)
 *   java -cp out main.java.BatchRunner data/cities.csv data/flights.csv results_n200_budgets linear all    (linear shape, all 3 budgets - 192 configs)
 *   java -cp out main.java.BatchRunner data/cities.csv data/flights.csv results_n200_all all all           (all shapes x all budgets - 1,152 configs)
 */
public class BatchRunner {

    static final int SEEDS_PER_CONFIG = 30;
    static final int[] SEEDS;
    static {
        SEEDS = new int[SEEDS_PER_CONFIG];
        for (int i = 0; i < SEEDS_PER_CONFIG; i++) SEEDS[i] = 1000 + i;
    }

    static final int POPULATION_SIZE = 100;
    static final int GENERATIONS = 150;
    static final int MAX_DAYS = 7;

    static final int STAGNATION_WINDOW = 10;
    static final double MUTATION_BOOST_FACTOR = 2.5;
    static final double RANDOM_IMMIGRANT_RATE = 0.1; // corrected - see class comment above

    static final String CITIES_CSV_DEFAULT = "data/cities.csv";
    static final String FLIGHTS_CSV_DEFAULT = "data/flights.csv";
    static final String RESULTS_DIR_DEFAULT = "results";
    static final String SHAPE_DEFAULT = "linear";
    static final String BUDGET_DEFAULT = "current";

    static class ObjectiveShape {
        String label;
        double costSlope, preferenceSlope;
        boolean nonlinearCost, nonlinearPreference;
        ObjectiveShape(String label, double costSlope, double preferenceSlope,
                       boolean nonlinearCost, boolean nonlinearPreference) {
            this.label = label;
            this.costSlope = costSlope;
            this.preferenceSlope = preferenceSlope;
            this.nonlinearCost = nonlinearCost;
            this.nonlinearPreference = nonlinearPreference;
        }
    }

    static final ObjectiveShape[] ALL_SHAPES = {
            new ObjectiveShape("linear",            1.0, 1.0, false, false),
            new ObjectiveShape("cost_slope_2.0",    2.0, 1.0, false, false),
            new ObjectiveShape("cost_slope_0.5",    0.5, 1.0, false, false),
            new ObjectiveShape("pref_slope_2.0",    1.0, 2.0, false, false),
            new ObjectiveShape("pref_slope_0.5",    1.0, 0.5, false, false),
            new ObjectiveShape("nonlinear_both",    1.0, 1.0, true,  true),
    };

    static class BudgetCondition {
        String label;
        double totalBudget, dailyBudget;
        BudgetCondition(String label, double totalBudget, double dailyBudget) {
            this.label = label;
            this.totalBudget = totalBudget;
            this.dailyBudget = dailyBudget;
        }
    }

    static final BudgetCondition[] ALL_BUDGETS = {
            new BudgetCondition("tight",   3000, 1000),
            new BudgetCondition("current", 5000, 1500),
            new BudgetCondition("loose",   8000, 2500),
    };

    public static void main(String[] args) throws IOException {
        String citiesCsv = args.length > 0 ? args[0] : CITIES_CSV_DEFAULT;
        String flightsCsv = args.length > 1 ? args[1] : FLIGHTS_CSV_DEFAULT;
        String resultsDir = args.length > 2 ? args[2] : RESULTS_DIR_DEFAULT;
        String shapeArg = args.length > 3 ? args[3] : SHAPE_DEFAULT;
        String budgetArg = args.length > 4 ? args[4] : BUDGET_DEFAULT;

        ObjectiveShape[] shapesToRun;
        if (shapeArg.equalsIgnoreCase("all")) {
            shapesToRun = ALL_SHAPES;
        } else {
            ObjectiveShape match = null;
            for (ObjectiveShape s : ALL_SHAPES) if (s.label.equals(shapeArg)) match = s;
            if (match == null) {
                System.out.println("Unknown shape '" + shapeArg + "'. Valid options: linear, cost_slope_2.0, "
                        + "cost_slope_0.5, pref_slope_2.0, pref_slope_0.5, nonlinear_both, all");
                return;
            }
            shapesToRun = new ObjectiveShape[]{match};
        }

        BudgetCondition[] budgetsToRun;
        if (budgetArg.equalsIgnoreCase("all")) {
            budgetsToRun = ALL_BUDGETS;
        } else {
            BudgetCondition match = null;
            for (BudgetCondition b : ALL_BUDGETS) if (b.label.equals(budgetArg)) match = b;
            if (match == null) {
                System.out.println("Unknown budget '" + budgetArg + "'. Valid options: tight, current, loose, all");
                return;
            }
            budgetsToRun = new BudgetCondition[]{match};
        }

        System.out.println("Using cities: " + citiesCsv);
        System.out.println("Using flights: " + flightsCsv);
        System.out.println("Writing results to: " + resultsDir);
        System.out.println("Objective shape(s): " + shapeArg);
        System.out.println("Budget condition(s): " + budgetArg);

        List<City> cities = CityData.load(citiesCsv);
        int numCities = cities.size();

        FlightMatrix fm = new FlightMatrix(numCities + 1, MAX_DAYS);
        FlightData.load(fm, flightsCsv);

        new java.io.File(resultsDir).mkdirs();
        try (BufferedWriter index = new BufferedWriter(new FileWriter(resultsDir + "/run_index.csv"))) {
            index.write("shapeLabel,costSlope,preferenceSlope,useNonlinearCost,useNonlinearPreference,"
                    + "budgetLabel,totalBudget,dailyBudget,"
                    + "configLabel,useCrowdingDistance,preserveExtremePoints,removeDuplicateObjectiveSolutions,"
                    + "useAdaptiveMutation,useRandomImmigrants,useDiscreteObjectives,"
                    + "seed,populationLogPath,paretoLogPath\n");

            boolean[] boolValues = {false, true};
            int totalRuns = shapesToRun.length * budgetsToRun.length * 64 * SEEDS_PER_CONFIG;
            int runNum = 0;

            for (ObjectiveShape shape : shapesToRun) {
                for (BudgetCondition budget : budgetsToRun) {

                    TourEvaluator evaluator = new TourEvaluator(budget.totalBudget, budget.dailyBudget, fm);

                    for (boolean crowding : boolValues) {
                        for (boolean extremes : boolValues) {
                            for (boolean dedupe : boolValues) {
                                for (boolean adaptiveMutation : boolValues) {
                                    for (boolean randomImmigrants : boolValues) {
                                        for (boolean discrete : boolValues) {

                                            String configLabel = "crowd" + (crowding ? "1" : "0")
                                                    + "_extreme" + (extremes ? "1" : "0")
                                                    + "_dedupe" + (dedupe ? "1" : "0")
                                                    + "_adaptmut" + (adaptiveMutation ? "1" : "0")
                                                    + "_immig" + (randomImmigrants ? "1" : "0")
                                                    + "_discrete" + (discrete ? "1" : "0");

                                            String configDir = resultsDir + "/" + shape.label + "/" + budget.label + "/" + configLabel;
                                            new java.io.File(configDir).mkdirs();

                                            for (int seed : SEEDS) {
                                                runNum++;
                                                System.out.println("[" + runNum + "/" + totalRuns + "] "
                                                        + shape.label + " " + budget.label + " " + configLabel + " seed=" + seed);

                                                Config config = new Config();
                                                config.useCrowdingDistance = crowding;
                                                config.preserveExtremePoints = extremes;
                                                config.removeDuplicateObjectiveSolutions = dedupe;
                                                config.useAdaptiveMutation = adaptiveMutation;
                                                config.stagnationWindow = STAGNATION_WINDOW;
                                                config.mutationBoostFactor = MUTATION_BOOST_FACTOR;
                                                config.useRandomImmigrants = randomImmigrants;
                                                config.randomImmigrantRate = RANDOM_IMMIGRANT_RATE;
                                                config.useDiscreteObjectives = discrete;

                                                config.costSlope = shape.costSlope;
                                                config.preferenceSlope = shape.preferenceSlope;
                                                config.useNonlinearCost = shape.nonlinearCost;
                                                config.useNonlinearPreference = shape.nonlinearPreference;

                                                config.minTourLength = 2;
                                                config.maxTourLength = 8;

                                                GlobalRandom.setSeed(seed);

                                                String popLogPath = configDir + "/seed" + seed + "_population_log.csv";
                                                String paretoLogPath = configDir + "/seed" + seed + "_pareto_log.csv";

                                                NsgaII nsga = new NsgaII(POPULATION_SIZE, GENERATIONS, cities, MAX_DAYS, evaluator, config);
                                                nsga.run(popLogPath, paretoLogPath, false);

                                                index.write(shape.label + "," + shape.costSlope + "," + shape.preferenceSlope + ","
                                                        + shape.nonlinearCost + "," + shape.nonlinearPreference + ","
                                                        + budget.label + "," + budget.totalBudget + "," + budget.dailyBudget + ","
                                                        + configLabel + "," + crowding + "," + extremes + "," + dedupe + ","
                                                        + adaptiveMutation + "," + randomImmigrants + "," + discrete + ","
                                                        + seed + "," + popLogPath + "," + paretoLogPath + "\n");
                                                index.flush();
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        System.out.println("Batch run complete. See " + resultsDir + "/run_index.csv");
    }
}
