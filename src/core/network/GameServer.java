package core.network;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.Range;
import utils.Validate;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import core.Automator;
import core.Player;
import core.Policy;
import core.Rules;

public class GameServer<S, A, R extends Rules<S, A>> implements Automator<S, A, R>
{
    private static final Logger LOG = LoggerFactory.getLogger(GameServer.class);

    private final Map<Player, GameListener<S, A>> playersToListeners_;

    private static final int MAX_PORTS = (1 << 16);

    private final ListeningExecutorService threadPool_ = MoreExecutors.listeningDecorator(Executors
            .newWorkStealingPool());

    private final AtomicInteger succesfulClientConnections_ = new AtomicInteger(0);
    private final AtomicInteger failedClientConnections_ = new AtomicInteger(0);
    final R rules_;

    /**
     * Creates a game server for the specified game
     *
     * @param game
     * @throws IOException
     */
    public GameServer(final R rules, final Collection<Player> players, final Class<A> actionClass)
    {
        Validate.notNull(rules, "Cannot create a GameServer with a null Ruleset");
        rules_ = rules;
        final Range<Integer> playerRange = rules.numberOfPlayers();
        Validate.notNull(playerRange, "Cannot create a GameServer for a null player range");
        Validate.notEmpty(players, "Cannot create a GameServer with a null Collection of Players");
        final int numPlayers = players.size();
        Validate.isTrue(playerRange.isValueWithin(numPlayers), String.format(
                "Cannot create a game for %d players; %d is not within %s", numPlayers, numPlayers,
                playerRange));

        Validate.isTrue(numPlayers <= MAX_PORTS,
                String.format("Cannot create a GameServer with more clients "
                        + "(%d) than ports available (%d)!", numPlayers, MAX_PORTS));
        Validate.notNull(actionClass, "Cannot create a GameServer with a null actionClass");

        playersToListeners_ = Maps.newHashMapWithExpectedSize(numPlayers);

        initializeListenersFromPlayers(players, actionClass);

        /*
         * Not sure of the best way to properly set up clients & severs
         * currently. In my mind, this would be done by some kind of startup
         * script, probably not pure java code. DUNNO LOL For now we'll assume
         * that the caller will be doing something like: GameServer myGameServer
         * = new GameServer(TicTacToe); Map<Player, Int> playersToPorts =
         * myGameServer.getPlayersToPorts(); ... // run off and create and
         * initialize clients ... // figure out some way for GameServer to block
         * until all clients are connected ... // Start game ??? Profit
         */
    }

    /*
     * This should only ever be called from Constructor. This is a bundled init
     * method; it constructs all GameListeners, maps Players to those listeners,
     * and also spawns threads to await on socket connections for those
     * listeners
     */
    private void initializeListenersFromPlayers(final Collection<Player> players,
            final Class<A> actionClass)
    {
        final Set<Integer> usedPorts = Sets.newHashSetWithExpectedSize(players.size());
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
                // Offset the ports so we don't overlap with common ones
                final int portOffset = 100;
                do
                {
                    port = ThreadLocalRandom.current().nextInt(MAX_PORTS - portOffset) + portOffset
                            + 1;
                }
                while(usedPorts.add(port));

                LOG.info("Mapping Player {} to port {}", player, port);
                final GameListener<S, A> listener = new GameListener<S, A>(port, actionClass);
                playersToListeners_.put(player, listener);
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
    }

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
    public boolean awaitAllClientConnections() throws InterruptedException
    {
        final long pollTimeMilliseconds = 100;
        while(true)
        {
            if(areAllClientsConnected() || hasAClientConnectionFailed())
            {
                break;
            }
            Thread.sleep(pollTimeMilliseconds);
        }

        LOG.info(hasAClientConnectionFailed() ? "A client had a problem connecting"
                : "All clients connected successfully");

        return !(hasAClientConnectionFailed());
    }

    public boolean hasAClientConnectionFailed()
    {
        return failedClientConnections_.get() != 0;
    }

    public boolean areAllClientsConnected()
    {
        return succesfulClientConnections_.get() == numPlayers();
    }

    public int numPlayers()
    {
        return playersToListeners_.size();
    }

    public Map<Player, Integer> getPlayersToPorts()
    {
        return playersToListeners_
                .entrySet()
                .stream()
                .collect(
                        Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue()
                                .getPort()));
    }

    public void playUntilCompletion() throws InterruptedException, ExecutionException
    {
        Validate.isTrue(areAllClientsConnected(),
                "Cannot play a game to completion that doesn't have all clients connected!");
        final S initialState = rules_.generateInitialState(playersToListeners_.keySet());
        Validate.notNull(initialState, "Cannot play a game with a null initial state");
        S currentState = initialState;
        Player currentPlayer;
        while(!rules_.isTerminal(currentState))
        {
            currentPlayer = rules_.getCurrentPlayer(currentState);
            final GameListener<S, A> listenerForPlayer = playersToListeners_.get(currentPlayer);
            Validate.notNull(listenerForPlayer, String.format(
                    "Rules %s reported player %s, but we have no knowledge of it (%s)", rules_,
                    currentPlayer, playersToListeners_));
            final Collection<A> availableActions = rules_.getAvailableActions(currentPlayer,
                    currentState);
            final S filteredState = rules_.filterState(currentState, currentPlayer);
            final A chosenAction = listenerForPlayer.requestChooseAction(filteredState);
            Validate.isTrue(availableActions.contains(chosenAction), String.format(
                    "Cannot take Action %s, it is not valid. Valid actions: %s", chosenAction,
                    availableActions));
            currentState = rules_.transition(currentState, chosenAction);
        }

        LOG.info("Ending state for game between {}:{}{}",
                new Object[] { playersToListeners_.keySet(), System.lineSeparator(), currentState });
    }

    @Override
    public S playGameToCompletion(final R rules, final S initialState,
            final Map<Player, Policy<S, A>> policies)
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public S advanceUntilPlayerTurn(final R rules, final S initialState, final Player player,
            final Map<Player, Policy<S, A>> policies)
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public S advanceSingleAction(final R rules, final S initialState,
            final Map<Player, Policy<S, A>> policies)
    {
        // TODO Auto-generated method stub
        return null;
    }
}
