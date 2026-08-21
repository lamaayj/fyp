package main.java;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;


public class FlightData {

    public static void load(FlightMatrix fm, String csvPath) {
        int rowsLoaded = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(csvPath))) {
            String line = reader.readLine(); // header
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] parts = line.split(",");
                int origin = Integer.parseInt(parts[0].trim());
                int dest = Integer.parseInt(parts[1].trim());
                int day = Integer.parseInt(parts[2].trim());
                double price = Double.parseDouble(parts[3].trim());
                fm.setPrice(origin, dest, day, price);
                rowsLoaded++;
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load flight data from " + csvPath, e);
        }
        System.out.println("Loaded " + rowsLoaded + " flight price entries from " + csvPath
                + " (matrix now holds " + fm.size() + " routes)");
    }
}
