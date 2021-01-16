package ticketingsystem;

public class TicketingDS implements TicketingSystem {
    private Train[] trains;
    public TicketingDS(int routenum, int coachnum, int seatnum, 
                       int stationnum, int threadnum) {
        trains = new Train[routenum];
        for (int i = 0; i < routenum; i++)
        	trains[i] = new Train(i+1, coachnum, seatnum, stationnum);
    }
    
    public int inquiry(int route, int departure, int arrival) {
        //System.out.println("inq " + route + " " + departure + " " + arrival);//debug
        return trains[route-1].inquiry(departure, arrival);
    }

	public Ticket buyTicket(String passenger, int route, int departure, int arrival) {
        //System.out.println("buy " + passenger + " " + route + " " + departure + " " + arrival);//debug
        return trains[route-1].buyTicket(passenger, departure, arrival);
    }

    public boolean refundTicket(Ticket ticket) {
        //System.out.println("refund " + ticket.tid);
        if (ticket == null || ticket.route < 1 || ticket.route > trains.length)
        	return false;
        return trains[ticket.route-1].refundTicket(ticket);
    }
}


