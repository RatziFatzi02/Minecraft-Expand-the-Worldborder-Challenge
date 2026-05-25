package de.ratzifatzi.expandworldborder.manager;

import java.time.Instant;

public final class TimerManager {

    private long globalElapsedSeconds;
    private long runElapsedSeconds;
    private long globalStartedAtEpochSecond = -1L;
    private long runStartedAtEpochSecond = -1L;

    public TimerManager(long globalElapsedSeconds, long runElapsedSeconds) {
        this.globalElapsedSeconds = Math.max(0L, globalElapsedSeconds);
        this.runElapsedSeconds = Math.max(0L, runElapsedSeconds);
    }

    public void resumeForRunningState() {
        if (!isGlobalTimerRunning()) {
            globalStartedAtEpochSecond = now();
        }
        if (!isRunTimerRunning()) {
            runStartedAtEpochSecond = now();
        }
    }

    public void pauseForPauseState() {
        stopRunTimer();
        stopGlobalTimer();
    }

    public void startNewRunTimer() {
        if (!isGlobalTimerRunning()) {
            globalStartedAtEpochSecond = now();
        }
        runElapsedSeconds = 0L;
        runStartedAtEpochSecond = now();
    }

    public void stopRunTimer() {
        if (!isRunTimerRunning()) {
            return;
        }
        runElapsedSeconds += now() - runStartedAtEpochSecond;
        runStartedAtEpochSecond = -1L;
    }

    public void stopGlobalTimer() {
        if (!isGlobalTimerRunning()) {
            return;
        }
        globalElapsedSeconds += now() - globalStartedAtEpochSecond;
        globalStartedAtEpochSecond = -1L;
    }

    public boolean isGlobalTimerRunning() {
        return globalStartedAtEpochSecond >= 0L;
    }

    public boolean isRunTimerRunning() {
        return runStartedAtEpochSecond >= 0L;
    }

    public long getGlobalElapsedSeconds() {
        if (!isGlobalTimerRunning()) {
            return globalElapsedSeconds;
        }
        return globalElapsedSeconds + now() - globalStartedAtEpochSecond;
    }

    public long getRunElapsedSeconds() {
        if (!isRunTimerRunning()) {
            return runElapsedSeconds;
        }
        return runElapsedSeconds + now() - runStartedAtEpochSecond;
    }

    private long now() {
        return Instant.now().getEpochSecond();
    }
}
