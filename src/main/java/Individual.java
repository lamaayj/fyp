package main.java;

import java.util.*;

public class Individual {

    private List<Visit> tour;
    private double cost;
    private double preference;
    double penalty;
    public int rank;
    public double crowdingDistance;
    public int dominationCount;
    public List<Individual> dominatedSet = new ArrayList<>();


    public Individual(List<Visit> tour){
        this.tour = tour;
    }

    public void evaluate(TourEvaluator evaluator, Config config){

        EvaluationResult result = evaluator.evaluate(tour);

        double adjustedCost = result.totalCost;
        double adjustedPreference = result.totalPreference;

        adjustedCost = Math.pow(adjustedCost, config.costSlope);
        adjustedPreference = Math.pow(adjustedPreference, config.preferenceSlope);

        if(config.useNonlinearCost){
            adjustedCost = adjustedCost + 0.05 * adjustedCost * adjustedCost;
        }
        if(config.useNonlinearPreference){
            adjustedPreference = Math.log(adjustedPreference + 1.0) * 10.0;
        }
        if(config.useDiscreteObjectives){
            adjustedCost = Math.round(adjustedCost);
            adjustedPreference = Math.round(adjustedPreference);
        }

        this.cost = adjustedCost;
        this.preference = adjustedPreference;
        this.penalty = config.usePenalty ? result.penalty * config.penaltyWeight : 0.0;
    }

    public double getCost(){
        return cost;
    }

    public double getPreference(){
        return preference;
    }

    public List<Visit> getTour() {
        return tour;
    }

    public double getPenalty() {
        return penalty;
    }

    public boolean isFeasible() {
        return penalty <= 0.000001;
    }

    public boolean dominates(Individual other){

        if(this.isFeasible() && !other.isFeasible()) return true;
        if(!this.isFeasible() && other.isFeasible()) return false;

        if(!this.isFeasible() && !other.isFeasible()){
            return this.penalty < other.penalty;
        }

        boolean betterOrEqualCost = this.cost <= other.cost;
        boolean betterOrEqualPreference = this.preference >= other.preference;

        boolean strictlyBetter =
                this.cost < other.cost ||
                        this.preference > other.preference;

        return betterOrEqualCost && betterOrEqualPreference && strictlyBetter;
    }

    public static Individual randomIndividual(List<City> cities, int maxDays, Config config) {
        int upper = Math.min(config.maxTourLength, cities.size());
        int lower = Math.min(config.minTourLength, upper);
        int tourLength = lower + GlobalRandom.rand.nextInt(upper - lower + 1);

        List<City> selectedCities = new ArrayList<>(cities);
        List<Visit> tour = new ArrayList<>();
        Set<Integer> usedDays = new HashSet<>();

        Collections.shuffle(selectedCities, GlobalRandom.rand);
        selectedCities = selectedCities.subList(0, tourLength);



        for(City c : selectedCities){
            int day;
            do {
                day = GlobalRandom.rand.nextInt(maxDays);
            } while(usedDays.contains(day) && usedDays.size() < maxDays);

            usedDays.add(day);
            tour.add(new Visit(c, day));
        }
        tour.sort(Comparator.comparingInt(Visit::getArrivalDay));


        return new Individual(tour);
    }

    public String getRouteKey() {
        StringBuilder sb = new StringBuilder();
        for (Visit v : tour) {
            sb.append(v.getCity().getIndex())
                    .append("@")
                    .append(v.getArrivalDay())
                    .append("->");
        }
        return sb.toString();
    }

    public String getObjectiveKey() {
        return String.format("%.4f|%.4f|%.4f", getCost(), getPreference(), getPenalty());
    }

}