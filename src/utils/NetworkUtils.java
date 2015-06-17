package utils;

import java.io.BufferedReader;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetworkUtils
{
    private static final Logger LOG = LoggerFactory.getLogger(NetworkUtils.class);

    private static final long WAIT_TIME_MILLIS = 100L;

    public static void awaitBuffer(final BufferedReader reader)
    {
        Validate.notNull(reader, "Cannot await a null reader");
        try
        {
            while(!reader.ready())
            {
                Thread.sleep(WAIT_TIME_MILLIS);
            }
        }
        catch(InterruptedException | IOException e)
        {
            LOG.error("Caught unexpected exception while awaiting a buffered reader", e);
            throw new RuntimeException(e);
        }
    }
}
