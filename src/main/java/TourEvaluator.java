package main.java;

import java.util.List;

public class TourEvaluator {

    private double totalBudget;
    private double dailyBudget;
    private FlightMatrix matrix;

    private static final double MISSING_ROUTE_PENALTY = 100_000.0;

    public TourEvaluator(double totalBudget,
                         double dailyBudget,
                         FlightMatrix matrix) {
        this.totalBudget = totalBudget;
        this.dailyBudget = dailyBudget;
        this.matrix = matrix;
    }


    private double lookupPrice(int origin, int dest, int day, double[] penaltyAccumulator) {
        double price = matrix.getPrice(origin, dest, day);
        if (Double.isNaN(price)) {
            penaltyAccumulator[0] += MISSING_ROUTE_PENALTY;
            return MISSING_ROUTE_PENALTY;
        }
        return price;
    }

    public EvaluationResult evaluate(List<Visit> tour) {

        double totalCost = 0;
        double totalPreference = 0;
        double[] penalty = {0};

        if (tour.isEmpty())
            return new EvaluationResult(0, 0, 0);

        // Home -> first city
        Visit first = tour.get(0);
        int departureDay = first.getArrivalDay();
        double firstFlight = lookupPrice(0, first.getCity().getIndex(), departureDay, penalty);

        totalCost += firstFlight;

        if (firstFlight > dailyBudget)
            penalty[0] += (firstFlight - dailyBudget);

        totalCost += first.getCity().getDailyCost();
        totalPreference += first.getCity().getPreference();

        if (first.getCity().getDailyCost() > dailyBudget)
            penalty[0] += (first.getCity().getDailyCost() - dailyBudget);

        // Internal flights
        for (int i = 1; i < tour.size(); i++) {

            Visit prev = tour.get(i - 1);
            Visit curr = tour.get(i);

            double flightCost = lookupPrice(prev.getCity().getIndex(),
                    curr.getCity().getIndex(), curr.getArrivalDay(), penalty);

            totalCost += flightCost;

            if (flightCost > dailyBudget)
                penalty[0] += (flightCost - dailyBudget);

            totalCost += curr.getCity().getDailyCost();
            totalPreference += curr.getCity().getPreference();

            if (curr.getCity().getDailyCost() > dailyBudget)
                penalty[0] += (curr.getCity().getDailyCost() - dailyBudget);
        }

        // Return to home
        Visit last = tour.get(tour.size() - 1);
        double returnFlight = lookupPrice(last.getCity().getIndex(), 0, last.getArrivalDay(), penalty);

        totalCost += returnFlight;

        if (returnFlight > dailyBudget)
            penalty[0] += (returnFlight - dailyBudget);

        // Global budget constraint
        if (totalCost > totalBudget)
            penalty[0] += (totalCost - totalBudget);

        return new EvaluationResult(totalCost, totalPreference, penalty[0]);
    }
}
