package main.java;

public class Config {

    // Population settings
    public int minTourLength = 1;
    public int maxTourLength = 3;
    public int populationSize = 50;
    public int generations = 100;


    // Diversity settings
    public boolean useCrowdingDistance = true;
    public boolean preserveExtremePoints = false;
    public boolean removeDuplicateObjectiveSolutions = false;


    // Objective shaping
    public boolean useDiscreteObjectives = false;
    public boolean useNonlinearCost = false;
    public boolean useNonlinearPreference = false;
    public double costSlope = 1.0;
    public double preferenceSlope = 1.0;


    // Constraint handling
    public boolean usePenalty = true;
    public double penaltyWeight = 1.0;


    // Variation settings
    public double mutationRate = 0.2;
    public double addCityMutationRate = 0.1;
    public double removeCityMutationRate = 0.1;
    public double changeDayMutationRate = 0.2;



    public boolean useAdaptiveMutation = false;
    public int stagnationWindow = 10;
    public double mutationBoostFactor = 2.5;

    public boolean useRandomImmigrants = false;
    public double randomImmigrantRate = 0.1;

}

