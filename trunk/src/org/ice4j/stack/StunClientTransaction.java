/*
 * Stun4j, the OpenSource Java Solution for NAT and Firewall Traversal.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.ice4j.stack;

import java.util.logging.*;
import java.io.*;

import org.ice4j.*;
import org.ice4j.message.*;

/**
 * The ClientTransaction class retransmits (what a surprise) requests as
 * specified by rfc 3489.
 *
 * Once formulated and sent, the client sends the Binding Request.  Reliability
 * is accomplished through request retransmissions.  The ClientTransaction
 * retransmits the request starting with an interval of 100ms, doubling
 * every retransmit until the interval reaches 1.6s.  Retransmissions
 * continue with intervals of 1.6s until a response is received, or a
 * total of 9 requests have been sent. If no response is received by 1.6
 * seconds after the last request has been sent, the client SHOULD
 * consider the transaction to have failed. In other words, requests
 * would be sent at times 0ms, 100ms, 300ms, 700ms, 1500ms, 3100ms,
 * 4700ms, 6300ms, and 7900ms. At 9500ms, the client considers the
 * transaction to have failed if no response has been received.
 *
 *
 * <p>Organisation: <p> Louis Pasteur University, Strasbourg, France</p>
 * <p>Network Research Team (http://www-r2.u-strasbg.fr)</p></p>
 * @author Emil Ivov.
 * @author Pascal Mogeri (contributed configuration of client transactions).
 * @version 0.1
 */

class StunClientTransaction
    implements Runnable
{
    private static final Logger logger =
        Logger.getLogger(StunClientTransaction.class.getName());

    /**
     * The number of times to retransmit a request if no explicit value has been
     * specified by org.ice4j.MAX_RETRANSMISSIONS.
     */
    public static final int DEFAULT_MAX_RETRANSMISSIONS = 6;

    /**
     * Maximum number of retransmissions. Once this number is reached and if no
     * response is received after MAX_WAIT_INTERVAL miliseconds the request is
     * considered unanswered.
     */
    public int maxRetransmissions = DEFAULT_MAX_RETRANSMISSIONS;

    /**
     * The number of miliseconds a client should wait before retransmitting,
     * after it has sent a request for the first time.
     */
    public static final int DEFAULT_ORIGINAL_WAIT_INTERVAL = 100;

    /**
     * The number of miliseconds to wait before the first retansmission of the
     * request.
     */
    public int originalWaitInterval = DEFAULT_ORIGINAL_WAIT_INTERVAL;

    /**
     * The maximum number of miliseconds a client should wait between
     * consecutive retransmissionse, after it has sent a request for the first
     * time.
     */
    public static final int DEFAULT_MAX_WAIT_INTERVAL = 1600;

    /**
     * The maximum wait interval. Once this interval is reached we should stop
     * doubling its value.
     */
    public int maxWaitInterval = DEFAULT_MAX_WAIT_INTERVAL;

    /**
     * Indicates how many times we have retransmitted so fat.
     */
    private int retransmissionCounter = 0;

    /**
     * How much did we wait after our last retransmission.
     */
    private int nextWaitInterval       = originalWaitInterval;

    /**
     * The StunProvider that created us.
     */
    private StunProvider      providerCallback  = null;

    /**
     * The request that we are retransmitting.
     */
    private Request           request           = null;

    /**
     * The destination of the request.
     */
    private TransportAddress           requestDestination= null;


    /**
     * The id of the transaction.
     */
    private TransactionID    transactionID      = null;

    /**
     * The NetAccessPoint through which the original request was sent an where
     * we are supposed to be retransmitting.
     */
    private NetAccessPointDescriptor apDescriptor = null;

    /**
     * The instance to notify when a response has been received in the current
     * transaction or when it has timed out.
     */
    private ResponseCollector 	     responseCollector = null;

    /**
     * Specifies whether the transaction is active or not.
     */
    private boolean cancelled = false;

    /**
     * The date (in millis) when the next retransmission should follow.
     */
    private long nextRetransmissionDate = -1;

    /**
     * The thread that this transaction runs in.
     */
    private Thread runningThread = null;

    /**
     * Creates a client transaction
     * @param providerCallback the provider that created us.
     * @param request the request that we are living for.
     * @param requestDestination the destination of the request.
     * @param apDescriptor the access point through which we are supposed to
     * @param responseCollector the instance that should receive this request's
     * response.
     * retransmit.
     */
    public StunClientTransaction(StunProvider            providerCallback,
                                Request                  request,
                                TransportAddress              requestDestination,
                                NetAccessPointDescriptor apDescriptor,
                                ResponseCollector        responseCollector)
    {
        this.providerCallback  = providerCallback;
        this.request           = request;
        this.apDescriptor      = apDescriptor;
        this.responseCollector = responseCollector;
        this.requestDestination = requestDestination;

        initTransactionConfiguration();

        this.transactionID = TransactionID.createTransactionID();
        try
        {
            request.setTransactionID(transactionID.getTransactionID());
        }
        catch (StunException ex)
        {
            //Shouldn't happen so lets just through a runtime exception in
            //case anything is real messed up
            throw new IllegalArgumentException("The TransactionID class "
                                      +"generated an invalid transaction ID");
        }

        runningThread = new Thread(this);
    }

    /**
     * Implements the retransmissions algorithm. Retransmits the request
     * starting with an interval of 100ms, doubling every retransmit until the
     * interval reaches 1.6s.  Retransmissions continue with intervals of 1.6s
     * until a response is received, or a total of 7 requests have been sent.
     * If no response is received by 1.6 seconds after the last request has been
     * sent, we consider the transaction to have failed.
     */
    public void run()
    {
        runningThread.setName("CliTran");
        nextWaitInterval = originalWaitInterval;

        for (retransmissionCounter = 0;
             retransmissionCounter < maxRetransmissions;
             retransmissionCounter ++)
        {
            waitFor(nextWaitInterval);
            //did someone tell us to get lost?

            if(cancelled)
                return;

            if(nextWaitInterval < maxWaitInterval)
                nextWaitInterval *= 2;

            try
            {
                sendRequest0();
            }
            catch (Exception ex)
            {
                //I wonder whether we should notify anyone that a retransmission
                //has failed
                logger.log(Level.WARNING,
                           "A client tran retransmission failed", ex);
            };
        }

        //before stating that a transaction has timeout-ed we should first wait
        //for a reception of the response
        if(nextWaitInterval < maxWaitInterval)
                nextWaitInterval *= 2;

        waitFor(nextWaitInterval);

        responseCollector.processTimeout();
        providerCallback.removeClientTransaction(this);

    }

    /**
     * Sends the request and schedules the first retransmission for after
     * ORIGINAL_WAIT_INTERVAL and thus starts the retransmission algorithm.
     *
     * @throws IOException  if an error occurs while sending message bytes
     * through the network socket.
     * @throws IllegalArgumentException if the apDescriptor references an
     * access point that had not been installed,
     * @throws StunException if message encoding fails,
     *
     */
    void sendRequest()
        throws StunException, IllegalArgumentException, IOException
    {
        sendRequest0();

        runningThread.start();
    }

    /**
     * Simply calls the sendMessage method of the accessmanager.
     *
     * @throws IOException  if an error occurs while sending message bytes
     * through the network socket.
     * @throws IllegalArgumentException if the apDescriptor references an
     * access point that had not been installed,
     * @throws StunException if message encoding fails,
     */
    private void sendRequest0()
        throws StunException, IllegalArgumentException, IOException
    {
        if(cancelled)
        {
            logger.finer("Trying to resend a cancelled transaction.");
            return;
        }
        providerCallback.getNetAccessManager().sendMessage(
            this.request,
            apDescriptor,
            requestDestination);
    }

    /**
     * Returns the request that was the reason for creating this transaction.
     * @return the request that was the reason for creating this transaction.
     */
    Request getRequest()
    {
        return this.request;
    }

    /**
     * Waits until next retransmission is due or until the transaction is
     * cancelled (whichever comes first).
     *
     * @param millis the number of milliseconds to wait for.
     */
    synchronized void waitFor(long millis)
    {
        try
        {
            wait(millis);
        }
        catch (InterruptedException ex)
        {
            logger.log(Level.FINE, "Interrupted", ex);
        }
    }


    /**
     * Cancels the transaction. Once this method is called the transaction is
     * considered terminated and will stop retransmissions.
     */
    synchronized void cancel()
    {
        this.cancelled = true;
        notifyAll();
    }

    /**
     * Dispatches the response then cancels itself and notifies the StunProvider
     * for its termination.
     * @param evt the event that contains the newly received message
     */
    void handleResponse(StunMessageEvent evt)
    {
        String keepTran = System.getProperty(
            "org.ice4j.KEEP_CLIENT_TRANS_AFTER_A_RESPONSE");

        if( keepTran == null || !keepTran.trim().equalsIgnoreCase("true"))
            this.cancel();

        this.responseCollector.processResponse(evt);
    }

    /**
     * Returns the ID of the current transaction.
     *
     * @return the ID of the transaction.
     */
    TransactionID getTransactionID()
    {
        return this.transactionID;
    }

    /**
     * Init transaction duration/retransmission parameters.
     * (Mostly, contributed by Pascal Maugeri).
     */
    private void initTransactionConfiguration()
    {
        //Max Retransmissions
        String maxRetransmissionsStr =
            System.getProperty("org.ice4j.MAX_RETRANSMISSIONS");

        if(maxRetransmissionsStr != null
           && maxRetransmissionsStr.trim().length() > 0){
            try
            {
                maxRetransmissions = Integer.parseInt(maxRetransmissionsStr);
            }
            catch (NumberFormatException e)
            {
                logger.log(Level.FINE,
                           "Failed to parse MAX_RETRANSMISSIONS",
                           e);
                maxRetransmissions = DEFAULT_MAX_RETRANSMISSIONS;
            }
        }

        //Original Wait Interval
        String originalWaitIntervalStr =
            System.getProperty("org.ice4j.ORIGINAL_WAIT_INTERVAL");

        if(originalWaitIntervalStr != null
           && originalWaitIntervalStr.trim().length() > 0){
            try
            {
                originalWaitInterval
                    = Integer.parseInt(originalWaitIntervalStr);
            }
            catch (NumberFormatException e)
            {
                logger.log(Level.FINE,
                           "Failed to parse ORIGINAL_WAIT_INTERVAL",
                           e);
                originalWaitInterval = DEFAULT_ORIGINAL_WAIT_INTERVAL;
            }
        }

        //Max Wait Interval
        String maxWaitIntervalStr =
            System.getProperty("org.ice4j.MAX_WAIT_INTERVAL");

        if(maxWaitIntervalStr != null
           && maxWaitIntervalStr.trim().length() > 0){
            try
            {
                maxWaitInterval = Integer.parseInt(maxWaitIntervalStr);
            }
            catch (NumberFormatException e)
            {
                logger.log(Level.FINE, "Failed to parse MAX_WAIT_INTERVAL", e);
                maxWaitInterval = DEFAULT_MAX_WAIT_INTERVAL;
            }
        }
    }
}