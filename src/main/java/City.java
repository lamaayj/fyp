package main.java;
public class City {
    private String name;
    private int index;
    private double dailyCost;
    private double preference;

    public City(String name, int index, double dailyCost, double preference) {
        this.name = name;
        this.index = index;
        this.dailyCost = dailyCost;
        this.preference = preference;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public double getDailyCost() {
        return dailyCost;
    }

    public void setDailyCost(double dailyCost) {
        this.dailyCost = dailyCost;
    }

    public double getPreference() {
        return preference;
    }

    public void setPreference(double preference) {
        this.preference = preference;
    }

}