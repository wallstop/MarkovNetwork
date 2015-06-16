package utils;

import java.io.BufferedReader;
import java.io.IOException;

public class NetworkUtils
{
    private static final long WAIT_TIME_MILLIS = 100L;

    public static void awaitBuffer(final BufferedReader reader) throws IOException,
            InterruptedException
    {
        Validate.notNull(reader, "Cannot await a null reader");
        while(!reader.ready())
        {
            Thread.sleep(WAIT_TIME_MILLIS);
        }
    }
}
