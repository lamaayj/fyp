package main.java;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NsgaII {

    int populationSize;
    int generations;
    List<City> cities;
    int maxDays;
    TourEvaluator evaluator;
    Config config;

    public NsgaII(int popSize, int gens, List<City> cities, int maxDays,
                  TourEvaluator evaluator, Config config){
        this.populationSize = popSize;
        this.generations = gens;
        this.cities = cities;
        this.maxDays = maxDays;
        this.evaluator = evaluator;
        this.config = config;
    }

    public List<Individual> run(){
        return run("population_log.csv", "pareto_log.csv", true);
    }


    public List<Individual> run(String populationLogPath, String paretoLogPath, boolean verbose){

        PlotLogger logger = new PlotLogger(populationLogPath, paretoLogPath);
        Population population = Population.initialize(populationSize, cities, maxDays, config);
        population.evaluateAll(evaluator, config);

        List<List<Individual>> initialFronts = NonDominatedSorting.sort(population);
        for(List<Individual> front : initialFronts){
            if(config.useCrowdingDistance){
                CrowdingDistance.compute(front);
            }
        }
        logger.logPopulation(0, population.individuals);
        logger.logParetoFront(0, population.getParetoFront());

        double baseMutationRate = config.mutationRate;
        double baseAddCityRate = config.addCityMutationRate;
        double baseRemoveCityRate = config.removeCityMutationRate;
        double baseChangeDayRate = config.changeDayMutationRate;
        int stagnantGenerations = 0;
        int lastUniqueObjectiveCount = -1;

        for(int g = 0; g < generations; g++){

            if (config.useAdaptiveMutation) {
                Set<String> objKeys = new HashSet<>();
                for (Individual ind : population.individuals) objKeys.add(ind.getObjectiveKey());
                int uniqueCount = objKeys.size();

                stagnantGenerations = (uniqueCount <= lastUniqueObjectiveCount) ? stagnantGenerations + 1 : 0;
                lastUniqueObjectiveCount = uniqueCount;

                boolean boosting = stagnantGenerations >= config.stagnationWindow;
                double factor = boosting ? config.mutationBoostFactor : 1.0;
                config.mutationRate = Math.min(1.0, baseMutationRate * factor);
                config.addCityMutationRate = Math.min(1.0, baseAddCityRate * factor);
                config.removeCityMutationRate = Math.min(1.0, baseRemoveCityRate * factor);
                config.changeDayMutationRate = Math.min(1.0, baseChangeDayRate * factor);
            }

            Population offspring = new Population();


            Set<String> offspringRouteKeys = new HashSet<>();
            Set<String> offspringObjectiveKeys = new HashSet<>();

            int attempts = 0;
            int maxAttempts = populationSize * 20;

            while (offspring.size() < populationSize && attempts < maxAttempts) {
                attempts++;

                Individual parent1 = TournamentSelection.select(population);
                Individual parent2 = TournamentSelection.select(population);

                Individual child = GeneticOperators.crossover(parent1, parent2, cities, maxDays, config);
                if (child.getRouteKey().equals(parent1.getRouteKey()) ||
                        child.getRouteKey().equals(parent2.getRouteKey())) {
                    GeneticOperators.mutateStrong(child, cities, maxDays, config);
                } else {
                    GeneticOperators.mutate(child, cities, maxDays, config);
                }

                child.evaluate(evaluator, config);

                String routeKey = child.getRouteKey();
                String objectiveKey = child.getObjectiveKey();

                boolean duplicateRoute = offspringRouteKeys.contains(routeKey);
                boolean duplicateObjective = offspringObjectiveKeys.contains(objectiveKey);

                if (duplicateRoute) continue;
                if (config.removeDuplicateObjectiveSolutions && duplicateObjective) continue;

                offspring.individuals.add(child);
                offspringRouteKeys.add(routeKey);
                offspringObjectiveKeys.add(objectiveKey);
            }

            while (offspring.size() < populationSize) {
                Individual parent = TournamentSelection.select(population);
                Individual child = new Individual(copyTour(parent.getTour()));
                GeneticOperators.mutateStrong(child, cities, maxDays, config);
                child.evaluate(evaluator, config);
                offspring.individuals.add(child);
            }

            Population combined = population.merge(offspring);
            combined.evaluateAll(evaluator, config);
            population = combined.selectNextGeneration(populationSize, config);

            if (config.useRandomImmigrants) {
                int numImmigrants = (int) Math.round(config.randomImmigrantRate * populationSize);
                injectRandomImmigrants(population, numImmigrants);
            }

            printDiversityStats(population, g + 1);

            List<List<Individual>> fronts = NonDominatedSorting.sort(population);
            for(List<Individual> front : fronts){
                if(config.useCrowdingDistance){
                    CrowdingDistance.compute(front);
                }
            }
            logger.logPopulation(g + 1, population.individuals);
            logger.logParetoFront(g + 1, population.getParetoFront());

            if (verbose) {
                System.out.println("\n=== Generation " + (g + 1) + " ===");
                int i = 1;
                for (Individual ind : population.individuals) {
                    System.out.print("Ind " + i +
                            ": Cost=" + ind.getCost() +
                            ", Pref=" + ind.getPreference() +
                            ", Penalty=" + ind.getPenalty() +
                            ", Rank=" + ind.rank +
                            ", Crowd=" + ind.crowdingDistance +
                            " | Route: ");
                    for (Visit v : ind.getTour()) {
                        System.out.print(v.getCity().getName() + "(Day " + v.getArrivalDay() + ") ");
                    }
                    System.out.println();
                    i++;
                }
            }
        }

        NonDominatedSorting.sort(population);
        return population.getParetoFront();
    }


    private void injectRandomImmigrants(Population population, int numImmigrants) {
        if (numImmigrants <= 0) return;

        List<Individual> sorted = new ArrayList<>(population.individuals);
        sorted.sort((a, b) -> {
            if (a.rank != b.rank) return Integer.compare(b.rank, a.rank); // worst rank first
            return Double.compare(a.crowdingDistance, b.crowdingDistance); // lowest crowding first
        });

        for (int i = 0; i < numImmigrants && i < sorted.size(); i++) {
            Individual worst = sorted.get(i);
            Individual immigrant = Individual.randomIndividual(cities, maxDays, config);
            immigrant.evaluate(evaluator, config);
            int idx = population.individuals.indexOf(worst);
            if (idx >= 0) {
                population.individuals.set(idx, immigrant);
            }
        }
    }

    private List<Visit> copyTour(List<Visit> original) {
        List<Visit> copy = new ArrayList<>();
        for (Visit v : original) {
            copy.add(new Visit(v.getCity(), v.getArrivalDay()));
        }
        return copy;
    }
    private void printDiversityStats(Population population, int generation) {
        Set<String> routeKeys = new HashSet<>();
        Set<String> objectiveKeys = new HashSet<>();

        for (Individual ind : population.individuals) {
            routeKeys.add(ind.getRouteKey());
            objectiveKeys.add(ind.getObjectiveKey());
        }

        System.out.println("Generation " + generation
                + " | Population=" + population.size()
                + " | Unique routes=" + routeKeys.size()
                + " | Unique objectives=" + objectiveKeys.size());
    }

}