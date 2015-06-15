package core.network;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.reflect.TypeToken;

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
            final Class<S> stateClass) throws UnknownHostException, IOException
    {
        Validate.notNull(rules, "Cannot create a GameClient from a null rule set");
        Validate.notNull(policy, "Cannot create a GameClient from a null policy");
        Validate.notNull(stateClass, "Cannot create a GameClient with a null State class");
        policy_ = policy;
        rules_ = rules;
        // TODO: Make ip address come into play
        server_ = new Socket("localhost", port);
        stateClass_ = stateClass;

        LOG.info("Started {} client with {} policy on port {}",
                new Object[] { GameClient.class.getSimpleName(), policy, port });
    }

    private S readStateFromServer()
    {
        try(final BufferedReader clientReader = new BufferedReader(new InputStreamReader(
                server_.getInputStream())))
        {
            final String actionResponse = clientReader.readLine();
            final S state = SerializationUtils.readValue(actionResponse, stateClass_);
            return state;
        }
        catch(IOException e)
        {
            LOG.error("Encountered unexpected exception while "
                    + "attempting to receive a state from the server", e);
            throw new RuntimeException(e);
        }
    }

    private void writeActionToServer(final A action)
    {
        final String stateAsJson = SerializationUtils.writeValue(action);
        try(final DataOutputStream outputStream = new DataOutputStream(server_.getOutputStream()))
        {
            outputStream.writeBytes(stateAsJson);
        }
        catch(IOException e)
        {
            LOG.error("Encountered unexpected exception while writing {} out to the server",
                    stateAsJson, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run()
    {

        while(true)
        {
            final S state = readStateFromServer();
            /*
             * TODO: See if there's a better way of determining what player we
             * are...
             */
            final Player player = rules_.getCurrentPlayer(state);
            final Collection<A> actions = rules_.getAvailableActions(player, state);
            final A chosenAction = policy_.chooseAction(state, actions);
            writeActionToServer(chosenAction);
        }
    }
}
