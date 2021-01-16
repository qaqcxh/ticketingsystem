package ticketingsystem;

import java.util.concurrent.atomic.AtomicBoolean;

public class Seat implements Comparable<Seat>{
    private final int id; //seat id begin at 0
    private final int seats; //seats per coach
    private AtomicBoolean inUse;
    private int stationUse; //bit[i]==1 means this seat between statioin [i+1,i+2] is used

    //Construct function
    public Seat(int id, int seats) {
        this.id = id;
        this.seats = seats;
        inUse = new AtomicBoolean(false);
    }

    //lock functions
    public boolean compareAndSet(boolean expectValue, boolean newValue) {
        return inUse.compareAndSet(expectValue, newValue);
    }

    public boolean isFreeAt(int departure, int arrival) {
        return ((stationUse >> (departure - 1)) & ((1 << (arrival - departure)) - 1)) == 0;
    }

    public boolean isOccupiedAt(int departure, int arrival) {
        return (((-1) << (arrival - departure)) | (stationUse >> (departure - 1))) == -1;
    }

    public void freeAt(int departure, int arrival) {
        int mask = ~(((1 << (arrival - departure)) - 1) << (departure - 1));
        stationUse &= mask;
    }
    
    public void occupyAt(int departure, int arrival) {
        int mask = ((1 << (arrival - departure)) - 1) << (departure - 1);
        stationUse |= mask;
    }

    //get more detail of a seat info
    public int getSeatID() {
        return id;
    }

    public int getCoachNumber() {
        return id / seats + 1;
    }

    public int getSeatNumber() {
        return id % seats + 1;
    }

    @Override
    public int compareTo(Seat o) {
        return this.id - o.getSeatID();
    }
}

