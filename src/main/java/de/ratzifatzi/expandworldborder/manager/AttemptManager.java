package de.ratzifatzi.expandworldborder.manager;

public final class AttemptManager {

    private int attemptCounter;

    public AttemptManager(int attemptCounter) {
        this.attemptCounter = Math.max(0, attemptCounter);
    }

    public int nextAttempt() {
        attemptCounter += 1;
        return attemptCounter;
    }

    public int getAttemptCounter() {
        return attemptCounter;
    }
}
