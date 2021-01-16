package ticketingsystem;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IntervalFreeSeat {
    private ConcurrentHashMap<Integer, Seat> idleSeats = new ConcurrentHashMap<Integer, Seat>(); //free seat hash map
    private int[] counter = new int[2];

    public IntervalFreeSeat(Seat[] intervalSeat) {
        for (Seat element : intervalSeat) 
            idleSeats.put(element.getSeatID(), element);
        counter[0] = intervalSeat.length;
        counter[1] = intervalSeat.length;
    }

    public Seat getFreeSeat(int counterIndex) {
        while (true) {
            if (counter[counterIndex] == 0)
                return null;
            Iterator iter = idleSeats.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry entry = (Map.Entry)iter.next();
                Seat seat = (Seat)entry.getValue();
                if (seat.compareAndSet(false, true)) {
                    if (idleSeats.containsKey(seat.getSeatID()))
                        return seat;
                    else seat.compareAndSet(true, false);
                }
            }
        }
    }

    public int size() {
        return idleSeats.size();
    }

    public void remove(Seat seat) {
        if (idleSeats == null)
            return;
        idleSeats.remove(seat.getSeatID(), seat);
    }

    public void put(Seat newSeat) {
        idleSeats.put(newSeat.getSeatID(), newSeat);
    }

    public int getCounter(int currIndex) {
        return counter[currIndex];
    }

    public void addCounter(int currIndex) {
        counter[1-currIndex] = counter[currIndex] + 1;
    }

    public void subCounter(int currIndex) {
        counter[1-currIndex] = counter[currIndex] - 1;
    }

    public void syncCounter(int oldIndex) {
        counter[oldIndex] = counter[1-oldIndex];
    }

}
