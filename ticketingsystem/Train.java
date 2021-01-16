package ticketingsystem;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicStampedReference;
import java.util.concurrent.locks.ReentrantLock;

public class Train {
    private int routeNum;
    private int seatNum;
    private int stationNum;
    private Seat[] routeSeats;
    private static AtomicLong tidAllocator = new AtomicLong(1);
    private IntervalFreeSeat[][] intervalSeats;
    private ConcurrentHashMap<Long, MyTicket> soldTickets; //tickets that are been sold
    
    private AtomicStampedReference<Integer> counterIndex = new AtomicStampedReference<Integer>(0,0);
    private ReentrantLock trainLock;
    
    private ThreadLocal<int[]> overlapIntervals = ThreadLocal.withInitial(()-> {
        return new int[(stationNum+5)*(stationNum+5)];
    });

    public Train(int routeNum, int coachNum, int seatNum, int stationNum) {
        this.routeNum = routeNum;
        this.seatNum = seatNum;
        this.stationNum = stationNum;
        trainLock = new ReentrantLock();
        soldTickets = new ConcurrentHashMap<Long, MyTicket>();
        
        routeSeats = new Seat[coachNum*seatNum];
        for (int i = 0; i < coachNum*seatNum; i++)
            routeSeats[i] = new Seat(i, seatNum);
        
        intervalSeats = new IntervalFreeSeat[stationNum][stationNum];
        for (int i = 0; i < stationNum; i++)
            for (int j = 0; j < stationNum; j++)
                intervalSeats[i][j] = new IntervalFreeSeat(routeSeats);
    }

    public int inquiry(int departure, int arrival) {
        if (departure < 1 || departure > stationNum ||
            arrival < 1 || arrival > stationNum ||
            departure >= arrival)
            return 0;

        while (true) {
            int[] oldStamp = new int[1];
            int[] newStamp = new int[1];

            int index = counterIndex.get(oldStamp);
            int counter = intervalSeats[departure-1][arrival-1].getCounter(index);

            //check if index has been changed
            counterIndex.get(newStamp);
            if (oldStamp[0] == newStamp[0])
                return counter;
        }
    }

    public Ticket buyTicket(String passenger, int departure, int arrival) {
        if (departure < 1 || departure > stationNum ||
            arrival < 1 || arrival > stationNum ||
            departure >= arrival)
            return null;

        // note this seat is marked in use
        Seat freeSeat = intervalSeats[departure-1][arrival-1].getFreeSeat(counterIndex.getReference());
        if (freeSeat == null)
            return null;

        int[] overlap = overlapIntervals.get();
        int size = 0;
        for (int i = 0; i < departure - 1; i++)
            for (int j = departure; j < stationNum; j++)
                if (freeSeat.isFreeAt(i+1, j+1)) {
                    overlap[size++] = (i << 10) | j;
                    intervalSeats[i][j].remove(freeSeat);
                }

        for (int i = departure - 1; i < arrival - 1; i++)
            for (int j = i + 1; j < stationNum; j++)
                if (freeSeat.isFreeAt(i+1, j+1)) {
                    overlap[size++] = (i << 10) | j;
                    intervalSeats[i][j].remove(freeSeat);
                }

        freeSeat.occupyAt(departure, arrival);

        //update all intesect intervals counter
        trainLock.lock();
        int[] oldStamp = new int[1];
        int index = counterIndex.get(oldStamp);

        for (int i = 0; i < size; i++)
            intervalSeats[overlap[i] >> 10][overlap[i] & 0x3ff].subCounter(index);

        //reverse counter index
        counterIndex.set(1-index, oldStamp[0] + 1);
        freeSeat.compareAndSet(true, false);

        //sync counter of all intersect interval
        for (int i = 0; i < size; i++)
            intervalSeats[overlap[i] >> 10][overlap[i] & 0x3ff].syncCounter(index);

        trainLock.unlock();

        //construct ticket
        Ticket ticket = new Ticket();
        ticket.tid = tidAllocator.getAndIncrement();
        ticket.passenger = passenger;
        ticket.route = routeNum;
        ticket.coach = freeSeat.getCoachNumber();
        ticket.seat = freeSeat.getSeatNumber();
        ticket.departure = departure;
        ticket.arrival = arrival;

        //record this bought ticket to soldTicket
        soldTickets.put(ticket.tid, new MyTicket(ticket));

        return ticket;
    }
        
    public boolean refundTicket(Ticket ticket) {    
        //check if this ticket is valid
        MyTicket myTicket = soldTickets.get(ticket.tid);
        if (myTicket == null || !myTicket.equals(ticket))
            return false;

        if (soldTickets.remove(myTicket.tid, myTicket) == false) //removed by previous thread
            return false;
        
        //lock this seat by CAS and put it to other intesect interval
        int seatID = (ticket.coach - 1) * seatNum + ticket.seat - 1;
        int departure = ticket.departure;
        int arrival = ticket.arrival;
        Seat freeSeat = routeSeats[seatID];
        while (freeSeat.compareAndSet(false, true) == false)
            continue;
        freeSeat.freeAt(departure, arrival);

        int[] overlap = overlapIntervals.get();
        int size = 0;
        for (int i = 0; i < departure - 1; i++)
            for (int j = departure; j < stationNum; j++)
                if (freeSeat.isFreeAt(i+1, j+1)) {
                    overlap[size++] = (i << 10) | j;
                    intervalSeats[i][j].put(freeSeat);
                }

        for (int i = departure - 1; i < arrival - 1; i++)
            for (int j = i + 1; j < stationNum; j++)
                if (freeSeat.isFreeAt(i+1, j+1)) {
                    overlap[size++] = (i << 10) | j;
                    intervalSeats[i][j].put(freeSeat);
                }


        //update counter
        trainLock.lock();
        int[] oldStamp = new int[1];
        int index = counterIndex.get(oldStamp);

        for (int i = 0; i < size; i++)
            intervalSeats[overlap[i] >> 10][overlap[i] & 0x3ff].addCounter(index);

        //reverse counter index
        counterIndex.set(1-index, oldStamp[0] + 1);
        freeSeat.compareAndSet(true, false);

        //sync counter
        for (int i = 0; i < size; i++)
            intervalSeats[overlap[i] >> 10][overlap[i] & 0x3ff].syncCounter(index);

        trainLock.unlock();
        return true;
    }
}

class MyTicket extends Ticket {
    public MyTicket(Ticket ticket) {
        this.tid = ticket.tid;
        this.passenger = ticket.passenger;
        this.route = ticket.route;
        this.coach = ticket.coach;
        this.seat = ticket.seat;
        this.departure = ticket.departure;
        this.arrival = ticket.arrival;
     }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Ticket || o instanceof MyTicket) {
            Ticket target = (Ticket)o;
            return this.tid == target.tid && 
                   this.passenger.equals(target.passenger) &&
                   this.route == target.route &&
                   this.coach == target.coach &&
                   this.seat == target.seat &&
                   this.departure == target.departure &&
                   this.arrival == target.arrival;
        }
        return false;
    }
}
