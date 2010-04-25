/*
 * ice4j, the OpenSource Java Solution for NAT and Firewall Traversal.
 * Maintained by the SIP Communicator community (http://sip-communicator.org).
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.ice4j;

import java.util.logging.*;

/**
 * The class contains a number of property names and their default values that
 * we use to configure the behavior of the ice4j stack.
 *
 * @author Emil Ivov
 */
public class StackProperties
{
    /**
     * Our class logger.
     */
    private static final Logger logger
        = Logger.getLogger(StackProperties.class.getName());

    /**
     * The name of the property containing the number of binds that we should
     * should execute in case a port is already bound to (each retry would be on
     * a new random port).
     */
    public static final String BIND_RETRIES = "org.ice4j.BIND_RETRIES";

    /**
     * The default number of binds that we would try
     * implementation should execute in case a port is already bound to (each
     * retry would be on a different port).
     */
    public static final int BIND_RETRIES_DEFAULT_VALUE = 50;

    /**
     * The number of milliseconds a client transaction should wait before
     * retransmitting, after it has sent a request for the first time.
     */
    public static final String FIRST_CTRAN_RETRANS_AFTER
                                        = "org.ice4j.FIRST_CTRAN_RETRANS_AFTER";

    /**
     * The maximum number of milliseconds that an exponential client
     * retransmission timer can reach.
     */
    public static final String MAX_CTRAN_RETRANS_TIMER
                                    = "org.ice4j.MAX_CTRAN_RETRANS_TIMER";

    /**
     * Indicates whether a client transaction should be kept after a response
     * is received rather than destroying it which is the default.
     */
    public static final String KEEP_CRANS_AFTER_A_RESPONSE
                                = "org.ice4j.KEEP_CRANS_AFTER_A_RESPONSE";

    /**
     * The maximum number of retransmissions a client transaction should send.
     */
    public static final String MAX_CTRAN_RETRANSMISSIONS
                                = "org.ice4j.MAX_RETRANSMISSIONS";

    /**
     * The name of the System property that allows us to set a custom maximum
     * for check list sizes.
     */
    public static final String MAX_CHECK_LIST_SIZE
                                        = "org.ice4j.MAX_CHECK_LIST_SIZE";

    /**
     * The value of the SOFTWARE attribute that ice4j should include in all
     * outgoing messages.
     */
    public static final String SOFTWARE = "org.ice4j.SOFTWARE";

    /**
     * The name of the property that tells the stack whether or not it should
     * let the application see retransmissions of incoming requests.
     */
    public static final String PROPAGATE_RECEIVED_RETRANSMISSIONS
                        = "org.ice4j.PROPAGATE_RECEIVED_RETRANSMISSIONS";

    /**
     * A property that allows us to specify whether we would expect link local
     * IPv6 addresses to be able to reach globally routable ones.
     */
    public static final String ALLOW_LINK_TO_GLOBAL_REACHABILITY
                                = "org.ice4j.ALLOW_LINK_TO_GLOBAL_REACHABILITY";

    /**
     * Returns the String value of the specified property (minus all
     * encompassing whitespaces)and null in case no property value was mapped
     * against the specified propertyName, or in case the returned property
     * string had zero length or contained whitespaces only.
     *
     * @param propertyName the name of the property that is being queried.
     *
     * @return the result of calling the property's toString method and null in
     * case there was no value mapped against the specified
     * <tt>propertyName</tt>, or the returned string had zero length or
     * contained whitespaces only.
     */
    public static String getString(String propertyName)
    {
        Object propValue = System.getProperty(propertyName);
        if (propValue == null)
            return null;

        String propStrValue = propValue.toString().trim();

        return (propStrValue.length() > 0)
                    ? propStrValue
                    : null;
    }

    /**
     * Returns the value of a specific property as a signed decimal integer. If
     * a property with the specified property name exists, its string
     * representation is parsed into a signed decimal integer according to the
     * rules of {@link Integer#parseInt(String)}. If parsing the value as a
     * signed decimal integer fails or there is no value associated with the
     * specified property name, <tt>defaultValue</tt> is returned.
     *
     * @param propertyName the name of the property to get the value of as a
     * signed decimal integer
     * @param defaultValue the value to be returned if parsing the value of the
     * specified property name as a signed decimal integer fails or there is no
     * value associated with the specified property name in the System
     * properties.
     * @return the value of the property with the specified name in the System
     * properties as a signed decimal integer;
     * <tt>defaultValue</tt> if parsing the value of the specified property name
     * fails or no value is associated among the System properties.
     */
    public static int getInt(String propertyName, int defaultValue)
    {
        String stringValue = getString(propertyName);
        int intValue = defaultValue;

        if ((stringValue != null) && (stringValue.length() > 0))
        {
            try
            {
                intValue = Integer.parseInt(stringValue);
            }
            catch (NumberFormatException ex)
            {
                logger.log(Level.FINE, propertyName
                    + " does not appear to be an integer. " + "Defaulting to "
                    + defaultValue + ".", ex);
            }
        }
        return intValue;
    }
}