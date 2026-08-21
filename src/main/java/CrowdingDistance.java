package main.java;

import java.util.Comparator;
import java.util.List;

public class CrowdingDistance {

    public static void compute(List<Individual> front){
        int size = front.size();
        if(size == 0) return;
        if(size == 1){
            front.get(0).crowdingDistance = Double.POSITIVE_INFINITY;
            return;
        }
        if(size == 2){
            front.get(0).crowdingDistance = Double.POSITIVE_INFINITY;
            front.get(1).crowdingDistance = Double.POSITIVE_INFINITY;
            return;
        }

        for(Individual ind : front){
            ind.crowdingDistance = 0.0;
        }

        // Minimise cost
        front.sort(Comparator.comparingDouble(Individual::getCost));
        front.get(0).crowdingDistance = Double.POSITIVE_INFINITY;
        front.get(size - 1).crowdingDistance = Double.POSITIVE_INFINITY;

        double minCost = front.get(0).getCost();
        double maxCost = front.get(size - 1).getCost();
        double costRange = maxCost - minCost;

        if(costRange > 0){
            for(int i = 1; i < size - 1; i++){
                double distance = (front.get(i + 1).getCost() - front.get(i - 1).getCost()) / costRange;
                if(!Double.isInfinite(front.get(i).crowdingDistance)){
                    front.get(i).crowdingDistance += distance;
                }
            }
        }

        // Maximise preference
        front.sort(Comparator.comparingDouble(Individual::getPreference));
        front.get(0).crowdingDistance = Double.POSITIVE_INFINITY;
        front.get(size - 1).crowdingDistance = Double.POSITIVE_INFINITY;

        double minPref = front.get(0).getPreference();
        double maxPref = front.get(size - 1).getPreference();
        double prefRange = maxPref - minPref;

        if(prefRange > 0){
            for(int i = 1; i < size - 1; i++){
                double distance = (front.get(i + 1).getPreference() - front.get(i - 1).getPreference()) / prefRange;
                if(!Double.isInfinite(front.get(i).crowdingDistance)){
                    front.get(i).crowdingDistance += distance;
                }
            }
        }
    }
}