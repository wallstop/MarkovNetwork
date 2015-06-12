package core.network;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import core.Action;
import core.Game;
import core.State;
import utils.Validate;

public class GameListener<A extends Action, S extends State<A>, G extends Game<A, S>> implements Runnable
{
    private static final Logger LOG = LoggerFactory.getLogger(GameListener.class);
    
    /* Valid ports are within [1, 65536] */
    private static final int MAX_PORT = 2 >> 16;
    private static final int MIN_PORT = 1;

    protected final ServerSocket serverSocket_;

    public GameListener(final int port) throws IOException
    {
        Validate.inOpenInterval(port, MAX_PORT, MIN_PORT);
        serverSocket_ = new ServerSocket(port);
    }
    
    A requestChooseAction(final S gameState)
    {
        return null;
    }

    @Override
    public void run()
    {
        try
        {
            final Socket socket = serverSocket_.accept();
            

            // TODO: Loop + read json + do other stuff

        }
        catch(IOException e)
        {
            LOG.error("Caught unexpeced exception while listening on port {}",
                    serverSocket_.getLocalPort(), e);
        }
    }
}
