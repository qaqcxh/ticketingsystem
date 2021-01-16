package ticketingsystem;

import java.util.ArrayList;
import java.util.Scanner;
import java.util.Stack;
import java.util.TreeSet;
import java.util.Vector;

enum Action {
    BUY, INQ, REF, SOLDOUT;
}

class Ticket implements Comparable<Ticket>{
	long tid;
	String passenger;
	int route;
	int coach;
	int seat;
	int departure;
	int arrival;

    @Override 
    public int compareTo(Ticket t) {
        if (this.tid < t.tid)
            return -1;
        else if (this.tid == t.tid)
            return 0;
        else return 1;
    }
}

class Node {
    public int id;
    public long preTime, postTime;
    public Action action;
    public boolean visited;
    
    public int threadID;
    public Ticket ticket; // buy/refund info
    public int route, departure, arrival, leftTicket; //inq info

    public ArrayList<Integer> inEdges;
    public int inDegree;

    void print() {
        //System.out.printf("node id %d ", id);//debug
        switch (action) {
            case BUY:
                System.out.printf("BUY ticket %d departure %d arrival %d\n", ticket.tid, ticket.departure, ticket.arrival);
                break;
            case REF:
                System.out.printf("REF ticket %d departure %d arrival %d\n", ticket.tid, ticket.departure, ticket.arrival);
                break;
            case INQ:
                System.out.printf("INQ left %d departure %d arrival %d\n", leftTicket, departure, arrival);
                break;
            case SOLDOUT:
                System.out.printf("SOLDOUT\n");
                break;
            default:
                System.out.println("ERROR NODE\n");
                break;
        }
    }
        

}

public class LinearizationChecker {
    private static Vector<Node> nodes = new Vector<Node>();
    private static ArrayList<Node>[] graph;
    private static int idAllocator = 0;
    private static int visitCounter = 0;

    //we only check one route, because trains are independent
    private static int coachNum = 3;
    private static int seatNum = 5;
    private static int stationNum = 5;
    private static TreeSet<Ticket> soldTickets = new TreeSet<Ticket>();
    private static IntervalFreeSeat[][] intervalSeats;
    private static Seat[] routeSeats;

    //stack to record linearizable sequence
    private static boolean noPrint = false;
    private static Stack<Integer> path = new Stack<Integer>();

    public static void main(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--help")) {
                System.out.printf("--help: print help info\n");
                System.out.printf("--coach x: set coachnum to x, default is 3\n");
                System.out.printf("--seat x: set seatnum to x, default is 5\n");
                System.out.printf("--station x: set stationnum to x, default is 5\n");
                System.out.printf("--no-path-info: don't print a linearization path\n");
                return;
            }
            else if (args[i].equals("--coach")) {
                i++;
                coachNum = Integer.valueOf(args[i]);
            }
            else if (args[i].equals("--seat")) {
                i++;
                seatNum = Integer.valueOf(args[i]);
            }
            else if (args[i].equals("--station")) {
                i++;
                stationNum = Integer.valueOf(args[i]);
            }
            else if (args[i].equals("--no-path-info")) {
                noPrint = true;
            }
            else {
                System.out.printf("Unknown parameters! see --help\n");
                System.exit(1);
            }
        }

        intervalSeats  = new IntervalFreeSeat[stationNum][stationNum];       
        routeSeats =  new Seat[coachNum*seatNum];
        input();
        buildGraph();
        constructRoute();
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).inDegree != 0)
                continue;
            if (DFS(i)) {
                System.out.println(":) Linearizable!");
                if (!noPrint)
                    printPath();
                System.exit(0);
            }
        }
        System.out.println(":( Not linearizable!");
        System.exit(1);
    }

    public static boolean DFS(int i) {
        Node node = nodes.get(i);
        if (inEdgeChck(node) == false)
            return false;
        node.visited = true;
        visitCounter++;
        //node.print();//debug
        if (node.action == Action.INQ) {
            int realLeft = intervalSeats[node.departure-1][node.arrival-1].size();
            if (realLeft != node.leftTicket) {
                node.visited = false;
                visitCounter--;
                //System.out.println("try false node " + node.id);//debug
                return false;
            }
            for (Node outNode : graph[i]) {
                if (outNode.visited)
                    continue;
                if (DFS(outNode.id)) {
                    path.push(i);
                    return true;
                }
            }
            if (visitCounter == nodes.size()) {
                path.push(i);
                return true;
            }
            else {
                node.visited = false;
                visitCounter--;
                //System.out.println("try false node " + node.id);//debug
                return false;
            }
        }
        else if (node.action == Action.BUY) {
            int departure = node.ticket.departure;
            int arrival = node.ticket.arrival;
            int seatID = (node.ticket.coach - 1)*seatNum + node.ticket.seat - 1;
            if (intervalSeats[departure-1][arrival-1].size() == 0 ||
                !routeSeats[seatID].isFreeAt(departure, arrival)) {
                node.visited = false;
                visitCounter--;
                //System.out.println("try false node " + node.id);//debug
                return false;
            }
            
            //remove intersect intervals
            soldTickets.add(node.ticket);
            for (int x = 0; x < departure - 1; x++)
                for (int y = departure; y < stationNum; y++)
                    if (routeSeats[seatID].isFreeAt(x+1, y+1))
                        intervalSeats[x][y].remove(routeSeats[seatID]);
            for (int x = departure - 1; x < arrival - 1; x++)
                for (int y = x + 1; y < stationNum; y++)
                    if (routeSeats[seatID].isFreeAt(x+1, y+1))
                        intervalSeats[x][y].remove(routeSeats[seatID]);
            routeSeats[seatID].occupyAt(departure, arrival);

            for (Node outNode : graph[i]) {
                if (outNode.visited)
                    continue;
                if (DFS(outNode.id)) {
                    path.push(i);
                    return true;
                }
            }
            if (visitCounter == nodes.size()) {
                path.push(i);
                return true;
            }
            else {
                //restore removed intersect intervals
                soldTickets.remove(node.ticket);
                routeSeats[seatID].freeAt(departure, arrival);
                for (int x = 0; x < departure - 1; x++)
                    for (int y = departure; y < stationNum; y++)
                        if (routeSeats[seatID].isFreeAt(x+1, y+1))
                            intervalSeats[x][y].put(routeSeats[seatID]);
                for (int x = departure - 1; x < arrival - 1; x++)
                    for (int y = x + 1; y < stationNum; y++)
                        if (routeSeats[seatID].isFreeAt(x+1, y+1))
                            intervalSeats[x][y].put(routeSeats[seatID]);

                node.visited = false;
                visitCounter--;
                //System.out.println("try false node " + node.id);//debug
                return false;
            }
        }
        else if (node.action == Action.REF) {//refund
            int departure = node.ticket.departure;
            int arrival = node.ticket.arrival;
            int seatID = (node.ticket.coach - 1)*seatNum + node.ticket.seat - 1;
            if (!soldTickets.contains(node.ticket) ||
                !routeSeats[seatID].isOccupiedAt(departure, arrival)) {
                node.visited = false;
                visitCounter--;
                //System.out.println("try false node " + node.id);//debug
                return false;
            }

            //free all interval seats if neccessary
            soldTickets.remove(node.ticket);
            routeSeats[seatID].freeAt(departure, arrival);
            for (int x = 0; x < departure - 1; x++)
                for (int y = departure; y < stationNum; y++)
                    if (routeSeats[seatID].isFreeAt(x+1, y+1))
                        intervalSeats[x][y].put(routeSeats[seatID]);
            for (int x = departure - 1; x < arrival - 1; x++)
                for (int y = x + 1; y < stationNum; y++)
                    if (routeSeats[seatID].isFreeAt(x+1, y+1))
                        intervalSeats[x][y].put(routeSeats[seatID]);

            //traverse other adjacent node
            for (Node outNode : graph[i]) {
                if (outNode.visited)
                    continue;
                if (DFS(outNode.id)) {
                    path.push(i);
                    return true;
                }
            }
            if (visitCounter == nodes.size()) {
                path.push(i);
                return true;
            }
            else {
                //restore freed seats
                soldTickets.add(node.ticket);
                for (int x = 0; x < departure - 1; x++)
                    for (int y = departure; y < stationNum; y++)
                        if (routeSeats[seatID].isFreeAt(x+1, y+1))
                            intervalSeats[x][y].remove(routeSeats[seatID]);
                for (int x = departure - 1; x < arrival - 1; x++)
                    for (int y = x + 1; y < stationNum; y++)
                        if (routeSeats[seatID].isFreeAt(x+1, y+1))
                            intervalSeats[x][y].remove(routeSeats[seatID]);
                routeSeats[seatID].occupyAt(departure, arrival);
                
                node.visited = false;
                visitCounter--;
                //System.out.println("try false node " + node.id);//debug
                return false;
            }
        }
        else { //sold out
            if (intervalSeats[node.departure-1][node.arrival-1].size() != 0) {
                node.visited = false;
                visitCounter--;
                //System.out.println("try false node " + node.id);//debug
                return false;
            }

            for (Node outNode : graph[i]) {
                if (outNode.visited)
                    continue;
                if (DFS(outNode.id)) {
                    path.push(i);
                    return true;
                }
            }
            if (visitCounter == nodes.size()) {
                path.push(i);
                return true;
            }
            else {
                node.visited = false;
                visitCounter--;
                //System.out.println("try false node " + node.id);//debug
                return false;
            }
        }
    }

    public static void input() {
        Scanner sc = new Scanner(System.in);
        while (sc.hasNext()) {
            Node node = new Node();
            node.inEdges = new ArrayList<Integer>();
            node.id = idAllocator++;
            node.preTime = sc.nextLong();
            node.postTime = sc.nextLong();
            node.threadID = sc.nextInt();
            String s = sc.next();
            if (s.equals("TicketBought")) {
                node.action = Action.BUY;
                node.ticket = new Ticket();
                node.ticket.tid = sc.nextLong();
                node.ticket.passenger = sc.next();
                node.ticket.route = sc.nextInt();
                node.ticket.coach = sc.nextInt();
                node.ticket.departure = sc.nextInt();
                node.ticket.arrival = sc.nextInt();
                node.ticket.seat = sc.nextInt();
            }
            else if (s.equals("RemainTicket")) {
                node.action = Action.INQ;
                node.leftTicket = sc.nextInt();
                node.route = sc.nextInt();
                node.departure = sc.nextInt();
                node.arrival = sc.nextInt();
            }
            else if (s.equals("TicketRefund")) {
                node.action = Action.REF;
                node.ticket = new Ticket();
                node.ticket.tid = sc.nextLong();
                node.ticket.passenger = sc.next();
                node.ticket.route = sc.nextInt();
                node.ticket.coach = sc.nextInt();
                node.ticket.departure = sc.nextInt();
                node.ticket.arrival = sc.nextInt();
                node.ticket.seat = sc.nextInt();
            }
            else if (s.equals("TicketSoldOut")) {
                node.action = Action.SOLDOUT;
                node.route = sc.nextInt();
                node.departure = sc.nextInt();
                node.arrival = sc.nextInt();
            }
            else if (s.equals("ErrOfRefund")) {
                continue;
            }
            nodes.add(node);
        }
    }
    
    @SuppressWarnings("unchecked")
    private static void buildGraph() {
        graph = (ArrayList<Node>[])new ArrayList[nodes.size()];//new ArrayList<Node>[nodes.size()];
        for (int i = 0; i < nodes.size(); i++)
            graph[i] = new ArrayList<Node>();

        for (int i = 0; i < nodes.size(); i++)
            for (int j = i + 1; j < nodes.size(); j++) {
                if (nodes.get(i).postTime <= nodes.get(j).preTime) {
                    graph[i].add(graph[i].size(), nodes.get(j));
                    nodes.get(j).inDegree++;
                    nodes.get(j).inEdges.add(i);
                }
                else if (nodes.get(j).postTime <= nodes.get(i).preTime) {
                    graph[j].add(graph[j].size(), nodes.get(i));
                    nodes.get(i).inDegree++;
                    nodes.get(i).inEdges.add(j);
                }
                else {
                    graph[i].add(graph[i].size(), nodes.get(j));
                    graph[j].add(graph[j].size(), nodes.get(i));
                }
            }
    }

    private static void constructRoute() {
        //initialize test system
        for (int i = 0; i < coachNum*seatNum; i++)
            routeSeats[i] = new Seat(i, seatNum);

        for (int j = 0; j < stationNum; j++)
            for (int k = j + 1; k < stationNum; k++)
                intervalSeats[j][k] = new IntervalFreeSeat(routeSeats);
    }

    private static boolean inEdgeChck(Node v) {
        for (int u : v.inEdges)
            if (nodes.get(u).visited == false)
                return false;
        return true;
    }

    private static void printPath() {
        while (!path.isEmpty()) {
            int id = path.pop();
            Node node = nodes.get(id);
            Ticket tic = node.ticket;
            
            switch (nodes.get(id).action) {
                case BUY:
                    System.out.printf("[%8d,%8d]Line: %4d Buy ticketID %d coach %d seat %d departure %d arrial %d\n",
                                      node.preTime, node.postTime, id+1, tic.tid, tic.coach, tic.seat, tic.departure, tic.arrival);
                    break;
                case REF:
                    System.out.printf("[%8d,%8d]Line: %4d Ref ticketID %d coach %d seat %d departure %d arrial %d\n",
                                      node.preTime, node.postTime, id+1, tic.tid, tic.coach, tic.seat, tic.departure, tic.arrival);
                    break;
                case INQ:
                    System.out.printf("[%8d,%8d]Line: %4d Inq leftTick %d departure %d arrival %d\n", 
                                      node.preTime, node.postTime, id+1, node.leftTicket, node.departure, node.arrival);
                    break;
                case SOLDOUT:
                    System.out.printf("[%8d,%8d]Line: %4d SoldOut departure %d arrival %d\n",
                                      node.preTime, node.postTime, id+1, node.departure, node.arrival);
                    break;
                default:
                    System.out.printf("Checker system error!!!\n");
                    break;
            }
        }

    }

}

