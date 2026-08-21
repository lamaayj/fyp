package main.java;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class CityData {

    public static List<City> load(String csvPath) {
        List<City> cities = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(csvPath))) {
            String line = reader.readLine(); // header
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] parts = line.split(",");
                int index = Integer.parseInt(parts[0].trim());
                String name = parts[1].trim();
                double dailyCost = Double.parseDouble(parts[2].trim());
                double preference = Double.parseDouble(parts[3].trim());

                if (index == 0) continue; // home/hub node - not a visitable city

                cities.add(new City(name, index, dailyCost, preference));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load city data from " + csvPath, e);
        }
        System.out.println("Loaded " + cities.size() + " visitable cities from " + csvPath);
        return cities;
    }
}
