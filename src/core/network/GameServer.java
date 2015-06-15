package core.network;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import utils.Range;
import utils.Validate;
import core.Player;
import core.Rules;

public class GameServer<S, A, R extends Rules<S, A>>
{
    private static final Logger LOG = LoggerFactory.getLogger(GameServer.class);

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
    public GameServer(final R rules, final Collection<Player> players, final Class<A> actionClass)
    {
        Validate.notNull(rules, "Cannot create a GameServer with a null Ruleset");
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
         * script, probably not pure java code. DUNNO LOL
         * 
         * For now we'll assume that the caller will be doing something like:
         * GameServer myGameServer = new GameServer(TicTacToe); Map<Player, Int>
         * playersToPorts = myGameServer.getPlayersToPorts(); ... // run off and
         * create and initialize clients ... // figure out some way for
         * GameServer to block until all clients are connected ... // Start game
         * ??? Profit
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
                do
                {
                    port = ThreadLocalRandom.current().nextInt(MAX_PORTS) + 1;
                }
                while(usedPorts.add(port));

                LOG.info("Mapping Player {} to port {}", player, port);
                final GameListener<S, A> listener = new GameListener<S, A>(port, actionClass);
                playersToListeners_.put(player, listener);
                final ListenableFuture<Void> waitingConnection = threadPool_
                        .submit(new Callable<Void>()
                        {
                            // Is there a better way to do this? wtf Void
                            @Override
                            public Void call() throws Exception
                            {
                                listener.connect();
                                return null;
                            }
                        });
                attachClientConnectionCallback(waitingConnection);
            }
        }
        catch(IOException e)
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
            public void onFailure(Throwable exception)
            {
                final int totalFailures = failedClientConnections_.incrementAndGet();
                LOG.error("A client failed to connect, {} total failures", totalFailures, exception);
            }

            @Override
            public void onSuccess(Void success)
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
     * 
     * @return True if all clients have connected succesfully, false otherwise
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
        
        LOG.info(hasAClientConnectionFailed() ? "A client had a problem connecting" : "All clients connected successfully");

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

}
