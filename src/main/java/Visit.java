package main.java;

public class Visit {

    private City city;
    private int arrivalDay;

    public Visit(City city, int arrivalDay) {
        this.city = city;
        this.arrivalDay = arrivalDay;
    }

    public City getCity() { return city; }
    public int getArrivalDay() { return arrivalDay; }

    public void setArrivalDay(int day) {
        this.arrivalDay = day;
    }
}