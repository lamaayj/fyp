package main.java;

import java.util.HashMap;
import java.util.Map;


public class FlightMatrix {

    private final Map<Long, Double> prices = new HashMap<>();
    private final int numCities;
    private final int maxDays;

    public FlightMatrix(int numCities, int maxDays) {
        this.numCities = numCities;
        this.maxDays = maxDays;
    }

    private long key(int origin, int dest, int day) {

        return ((long) origin * (numCities + 1) + dest) * (maxDays + 1) + day;
    }

    public void setPrice(int origin, int dest, int day, double price) {
        prices.put(key(origin, dest, day), price);
    }

    public double getPrice(int origin, int dest, int day) {
        Double p = prices.get(key(origin, dest, day));
        return (p != null) ? p : Double.NaN;
    }

    public boolean hasRoute(int origin, int dest, int day) {
        return prices.containsKey(key(origin, dest, day));
    }

    public int size() {
        return prices.size();
    }
}
