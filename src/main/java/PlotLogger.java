package main.java;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class PlotLogger {

    private final String populationCsvPath; // may be null - see class comment
    private final String paretoCsvPath;

    public PlotLogger(String populationCsvPath, String paretoCsvPath) {
        this.populationCsvPath = populationCsvPath;
        this.paretoCsvPath = paretoCsvPath;
        writeHeaders();
    }

    private void writeHeaders() {
        try {
            if (populationCsvPath != null) {
                try (BufferedWriter popWriter = new BufferedWriter(new FileWriter(populationCsvPath))) {
                    popWriter.write("generation,index,cost,preference,penalty,rank,crowdingDistance,feasible,route\n");
                }
            }
            try (BufferedWriter paretoWriter = new BufferedWriter(new FileWriter(paretoCsvPath))) {
                paretoWriter.write("generation,index,cost,preference,penalty,rank,crowdingDistance,feasible,route\n");
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create plot CSV files", e);
        }
    }

    public void logPopulation(int generation, List<Individual> population) {
        if (populationCsvPath == null) return; // logging disabled - no-op
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(populationCsvPath, true))) {
            int idx = 1;
            for (Individual ind : population) {
                writer.write(row(generation, idx, ind));
                writer.newLine();
                idx++;
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to log population CSV", e);
        }
    }

    public void logParetoFront(int generation, List<Individual> front) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(paretoCsvPath, true))) {
            int idx = 1;
            for (Individual ind : front) {
                writer.write(row(generation, idx, ind));
                writer.newLine();
                idx++;
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to log Pareto CSV", e);
        }
    }

    private String row(int generation, int index, Individual ind) {
        String feasible = ind.isFeasible() ? "true" : "false";
        String route = buildRouteString(ind);
        double crowd = Double.isInfinite(ind.crowdingDistance) ? 999999.0 : ind.crowdingDistance;

        return generation + "," +
                index + "," +
                ind.getCost() + "," +
                ind.getPreference() + "," +
                ind.getPenalty() + "," +
                ind.rank + "," +
                crowd + "," +
                feasible + ",\"" + route + "\"";
    }

    private String buildRouteString(Individual ind) {
        StringBuilder sb = new StringBuilder();
        List<Visit> tour = ind.getTour();

        for (int i = 0; i < tour.size(); i++) {
            Visit v = tour.get(i);
            sb.append(v.getCity().getName())
              .append("(Day ")
              .append(v.getArrivalDay())
              .append(")");
            if (i < tour.size() - 1) {
                sb.append(" -> ");
            }
        }

        return sb.toString();
    }
}
