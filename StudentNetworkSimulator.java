import java.util.*;

public class StudentNetworkSimulator extends NetworkSimulator
{
    /*
     * Predefined Constants (static member variables):
     *
     *   int MAXDATASIZE : the maximum size of the Message data and
     *                     Packet payload
     *
     *   int A           : a predefined integer that represents entity A
     *   int B           : a predefined integer that represents entity B 
     *
     * Predefined Member Methods:
     *
     *  void stopTimer(int entity): 
     *       Stops the timer running at "entity" [A or B]
     *  void startTimer(int entity, double increment): 
     *       Starts a timer running at "entity" [A or B], which will expire in
     *       "increment" time units, causing the interrupt handler to be
     *       called.  You should only call this with A.
     *  void toLayer3(int callingEntity, Packet p)
     *       Puts the packet "p" into the network from "callingEntity" [A or B]
     *  void toLayer5(String dataSent)
     *       Passes "dataSent" up to layer 5
     *  double getTime()
     *       Returns the current time in the simulator.  Might be useful for
     *       debugging.
     *  int getTraceLevel()
     *       Returns TraceLevel
     *  void printEventList()
     *       Prints the current event list to stdout.  Might be useful for
     *       debugging, but probably not.
     *
     *
     *  Predefined Classes:
     *
     *  Message: Used to encapsulate a message coming from layer 5
     *    Constructor:
     *      Message(String inputData): 
     *          creates a new Message containing "inputData"
     *    Methods:
     *      boolean setData(String inputData):
     *          sets an existing Message's data to "inputData"
     *          returns true on success, false otherwise
     *      String getData():
     *          returns the data contained in the message
     *  Packet: Used to encapsulate a packet
     *    Constructors:
     *      Packet (Packet p):
     *          creates a new Packet that is a copy of "p"
     *      Packet (int seq, int ack, int check, String newPayload)
     *          creates a new Packet with a sequence field of "seq", an
     *          ack field of "ack", a checksum field of "check", and a
     *          payload of "newPayload"
     *      Packet (int seq, int ack, int check)
     *          chreate a new Packet with a sequence field of "seq", an
     *          ack field of "ack", a checksum field of "check", and
     *          an empty payload
     *    Methods:
     *      boolean setSeqnum(int n)
     *          sets the Packet's sequence field to "n"
     *          returns true on success, false otherwise
     *      boolean setAcknum(int n)
     *          sets the Packet's ack field to "n"
     *          returns true on success, false otherwise
     *      boolean setChecksum(int n)
     *          sets the Packet's checksum to "n"
     *          returns true on success, false otherwise
     *      boolean setPayload(String newPayload)
     *          sets the Packet's payload to "newPayload"
     *          returns true on success, false otherwise
     *      int getSeqnum()
     *          returns the contents of the Packet's sequence field
     *      int getAcknum()
     *          returns the contents of the Packet's ack field
     *      int getChecksum()
     *          returns the checksum of the Packet
     *      int getPayload()
     *          returns the Packet's payload
     *
     */

    /*   Please use the following variables in your routines.
     *   int WindowSize  : the window size
     *   double RxmtInterval   : the retransmission timeout
     *   int LimitSeqNo  : when sequence number reaches this value, it wraps around
     */

    public static final int FirstSeqNo = 0;
    private int WindowSize;
    private double RxmtInterval;
    private int LimitSeqNo;
    
    // Add any necessary class variables here.  Remember, you cannot use
    // these variables to send messages error free!  They can only hold
    // state information for A or B.
    // Also add any necessary methods (e.g. checksum of a String)
    int nextSeqNo = 0;
    int lastSeqNo = 0;
    int sendBase = 0;
    int recievedBase = 0;

    double timerA = 0.0;
    double timerB = 0.0;

    Queue<Packet> unAckedBuffer = new LinkedList<Packet>();
    Queue<Packet> unSentBuffer = new LinkedList<Packet>();
    LinkedList<Packet> recievedBuffer = new LinkedList<Packet>();

    // Parameters for simulation stats
    int pktSent = 0;
    int pktReTransA = 0;
    int pktReceived = 0;
    int ackSent = 0;
    int corrupted = 0;
    private double rtt = 0.0;
    private double commuTime = 0.0;

    // This is the constructor.  Don't touch!
    public StudentNetworkSimulator(int numMessages,
                                   double loss,
                                   double corrupt,
                                   double avgDelay,
                                   int trace,
                                   int seed,
                                   int winsize,
                                   double delay)
    {
        super(numMessages, loss, corrupt, avgDelay, trace, seed);
	WindowSize = winsize;
	LimitSeqNo = winsize*2; // set appropriately; assumes SR here!
	RxmtInterval = delay;
    }

    private int calculateCheckSum(String message, int seq, int ack){
        int checksum = 0;
        for(int i = 0; i < message.length(); i++){
            checksum += (int) message.charAt(i);
        }
        checksum += seq;
        checksum += ack;
        return checksum;
    }

    private boolean inReceivedWindow(int seqNum){
        boolean inReceived = false;
        for(int i=0; i<WindowSize; i++){
            if((recievedBase + i) % (LimitSeqNo) == seqNum){
                inReceived = true;
            }
        }
        return inReceived;
    }

    private void increaseSeqNum(){
        if (nextSeqNo + 1 == LimitSeqNo) {
            nextSeqNo = 0;
        }
        else {
            nextSeqNo++;
        }
    }

    private boolean removeFromSentBuffer(int seqNum){
        boolean pktFound = false;
        for(Packet i : unAckedBuffer){
            if(i.getSeqnum() == seqNum){
                pktFound=true;
                break;
            }
        }
        if(pktFound){
            while(true){
                if(unAckedBuffer.remove().getSeqnum() == seqNum){
                    break;
                }
            }
        }
        return pktFound;
    }

    // This routine will be called whenever the upper layer at the sender [A]
    // has a message to send.  It is the job of your protocol to insure that
    // the data in such a message is delivered in-order, and correctly, to
    // the receiving upper layer.

    protected void aOutput(Message message)
    {
        pktSent++;
        Packet pktA = new Packet(nextSeqNo,0,0,message.getData());
        pktA.setChecksum(calculateCheckSum(pktA.getPayload(), pktA.getSeqnum(), pktA.getAcknum()));

        if (unAckedBuffer.size() < WindowSize) {
            System.out.println("\n-----------A OUTPUT----------");
            System.out.println("Starting the timer.\n");
            timerA = getTime();
            timerB = getTime();
            toLayer3(A, pktA);
            unAckedBuffer.add(pktA);
            startTimer(A, RxmtInterval);

            System.out.printf("\nPAYLOAD: %s \nLength: %d\n", new String(pktA.getPayload()).trim(), new String(pktA.getPayload()).trim().length());
            System.out.printf("\n************************************\n");
            System.out.printf("Sending packet \nSeq no: %d\nChecksum: %d\nGenerated Checksum: %d\n", pktA.getSeqnum(), pktA.getChecksum(), calculateCheckSum(message.getData(), pktA.getSeqnum(), pktA.getAcknum()));
            System.out.printf("\n************************************\n");
        }
        else {
            unSentBuffer.add(pktA);
        }
        increaseSeqNum();
        return;
    }
    
    // This routine will be called whenever a packet sent from the B-side 
    // (i.e. as a result of a toLayer3() being done by a B-side procedure)
    // arrives at the A-side.  "packet" is the (possibly corrupted) packet
    // sent from the B-side.
    protected void aInput(Packet packet)
    {
        int checksum = calculateCheckSum(packet.getPayload(), packet.getSeqnum(), packet.getAcknum());

        if(unAckedBuffer.size() == 0){
            return;
        }

        System.out.println("\n-------------- A INPUT --------------");

        // Print details of the received packet
        System.out.printf("Received packet details - Seqno: %d, Ackno: %d, Checksum: %d\n", packet.getSeqnum(), packet.getAcknum(), packet.getChecksum());

        if (checksum != packet.getChecksum()) { // Check for corrupt packet
            System.out.println("\nPacket is corrupted or not in order.\n");
            corrupted++;
        }
        else {
            System.out.println("\nPacket is correctly received.\n");
            stopTimer(A);
            rtt += (getTime() - timerA);
            removeFromSentBuffer(packet.getSeqnum());
            if(unAckedBuffer.size() < WindowSize && unSentBuffer.size() > 0){
                System.out.println("Sending the next packet in the window.");
                timerA = getTime();
                timerB = getTime();
                Packet nextPkt = unSentBuffer.poll();
                toLayer3(A, nextPkt);
                unSentBuffer.remove();
                unAckedBuffer.add(nextPkt);
                startTimer(A, RxmtInterval);
            }
        }
    }
    
    // This routine will be called when A's timer expires (thus generating a 
    // timer interrupt). You'll probably want to use this routine to control 
    // the retransmission of packets. See startTimer() and stopTimer(), above,
    // for how the timer is started and stopped. 
    protected void aTimerInterrupt()
    {
        System.out.println("\n-------------- A TIMER INTERRUPT --------------");
        toLayer3(A, unAckedBuffer.peek());
        System.out.println("Retransmitting packet:" + unAckedBuffer.peek().getPayload());
        startTimer(A, RxmtInterval);

        pktReTransA++;
        commuTime += (getTime() - timerB);
    }
    
    // This routine will be called once, before any of your other A-side 
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity A).
    protected void aInit()
    {
        System.out.println("\nINITIALIZING THE PACKET\n");
        sendBase = 0;
    }
    
    // This routine will be called whenever a packet sent from the A-side 
    // (i.e. as a result of a toLayer3() being done by an A-side procedure)
    // arrives at the B-side.  "packet" is the (possibly corrupted) packet
    // sent from the A-side.

    protected void bInput(Packet packet)
    {
        System.out.println("\n--------------- B INPUT --------------");
        int checksum = calculateCheckSum(packet.getPayload(), packet.getSeqnum(), packet.getAcknum());
        System.out.printf("\n************************************\n");
        System.out.printf("Payload: %s\nSeqno: %d\nPktchecksum: %d \nGenerated checksum: %d \nLength: %d", new String(packet.getPayload()).trim(), packet.getSeqnum(), packet.getChecksum(), checksum, new String(packet.getPayload()).trim().length());
        System.out.printf("\n************************************\n");

        if(checksum == packet.getChecksum()){
            if(inReceivedWindow(packet.getSeqnum())){
                recievedBuffer.add(packet);
                if (packet.getSeqnum() == recievedBase) {
                    boolean pktFound = true;
                    while(pktFound){
                        pktFound = false;

                        ArrayList<Packet> receivedBuffer = new ArrayList<Packet>();

                        for(Packet k : recievedBuffer){
                            receivedBuffer.add(k);
                        }

                        for(Packet pck : receivedBuffer) {
                            if(pck.getSeqnum() == recievedBase) {
                                lastSeqNo = recievedBase;
                                pktFound = true;
                                pktReceived++;
                                toLayer5(pck.getPayload());
                                recievedBuffer.remove(pck);
                                recievedBase = (recievedBase + 1) % LimitSeqNo;
                                break;
                            }
                        }
                    }
                    Packet ackedPkt = new Packet(lastSeqNo, 1, 0);
                    ackedPkt.setChecksum(calculateCheckSum(ackedPkt.getPayload(), ackedPkt.getSeqnum(), ackedPkt.getAcknum()));
                    toLayer3(B, ackedPkt);
                    ackSent++;
                    System.out.printf("\nSent data to layer 5 with packet: %d and %d", packet.getSeqnum(), pktReceived);
                }
            }
            else{
                Packet pktB = new Packet(packet.getSeqnum(),1,0);
                pktB.setChecksum(calculateCheckSum(pktB.getPayload(), pktB.getSeqnum(), pktB.getAcknum()));
                ackSent++;
                toLayer3(B, pktB);
                System.out.println("\nPacket corrupted or not in correct order\n\n");
                System.out.println("\nResending previous packet");
                System.out.printf("\nSeqno: %d \nAckno: %d \nChecksum: %d\n", pktB.getSeqnum(), pktB.getAcknum(), pktB.getChecksum());
            }
        }
        else{
            corrupted++;
        }
    }
    
    // This routine will be called once, before any of your other B-side 
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity B).
    protected void bInit()
    {
        recievedBase = 0;
    }

    // Use to print final statistics
    protected void Simulation_done()
    {
    	// TO PRINT THE STATISTICS, FILL IN THE DETAILS BY PUTTING VARIBALE NAMES. DO NOT CHANGE THE FORMAT OF PRINTED OUTPUT
    	System.out.println("\n\n===============STATISTICS=======================");
    	System.out.println("Number of original packets transmitted by A: " + pktSent);
    	System.out.println("Number of retransmissions by A: " + pktReTransA);
    	System.out.println("Number of data packets delivered to layer 5 at B: " + pktReceived);
    	System.out.println("Number of ACK packets sent by B: " + ackSent);
    	System.out.println("Number of corrupted packets: " + corrupted);
    	System.out.println("Ratio of lost packets: " + ((pktReTransA-corrupted)/(pktSent+pktReTransA+ackSent)));
    	System.out.println("Ratio of corrupted packets: " + ((corrupted)/((pktSent+pktReTransA)+ackSent-(pktReTransA-corrupted))));
    	System.out.println("Average RTT: " + (rtt/pktSent));
    	System.out.println("Average communication time: " + (commuTime/pktSent));
    	System.out.println("==================================================");

    	// PRINT YOUR OWN STATISTIC HERE TO CHECK THE CORRECTNESS OF YOUR PROGRAM
    	System.out.println("\nEXTRA:");
    	// EXAMPLE GIVEN BELOW
    	//System.out.println("Example statistic you want to check e.g. number of ACK packets received by A :" + "<YourVariableHere>"); 
    }	

}
