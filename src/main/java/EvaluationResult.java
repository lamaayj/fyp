package main.java;

public class EvaluationResult {
    public double totalCost;
    public double totalPreference;
    public double penalty;

    public EvaluationResult(double totalCost, double totalPreference, double penalty) {
        this.totalCost = totalCost;
        this.totalPreference = totalPreference;
        this.penalty = penalty;
    }
}