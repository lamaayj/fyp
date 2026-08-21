package main.java;

import java.util.ArrayList;
import java.util.List;

public class NonDominatedSorting {

    public static List<List<Individual>> sort(Population pop){
        List<List<Individual>> fronts = new ArrayList<>();
        List<Individual> firstFront = new ArrayList<>();

        for(Individual p : pop.individuals){
            p.dominationCount = 0;
            p.dominatedSet.clear();

            for(Individual q : pop.individuals){
                if(p == q) continue;

                if(p.dominates(q)){
                    p.dominatedSet.add(q);
                } else if(q.dominates(p)){
                    p.dominationCount++;
                }
            }

            if(p.dominationCount == 0){
                p.rank = 1;
                firstFront.add(p);
            }
        }

        fronts.add(firstFront);

        int i = 0;
        while(i < fronts.size() && !fronts.get(i).isEmpty()){
            List<Individual> nextFront = new ArrayList<>();

            for(Individual p : fronts.get(i)){
                for(Individual q : p.dominatedSet){
                    q.dominationCount--;
                    if(q.dominationCount == 0){
                        q.rank = i + 2;
                        nextFront.add(q);
                    }
                }
            }

            if(!nextFront.isEmpty()){
                fronts.add(nextFront);
            }
            i++;
        }

        return fronts;
    }
}