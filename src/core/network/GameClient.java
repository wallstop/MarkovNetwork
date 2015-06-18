package core.network;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.NetworkUtils;
import utils.SerializationUtils;
import utils.Validate;
import core.Player;
import core.Policy;
import core.Rules;

public class GameClient<S, A, R extends Rules<S, A>> implements Runnable
{
    private static final Logger LOG = LoggerFactory.getLogger(GameClient.class);

    private final Policy<S, A> policy_;
    private final Socket server_;
    private final Class<S> stateClass_;
    private final R rules_;

    public GameClient(final R rules, final Policy<S, A> policy, final int port,
            final Class<S> stateClass)
    {
        Validate.notNull(rules, "Cannot create a GameClient from a null rule set");
        Validate.notNull(policy, "Cannot create a GameClient from a null policy");
        Validate.notNull(stateClass, "Cannot create a GameClient with a null State class");
        policy_ = policy;
        rules_ = rules;
        // TODO: Make ip address come into play
        try
        {
            server_ = new Socket("localhost", port);
        }
        catch(final IOException e)
        {
            LOG.error("Could not create a server socket on {}", port, e);
            throw new RuntimeException(e);
        }

        stateClass_ = stateClass;
        LOG.info("Started {} client with {} policy on port {}", GameClient.class.getSimpleName(),
                policy, port);
    }

    private S readStateFromServer() throws InterruptedException
    {
        try
        {
            final BufferedReader serverReader = new BufferedReader(new InputStreamReader(
                    server_.getInputStream()));
            NetworkUtils.awaitBuffer(serverReader);
            final String stateJson = serverReader.readLine();
            final S state = SerializationUtils.readValue(stateJson, stateClass_);
            return state;
        }
        catch(final IOException e)
        {
            LOG.error("Encountered unexpected exception while "
                    + "attempting to receive a state from the server", e);
            throw new RuntimeException(e);
        }
    }

    private void writeActionToServer(final A action)
    {
        final String stateAsJson = SerializationUtils.writeValue(action);
        try
        {
            final DataOutputStream outputStream = new DataOutputStream(server_.getOutputStream());
            outputStream.writeBytes(stateAsJson + System.lineSeparator());
        }
        catch(final IOException e)
        {
            LOG.error("Encountered unexpected exception while writing {} out to the server",
                    stateAsJson, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run()
    {
        try
        {
            /*
             * TODO: Figure out some kind of signal/event driven architecture so
             * we can shutdown our threads cleanly
             */
            while(true)
            {
                final S state = readStateFromServer();

                /*
                 * TODO: See if there's a better way of determining what player
                 * we are...
                 */
                final Player player = rules_.getCurrentPlayer(state);
                LOG.info("Current player: {}", player);
                final Collection<A> actions = rules_.getAvailableActions(player, state);
                LOG.info("Available actions: {}", actions);
                final A chosenAction = policy_.chooseAction(state, actions);
                LOG.info("Chose action: {}", chosenAction);
                writeActionToServer(chosenAction);
            }
        }
        catch(final Exception e)
        {
            LOG.error("Caught unexpected exception while running Client", e);
        }
    }
}
