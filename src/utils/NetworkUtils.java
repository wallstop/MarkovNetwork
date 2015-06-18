package utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.function.BooleanSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kitchen-sink for Network utility functions
 *
 * @author wallstop
 */
public class NetworkUtils
{
    private static final Logger LOG = LoggerFactory.getLogger(NetworkUtils.class);

    /* Default interval to check on blacking resources */
    private static final long WAIT_TIME_MILLIS = 100L;

    public static void awaitTrue(final BooleanSupplier condition)
    {
        try
        {
            while(!condition.getAsBoolean())
            {
                Thread.sleep(WAIT_TIME_MILLIS);
            }
        }
        catch(final Exception e)
        {
            LOG.error("Caught unexpected exception while awaiting a conditon", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Blocks until the provided buffer is ready
     *
     * @param reader
     *            BufferedReader to block on.
     * @throws IllegalArgumentException
     *             if the provided BufferedReader is null
     * @throws RuntimeException
     *             if Thread.sleep or BufferedReader.ready() throws
     */
    public static void awaitBuffer(final BufferedReader reader)
    {
        Validate.notNull(reader, "Cannot await a null reader");
        awaitTrue(() ->
        {
            try
            {
                return reader.ready();
            }
            catch(final IOException e)
            {
                throw new RuntimeException(e);
            }
        });
    }

}
