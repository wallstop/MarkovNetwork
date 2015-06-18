package core.network;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.NetworkUtils;
import utils.Validate;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import core.Automator;
import core.Player;
import core.Rules;

/**
 * NetworkAutomators provide a way of running a simulation via a ruleset without
 * the "policies" being implemented in Java. With this kind of class, we divorce
 * the requirement that we feed an Automator a mapping of Players to Policies,
 * turning that into a mapping of players to connections, or players to ports.
 *
 * Unfortunately, the NetworkAutomator has no way of knowing how to actually
 * start the relevant clients, as they may be in any language, on any machine.
 * In theory, they will be on the same machine, or similar machines. However,
 * there's no reason to restrict this.
 *
 * NetworkAutomator provides some useful utility blocking functions. On
 * creation, it will automatically decide what ports to use for this game
 * session, one per player. The creator of the NetworkAutomator is then burdened
 * with creating the necessary clients to connect to those ports for those
 * players.
 *
 * @author wallstop
 *
 * @param <S>
 * @param <A>
 * @param <R>
 */
public class NetworkAutomator<S, A, R extends Rules<S, A>> extends Automator<S, A, R>
{
    private static final Logger LOG = LoggerFactory.getLogger(NetworkAutomator.class);

    private final Map<Player, GameListener<S, A>> playersToListeners_;

    private static final int MAX_PORTS = (1 << 16);

    private final ListeningExecutorService threadPool_ = MoreExecutors.listeningDecorator(Executors
            .newWorkStealingPool());

    private final AtomicInteger succesfulClientConnections_ = new AtomicInteger(0);
    private final AtomicInteger failedClientConnections_ = new AtomicInteger(0);

    /**
     * Creates a game server for the specified game
     *
     * @param game
     * @throws IOException
     */
    public NetworkAutomator(final R rules, final Collection<Player> players,
            final Class<A> actionClass)
    {
        super(rules, players);
        final int numPlayers = players.size();
        Validate.isTrue(numPlayers <= MAX_PORTS,
                String.format("Cannot create a GameServer with more clients "
                        + "(%d) than ports available (%d)!", numPlayers, MAX_PORTS));
        Validate.notNull(actionClass, "Cannot create a GameServer with a null actionClass");

        final Map<Player, GameListener<S, A>> playersToGameListeners = initializeListenersFromPlayers(
                players, actionClass);
        playersToListeners_ = ImmutableMap.copyOf(playersToGameListeners);
        currentState_ = rules_.generateInitialState(players);
    }

    /*
     * This should only ever be called from Constructor. This is a bundled init
     * method; it constructs all GameListeners, maps Players to those listeners,
     * and also spawns threads to await on socket connections for those
     * listeners
     */
    private Map<Player, GameListener<S, A>> initializeListenersFromPlayers(
            final Collection<Player> players, final Class<A> actionClass)
            {
        final Set<Integer> usedPorts = Sets.newHashSetWithExpectedSize(players.size());
        final Map<Player, GameListener<S, A>> playersToGameListeners = Maps
                .newHashMapWithExpectedSize(players.size());
        try
        {
            for(final Player player : players)
            {
                Validate.notNull(player,
                        "Cannot create a GameServer for a game that has a null player");
                /*
                 * Doesn't really matter what port the're on, so just pick one
                 * randomly (but make sure we haven't picked it before)
                 */
                int port;
                /*
                 * Offset the ports by some amount in an attempt to avoid
                 * overlap with existing / currently used / common ports
                 * (best-effort, if this fails, come up with a better
                 * methodology)
                 */
                final int portOffset = 10000;
                do
                {
                    port = ThreadLocalRandom.current().nextInt(MAX_PORTS - portOffset) + portOffset
                            + 1;
                }
                while(usedPorts.add(port));

                LOG.info("Mapping Player {} to port {}", player, port);
                final GameListener<S, A> listener = new GameListener<S, A>(port, actionClass);
                playersToGameListeners.put(player, listener);
                final ListenableFuture<Void> waitingConnection = threadPool_.submit(() ->
                {
                    listener.connect();
                    return null;
                });
                attachClientConnectionCallback(waitingConnection);
            }
        }
        catch(final IOException e)
        {
            LOG.error("Encountered unexpected exception while initializing listeners for {}",
                    players, e);
            throw new RuntimeException(e);
        }
        return playersToGameListeners;
            }

    /**
     * We need a way to update our counts of currently connected players as well
     * as to identify if any players have failed to connect. This is done by
     * attaching callbacks on the client-connection-futures. When a connection
     * either fails or succeeds, this attached callback will atomically update
     * the correct player connection value.
     *
     * @param connection
     *            The connection to attach a callback to.
     */
    private void attachClientConnectionCallback(final ListenableFuture<Void> connection)
    {
        Futures.addCallback(connection, new FutureCallback<Void>()
                {
            @Override
            public void onFailure(final Throwable exception)
            {
                final int totalFailures = failedClientConnections_.incrementAndGet();
                LOG.error("A client failed to connect, {} total failures", totalFailures, exception);
            }

            @Override
            public void onSuccess(final Void success)
            {
                final int totalConnections = succesfulClientConnections_.incrementAndGet();
                LOG.info("A client connected, {} total connections", totalConnections);
            }
                });
    }

    /**
     * Blocks until all clients have successfully connected or throw errors
     *
     * @throws InterruptedException
     * @return True if all clients have connected successfully, false otherwise
     */
    public boolean awaitAllClientConnections()
    {
        NetworkUtils.awaitTrue(() -> areAllClientsConnected() || hasAClientConnectionFailed());
        LOG.info(hasAClientConnectionFailed() ? "A client had a problem connecting"
                : "All clients connected successfully");

        return !(hasAClientConnectionFailed());
    }

    private boolean hasAClientConnectionFailed()
    {
        return failedClientConnections_.get() != 0;
    }

    private boolean areAllClientsConnected()
    {
        return succesfulClientConnections_.get() == numPlayers();
    }

    public int numPlayers()
    {
        return playersToListeners_.size();
    }

    /**
     * Since G TODO FINISH THIS COMMENT
     *
     * @return
     */
    public Map<Player, Integer> getPlayersToPorts()
    {
        return playersToListeners_
                .entrySet()
                .stream()
                .collect(
                        Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue()
                                .getPort()));
    }

    public void shutdown()
    {
        playersToListeners_.values().forEach(listener -> listener.disconnect());
    }

    @Override
    public S advanceUntilPlayerTurn(final Player player)
    {
        Validate.isTrue(playersToListeners_.containsKey(player), String.format(
                "Cannot advance the game to a "
                        + "Player (%s) who is not part of the game. Valid players: %s", player,
                        playersToListeners_.keySet()));
        if(!areAllClientsConnected())
        {
            final boolean allClientsConnected = awaitAllClientConnections();
            Validate.isTrue(allClientsConnected, "Cannot play a game to completion when"
                    + " clients have failed to connect");
        }
        for(int turns = 0; (!(Objects.equals(player, rules_.getCurrentPlayer(currentState_))) && !(rules_
                .isTerminal(currentState_))); ++turns)
        {
            advanceSingleAction();
            LOG.info("Advanced {} turns waiting Player {}. Current Player: {}", turns, player,
                    rules_.getCurrentPlayer(currentState_));
        }

        return currentState();
    }

    @Override
    public S advanceSingleAction()
    {
        if(!areAllClientsConnected())
        {
            final boolean allClientsConnected = awaitAllClientConnections();
            Validate.isTrue(allClientsConnected, "Cannot play a game to completion when"
                    + " clients have failed to connect");
        }

        final Player currentPlayer = rules_.getCurrentPlayer(currentState_);
        final GameListener<S, A> listenerForPlayer = playersToListeners_.get(currentPlayer);
        Validate.notNull(listenerForPlayer, String.format(
                "Rules %s reported player %s, but we have no knowledge of it (%s)", rules_,
                currentPlayer, playersToListeners_));
        final Collection<A> availableActions = rules_.getAvailableActions(currentPlayer,
                currentState_);
        final S filteredState = rules_.filterState(currentState_, currentPlayer);
        final A chosenAction = listenerForPlayer.requestChooseAction(filteredState);
        Validate.isTrue(availableActions.contains(chosenAction), String.format(
                "Cannot take Action %s, it is not valid. Valid actions: %s", chosenAction,
                availableActions));
        currentState_ = rules_.transition(currentState_, chosenAction);
        return currentState();
    }

    @Override
    public S playGameToCompletion()
    {
        if(!areAllClientsConnected())
        {
            final boolean allClientsConnected = awaitAllClientConnections();
            Validate.isTrue(allClientsConnected, "Cannot play a game to completion when"
                    + " clients have failed to connect");
        }

        while(!rules_.isTerminal(currentState_))
        {
            advanceSingleAction();
        }

        LOG.info("Ending state for game between {}:{}{}", playersToListeners_.keySet(),
                System.lineSeparator(), currentState_);

        return currentState();
    }

    @Override
    public S currentState()
    {
        return rules_.copyState(currentState_);
    }

    @Override
    public S currentStateFilteredForPlayer(final Player player)
    {
        return rules_.filterState(currentState_, player);
    }
}
