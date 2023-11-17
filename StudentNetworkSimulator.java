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
    int currentSeqNo = 0;
    int expectedACK = 0;
    int highestAckedSeqNum = -1;
    int sendBase = 0;
    int receivedBase = 0;

    double timerA = 0.0;
    double timerB = 0.0;

    Queue<Packet> senderWindow = new LinkedList<Packet>();
    Queue<Packet> unAckedBuffer = new LinkedList<Packet>();
    LinkedList<Packet> receivedBuffer = new LinkedList<Packet>();

    // Parameters for simulation stats
    int pktSent = 0;
    int pktReTrans = 0;
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

    private void increaseSeqNum(){
        if (currentSeqNo + 1 == LimitSeqNo) {
            currentSeqNo = 0;
        }
        else {
            currentSeqNo++;
        }
    }

    private void increaseReceivedBase(){
        if (receivedBase + 1 == LimitSeqNo) {
            receivedBase = 0;
        }
        else {
            receivedBase++;
        }
    }

    private void removeFromSentBuffer(int seqNum){
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
    }

    private void processInOrderPacket(Packet packet) {
        System.out.println("Transmitting Packet to Layer 5, Payload: " + packet.getPayload());
        toLayer5(packet.getPayload());
        pktReceived++;

        highestAckedSeqNum = packet.getSeqnum();

        // Process any buffered packets that are now in order
        while (!receivedBuffer.isEmpty() && receivedBuffer.peek().getSeqnum() == receivedBase + 1) {
            increaseReceivedBase(); // Increase before processing the next in-order packet
            Packet nextInOrder = receivedBuffer.poll();
            System.out.println("Transmitting Packet to Layer 5, Payload: " + nextInOrder.getPayload());
            toLayer5(nextInOrder.getPayload());
            pktReceived++;

            highestAckedSeqNum = nextInOrder.getSeqnum();
        }
        sendACKWithSACK(receivedBase);
        increaseReceivedBase();
    }

    private void processOutOfOrderPacket(Packet packet) {
        for (Packet pkt : receivedBuffer) {
            if (packet.getSeqnum() == pkt.getSeqnum()) {
                return;
            }
        }
        System.out.println("Packet not in order, buffered");
        receivedBuffer.add(packet);
        System.out.println("Receiver Buffer: " + receivedBuffer.toString());

        sendACKWithSACK(packet.getSeqnum());
    }

    private boolean isDuplicate(int seqNum) {
        // Calculate the range of acceptable sequence numbers
        int upperBound = (receivedBase + WindowSize - 1) % LimitSeqNo;

        if (receivedBase <= upperBound) {
            // No wrapping in the window
            return !(seqNum >= receivedBase && seqNum <= upperBound);
        } else {
            // Wrapping occurs in the window
            return !(seqNum >= receivedBase || seqNum <= upperBound);
        }
    }

    private void sendACKWithSACK(int ackNum) {
        Packet ackPkt = new Packet(ackNum, ackNum, 0);

        LinkedList<Packet> sackList = receivedBuffer;
        Collections.sort(sackList, Comparator.comparingInt(Packet::getSeqnum));

        ackPkt.setSack(sackList);
        System.out.println("ACK PACKET TEST: " + ackPkt.toString());
        ackPkt.setChecksum(calculateCheckSum(ackPkt.getPayload(), ackPkt.getSeqnum(), ackPkt.getAcknum()));

        toLayer3(B, ackPkt);
        ackSent++;
    }

    private void resendACK(int seqNum) {
        Packet ackPkt = new Packet(seqNum, seqNum, 0);
        ackPkt.setChecksum(calculateCheckSum(ackPkt.getPayload(), ackPkt.getSeqnum(), ackPkt.getAcknum()));
        toLayer3(B, ackPkt);
    }

    // This routine will be called whenever the upper layer at the sender [A]
    // has a message to send.  It is the job of your protocol to insure that
    // the data in such a message is delivered in-order, and correctly, to
    // the receiving upper layer.

    protected void aOutput(Message message)
    {
        pktSent++;
        Packet sendPkt = new Packet(currentSeqNo,0,0,message.getData());
        sendPkt.setChecksum(calculateCheckSum(sendPkt.getPayload(), sendPkt.getSeqnum(), sendPkt.getAcknum()));

        if (unAckedBuffer.size() < WindowSize) {
            System.out.println("\n-----------A OUTPUT----------");

            System.out.println("Starting the timer.\n");
            timerA = getTime();
            timerB = getTime();
            toLayer3(A, sendPkt);
            unAckedBuffer.add(sendPkt);
            startTimer(A, RxmtInterval);

            expectedACK = currentSeqNo;
            increaseSeqNum();

            System.out.printf("\n************************************\n");
            System.out.printf("Sending packet \nSeqNo: %d\nChecksum: %d\nGenerated Checksum: %d\n", sendPkt.getSeqnum(), sendPkt.getChecksum(), calculateCheckSum(message.getData(), sendPkt.getSeqnum(), sendPkt.getAcknum()));
            System.out.println("UnAcked Buffer: " + unAckedBuffer.toString());
            System.out.println("************************************");
        }
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
        System.out.println(("Current SeqNo: " + currentSeqNo));
        System.out.println("Received packet details: " + packet.toString());

        // Check for corrupt packet
        if (checksum != packet.getChecksum()) {
            System.out.println("\nPacket is corrupted.");
            corrupted++;
            return;
        }
        else {
            int[] sack = packet.getSack();
            for (int seq : sack) {
                if (seq != -1) {  // Assuming -1 indicates an empty slot in SACK
                    removeFromSentBuffer(seq);
                }
            }

            System.out.println("\nPacket is correctly received. Expected ACK:" + expectedACK + "\n");

            if (expectedACK == packet.getAcknum()) {
                removeFromSentBuffer(packet.getSeqnum());
                stopTimer(A);
                rtt += (getTime() - timerA);
            }

            while (unAckedBuffer.size() < WindowSize && senderWindow.size() > 0) {
                System.out.println("Sending the next packet in the window.");
                timerA = getTime();
                timerB = getTime();

                Packet nextPkt = senderWindow.poll();
                toLayer3(A, nextPkt);
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

        // Retransmit all packets in the unacknowledged buffer
        for (Packet pkt : unAckedBuffer) {
            System.out.println("Retransmitting packet: " + pkt.getPayload());
            toLayer3(A, pkt);
            pktReTrans++;
        }

        // Restart the timer
        startTimer(A, RxmtInterval);
        timerA = getTime();
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
        int checksum = calculateCheckSum(packet.getPayload(), packet.getSeqnum(), packet.getAcknum());

        System.out.println("\n--------------- B INPUT --------------");
        System.out.printf("Payload: %s\nSeqno: %d\nPktchecksum: %d \nGenerated checksum: %d \nLength: %d", new String(packet.getPayload()).trim(), packet.getSeqnum(), packet.getChecksum(), checksum, new String(packet.getPayload()).trim().length());
        System.out.println("\nReceived Base: " + receivedBase);
        System.out.printf("************************************\n");

        // Checksum valid, packet received
        if(checksum == packet.getChecksum()){
            if ((isDuplicate(packet.getSeqnum()))) {
                // Find a duplicate packet, resend ACK
                System.out.println("Duplicate packet received.");
                resendACK(packet.getSeqnum());
            }
            else {
                if (packet.getSeqnum() == receivedBase) {
                    processInOrderPacket(packet);
                } else {
                    processOutOfOrderPacket(packet);
                }
            }
        }
        else {
            System.out.println("Packet is corrupted\n");
            corrupted++;
        }
    }
    
    // This routine will be called once, before any of your other B-side 
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity B).
    protected void bInit()
    {
        receivedBase = 0;
        highestAckedSeqNum = -1;
    }

    // Use to print final statistics
    protected void Simulation_done()
    {
    	// TO PRINT THE STATISTICS, FILL IN THE DETAILS BY PUTTING VARIBALE NAMES. DO NOT CHANGE THE FORMAT OF PRINTED OUTPUT
    	System.out.println("\n\n===============STATISTICS=======================");
    	System.out.println("Number of original packets transmitted by A: " + pktSent);
    	System.out.println("Number of retransmissions by A: " + pktReTrans);
    	System.out.println("Number of data packets delivered to layer 5 at B: " + pktReceived);
    	System.out.println("Number of ACK packets sent by B: " + ackSent);
    	System.out.println("Number of corrupted packets: " + corrupted);
    	System.out.println("Ratio of lost packets: " + (((double)(pktReTrans -corrupted))/((double)(pktSent+ pktReTrans +ackSent))));
    	System.out.println("Ratio of corrupted packets: " + ((double)(corrupted)/((double)(pktSent+ pktReTrans)+ackSent-(pktReTrans -corrupted))));
    	System.out.println("Average RTT: " + (rtt/pktSent/2));
    	System.out.println("Average communication time: " + (commuTime/pktSent));
    	System.out.println("==================================================");

    	// PRINT YOUR OWN STATISTIC HERE TO CHECK THE CORRECTNESS OF YOUR PROGRAM
    	System.out.println("\nEXTRA:");
    	// EXAMPLE GIVEN BELOW
    	//System.out.println("Example statistic you want to check e.g. number of ACK packets received by A :" + "<YourVariableHere>"); 
    }	

}
