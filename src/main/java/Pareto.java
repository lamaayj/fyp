package main.java;

public class Pareto {
    // Returns true if a dominates b
    public static boolean dominates(EvaluationResult a, EvaluationResult b) {
        boolean betterOrEqual = (a.totalCost <= b.totalCost) && (a.totalPreference >= b.totalPreference);
        boolean strictlyBetter = (a.totalCost < b.totalCost) || (a.totalPreference > b.totalPreference);
        return betterOrEqual && strictlyBetter;
    }
}