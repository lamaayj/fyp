package main.java;

public class TournamentSelection {


    public static Individual select(Population pop){

        Individual a = pop.individuals.get(GlobalRandom.rand.nextInt(pop.size()));
        Individual b = pop.individuals.get(GlobalRandom.rand.nextInt(pop.size()));

        if(a.rank < b.rank) return a;
        if(b.rank < a.rank) return b;

        if(a.crowdingDistance > b.crowdingDistance) return a;
        if(b.crowdingDistance > a.crowdingDistance) return b;

        return GlobalRandom.rand.nextBoolean() ? a : b;
    }
}