package main.java;

import java.util.*;

public class Population {

    public List<Individual> individuals = new ArrayList<>();

    public static Population initialize(int size, List<City> cities, int maxDays, Config config){
        Population pop = new Population();
        for(int i=0; i<size; i++){
            pop.individuals.add(Individual.randomIndividual(cities, maxDays, config));
        }
        return pop;
    }

    public void evaluateAll(TourEvaluator evaluator, Config config){
        for(Individual ind : individuals){
            ind.evaluate(evaluator, config);
        }
    }

    public int size(){
        return individuals.size();
    }

    public Population merge(Population other){
        Population combined = new Population();
        combined.individuals.addAll(this.individuals);
        combined.individuals.addAll(other.individuals);
        return combined;
    }

    public Population selectNextGeneration(int n, Config config){
        Population next = new Population();

        List<List<Individual>> fronts = NonDominatedSorting.sort(this);
        Set<String> seenRoutes = new HashSet<>();
        Set<String> seenObjectives = new HashSet<>();

        if(config.preserveExtremePoints){
            preserveExtremeIndividuals(fronts, next, n, seenRoutes, seenObjectives, config);
        }

        for(List<Individual> front : fronts){
            if(config.useCrowdingDistance){
                CrowdingDistance.compute(front);
            }

            if(config.useCrowdingDistance){
                front.sort((a, b) -> Double.compare(b.crowdingDistance, a.crowdingDistance));
            } else {
                //front.sort(Comparator.comparingDouble(Individual::getCost));
                front.sort((a, b) -> 0);
                Collections.shuffle(front, GlobalRandom.rand);
            }

            for(Individual ind : front){
                if(next.size() >= n){
                    return next;
                }

                String routeKey = ind.getRouteKey();
                String objectiveKey = ind.getObjectiveKey();

                if(seenRoutes.contains(routeKey)){
                    continue;
                }

                if(config.removeDuplicateObjectiveSolutions && seenObjectives.contains(objectiveKey)){
                    continue;
                }

                next.individuals.add(ind);
                seenRoutes.add(routeKey);
                seenObjectives.add(objectiveKey);
            }
        }

        return next;
    }

    private void preserveExtremeIndividuals(List<List<Individual>> fronts,
                                            Population next,
                                            int n,
                                            Set<String> seenRoutes,
                                            Set<String> seenObjectives,
                                            Config config){
        if(fronts.isEmpty() || fronts.get(0).isEmpty()) return;

        List<Individual> firstFront = fronts.get(0);

        Individual minCost = firstFront.stream()
                .min(Comparator.comparingDouble(Individual::getCost))
                .orElse(null);

        Individual maxPreference = firstFront.stream()
                .max(Comparator.comparingDouble(Individual::getPreference))
                .orElse(null);

        if(minCost != null && next.size() < n){
            String routeKey = minCost.getRouteKey();
            String objectiveKey = minCost.getObjectiveKey();

            if(!seenRoutes.contains(routeKey) &&
                    (!config.removeDuplicateObjectiveSolutions || !seenObjectives.contains(objectiveKey))) {
                next.individuals.add(minCost);
                seenRoutes.add(routeKey);
                seenObjectives.add(objectiveKey);
            }
        }

        if(maxPreference != null && next.size() < n){
            String routeKey = maxPreference.getRouteKey();
            String objectiveKey = maxPreference.getObjectiveKey();

            if(!seenRoutes.contains(routeKey) &&
                    (!config.removeDuplicateObjectiveSolutions || !seenObjectives.contains(objectiveKey))) {
                next.individuals.add(maxPreference);
                seenRoutes.add(routeKey);
                seenObjectives.add(objectiveKey);
            }
        }
    }


    public List<Individual> getParetoFront(){
        List<Individual> front = new ArrayList<>();
        for(Individual ind : individuals){
            if(ind.rank == 1){
                front.add(ind);
            }
        }
        return front;
    }
}