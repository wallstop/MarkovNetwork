package core.network;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.SerializationUtils;
import utils.Validate;

public class GameListener<S, A>
{
    private static final Logger LOG = LoggerFactory.getLogger(GameListener.class);

    /* We define valid ports to be within (1, 65536) */
    private static final int MAX_PORT = (1 << 16);
    private static final int MIN_PORT = 1;

    protected final ServerSocket serverSocket_;
    protected final Class<A> actionType_;;

    protected Socket clientConnection_;

    public GameListener(final int port, final Class<A> actionClass) throws IOException
    {
        Validate.inOpenInterval(port, MIN_PORT, MAX_PORT);
        Validate.notNull(actionClass, "Cannot create a GameListener for a null Action class");
        serverSocket_ = new ServerSocket(port);
        actionType_ = actionClass;
    }

    public A requestChooseAction(final S gameState)
    {
        Validate.notNull(clientConnection_,
                "Cannot make transactions with a null client connection");
        writeStateToClient(gameState);
        final A action = readResponseFromClient();
        return action;
    }

    private void writeStateToClient(final S gameState)
    {
        final String stateAsJson = SerializationUtils.writeValue(gameState);
        try(final DataOutputStream outputStream = new DataOutputStream(
                clientConnection_.getOutputStream()))
        {
            outputStream.writeBytes(stateAsJson);
        }
        catch(IOException e)
        {
            LOG.error("Encountered unexpected exception while writing {} out to the client",
                    stateAsJson, e);
            throw new RuntimeException(e);
        }
    }

    private A readResponseFromClient()
    {
        try(final BufferedReader clientReader = new BufferedReader(new InputStreamReader(
                clientConnection_.getInputStream())))
        {
            final String actionResponse = clientReader.readLine();
            final A action = SerializationUtils.readValue(actionResponse, actionType_);
            return action;
        }
        catch(IOException e)
        {
            LOG.error("Encountered unexpected exception while "
                    + "attempting to receive an action response from client", e);
            throw new RuntimeException(e);
        }
    }

    public void connect() throws IOException
    {
        try
        {
            final Socket socket = serverSocket_.accept();
            if(socket == null)
            {
                LOG.error("Attempted to establish a connection on port "
                        + "{}, but accept() returned null", serverSocket_.getLocalPort());
                throw new RuntimeException("Could not establish client connection");
            }
            clientConnection_ = socket;
        }
        catch(IOException e)
        {
            LOG.error("Caught unexpeced exception while listening on port {}",
                    serverSocket_.getLocalPort(), e);
            throw e;
        }
    }

    public int getPort()
    {
        return serverSocket_.getLocalPort();
    }
}
