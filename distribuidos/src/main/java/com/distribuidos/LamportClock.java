package com.distribuidos;

public class LamportClock {
    private long clock = 0L;


    // local event: increment clock when something happens locally
    public synchronized void localEvent() {
        clock++;
    }

    // when sending a message (we increment and return the value)
    public synchronized long sendMessage() {
        clock++;
        return clock;
    }

    // when receiving: take max + 1
    public synchronized void receiveMessage(long receivedTimestamp) {
        clock = Math.max(clock, receivedTimestamp) + 1; // 
    }

    // get current clock value
    public synchronized long getClock() {
        return clock;
    }

    public synchronized void reset() {
        clock = 0L;
    }
}
