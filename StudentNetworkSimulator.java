import java.util.*;

import javax.security.auth.login.AccountLockedException;

import java.io.*;

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

    //Stats
    private int numberOfCorruptedPacket = 0;
    private int numberOfRetransmittedPacket = 0;
    private int numberOfAckedPacketSendByB = 0;
    private int numberOfPacketsTransmittedByA = 0;
    private int numberOfPacketsToLayer5ByB = 0;


    //Sender Side (A) Parameters
    private int seqNoSender = 0;
    private int ackNoSender = 0;
    private LinkedList<PacketElement> sendWindowPacketElementLinkedList = new LinkedList<>();
    private double waitTimeOutSender;

    private class PacketElement{
        public Packet packet;
        public boolean isSent;
        public boolean isAcked;

        public PacketElement(Packet packet){
            this.packet = packet;
            this.isAcked = false;
            this.isSent = false;
        }
    }

    //Reciever Side (B) Parameters
    private int ackNoReciever;
    private int seqNoReciever;
    private HashMap<Integer, String> recieverUnconsumedHashMap = new HashMap<>();
    private int currentSeqNoReadyToConsume;
    private int consumedNo;

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

    
    // This routine will be called whenever the upper layer at the sender [A]
    // has a message to send.  It is the job of your protocol to insure that
    // the data in such a message is delivered in-order, and correctly, to
    // the receiving upper layer.


    protected void aOutput(Message message)
    {
        pushToMessageBuffer(message);
        updateSendWindow();
        send();
    }

    private void pushToMessageBuffer(Message message){
        int seq = getCurrentSeqNoSender();
        int ack = getCurrentAckNoSender();
        int check = calculateCheckSum(message.getData(), seq, ack);
        Packet p = new Packet(seq, ack, check, message.getData());
        sendWindowPacketElementLinkedList.addLast(new PacketElement(p));
    }

    private void send(){
        int step = 0;
        while(step < WindowSize && step<sendWindowPacketElementLinkedList.size()){
            PacketElement currentPacketElement =sendWindowPacketElementLinkedList.get(step);
            if(currentPacketElement.isSent == false){
                toLayer3(A, currentPacketElement.packet);
                startTimer(A, waitTimeOutSender);
                currentPacketElement.isSent = true;
                numberOfPacketsTransmittedByA++;
            }
            step++;
        }
    }

    private void updateSendWindow(){
        while(!sendWindowPacketElementLinkedList.isEmpty() && sendWindowPacketElementLinkedList.getFirst().isAcked == true){
            ackNoSender = sendWindowPacketElementLinkedList.removeFirst().packet.getSeqnum();
            
        }
    }

    private int getCurrentSeqNoSender(){
        seqNoSender++;
        return seqNoSender % LimitSeqNo;
    }

    private int getCurrentAckNoSender(){
        return ackNoSender;
    }

    private int calculateCheckSum(String message, int seq, int ack){
        int checksum = 0;
        for(int i = 0; i < message.length(); i++){
            checksum += (int) message.charAt(i);
        }
        checksum+= seq;
        checksum+= ack;
        return checksum;
    }
    
    // This routine will be called whenever a packet sent from the B-side 
    // (i.e. as a result of a toLayer3() being done by a B-side procedure)
    // arrives at the A-side.  "packet" is the (possibly corrupted) packet
    // sent from the B-side.
    protected void aInput(Packet packet)
    {
        //check corruptions
        if(isCorrupted(packet) ){
            numberOfCorruptedPacket++;
            return;
        }
        int step = 0;
        while(step < WindowSize && step < sendWindowPacketElementLinkedList.size()){
            PacketElement p = sendWindowPacketElementLinkedList.get(step);
            if(p.packet.getSeqnum() == packet.getAcknum()){
                p.isAcked = true;
                break;
            }
            step++;
        }
        updateSendWindow();
        send();
    }
    
    // This routine will be called when A's timer expires (thus generating a 
    // timer interrupt). You'll probably want to use this routine to control 
    // the retransmission of packets. See startTimer() and stopTimer(), above,
    // for how the timer is started and stopped. 
    protected void aTimerInterrupt()
    {
        int step = 0;
        while (step < WindowSize && step<sendWindowPacketElementLinkedList.size()) {
            PacketElement currentPacket = sendWindowPacketElementLinkedList.get(step);
            if(currentPacket.isAcked == false && currentPacket.isSent == true){
                toLayer3(A, currentPacket.packet);
                startTimer(A, waitTimeOutSender);
                numberOfRetransmittedPacket++;
            }
            step++;
        }
    }
    
    // This routine will be called once, before any of your other A-side 
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity A).
    protected void aInit()
    {
        System.out.println("Init Sender A");
        seqNoSender = -1;
        ackNoSender = 0;
        waitTimeOutSender = 5 * RxmtInterval;

    }
    
    // This routine will be called whenever a packet sent from the B-side 
    // (i.e. as a result of a toLayer3() being done by an A-side procedure)
    // arrives at the B-side.  "packet" is the (possibly corrupted) packet
    // sent from the A-side.
    protected void bInput(Packet packet)
    {
        if(isCorrupted(packet) ){
            numberOfCorruptedPacket++;
            return;
        }
        int check = calculateCheckSum("ACK", 1, packet.getSeqnum());
        toLayer3(B, new Packet(1,packet.getSeqnum(),check,"ACK"));
        numberOfAckedPacketSendByB++;
        recieverUnconsumedHashMap.put(packet.getSeqnum(),packet.getPayload());

        tryToConsumeMessage();
        
    }

    private void tryToConsumeMessage(){
        currentSeqNoReadyToConsume = consumedNo % LimitSeqNo;
        System.out.println("currentSeqNoReadyToConsume " + currentSeqNoReadyToConsume);
        while(recieverUnconsumedHashMap.get(currentSeqNoReadyToConsume)!=null){
            toLayer5(recieverUnconsumedHashMap.get(currentSeqNoReadyToConsume));
            consumedNo++;
            numberOfPacketsToLayer5ByB++;
            recieverUnconsumedHashMap.remove(currentSeqNoReadyToConsume);
            currentSeqNoReadyToConsume = consumedNo % WindowSize;
        }
    }
    
    // This routine will be called once, before any of your other B-side 
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity B).
    protected void bInit()
    {
        System.out.println("bInit");

        seqNoReciever = 0;
        ackNoReciever = 0;
        consumedNo = 0;
        currentSeqNoReadyToConsume = 0;
    }

    // Use to print final statistics
    protected void Simulation_done()
    {
    	// TO PRINT THE STATISTICS, FILL IN THE DETAILS BY PUTTING VARIBALE NAMES. DO NOT CHANGE THE FORMAT OF PRINTED OUTPUT
    	System.out.println("\n\n===============STATISTICS=======================");
    	System.out.println("Number of original packets transmitted by A:" + numberOfPacketsTransmittedByA);
    	System.out.println("Number of retransmissions by A:" + numberOfRetransmittedPacket);
    	System.out.println("Number of data packets delivered to layer 5 at B:" + numberOfPacketsToLayer5ByB);
    	System.out.println("Number of ACK packets sent by B:" + numberOfAckedPacketSendByB);
    	System.out.println("Number of corrupted packets:" + numberOfCorruptedPacket);
    	System.out.println("Ratio of lost packets:" +  numberOfRetransmittedPacket / (numberOfAckedPacketSendByB+numberOfPacketsTransmittedByA));
    	System.out.println("Ratio of corrupted packets:" + numberOfCorruptedPacket / (numberOfPacketsTransmittedByA+numberOfAckedPacketSendByB));
    	System.out.println("Average RTT:" + "<YourVariableHere>");
    	System.out.println("Average communication time:" + "<YourVariableHere>");
    	System.out.println("==================================================");

    	// PRINT YOUR OWN STATISTIC HERE TO CHECK THE CORRECTNESS OF YOUR PROGRAM
    	System.out.println("\nEXTRA:");
    	// EXAMPLE GIVEN BELOW
    	//System.out.println("Example statistic you want to check e.g. number of ACK packets received by A :" + "<YourVariableHere>"); 
    }	

    private boolean isCorrupted (Packet packet) {
        return calculateCheckSum(packet.getPayload(), packet.getSeqnum(), packet.getAcknum()) != packet.getChecksum();
    }


}