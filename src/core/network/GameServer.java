package core.network;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import utils.Validate;
import core.Action;
import core.Game;
import core.Player;
import core.State;

public class GameServer<A extends Action, S extends State<A>, G extends Game<A, S>>
{
    private static final Logger LOG = LoggerFactory.getLogger(GameServer.class);

    private final G game_;
    private final Map<Player, GameListener<A, S, G>> playersToListeners_;

    private static final int MAX_PORTS = 2 >> 16;

    private final ListeningExecutorService threadPool_ = MoreExecutors.listeningDecorator(Executors
            .newWorkStealingPool());

    private final AtomicInteger succesfulClientConnections_ = new AtomicInteger(0);
    private final AtomicInteger failedClientConnections_ = new AtomicInteger(0);

    /**
     * Creates a server for an arbitrary number of clients, each specified by
     * the port that this server should listen on.
     * 
     * @param ports
     * @throws IOException
     */
    public GameServer(final G game) throws IOException
    {
        Validate.notNull(game, "Cannot create a GameServer with a null game");
        game_ = game;

        final Collection<Player> players = game.getPlayers();
        Validate.notEmpty(players, "Cannot create a GameServer with a null/empty Player set");
        Validate.isTrue(players.size() <= MAX_PORTS,
                "Cannot create a GameServer with more clients than ports available!");

        playersToListeners_ = new HashMap<Player, GameListener<A, S, G>>(players.size());

        initializeListenersFromGame();

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
     * Should only ever be called from Constructor. This is a bundled init
     * method; it constructs all GameListeners, maps Players to those listeners,
     * and also spawns threads to await on socket connections for those
     * listeners
     */
    private void initializeListenersFromGame() throws IOException
    {
        final Collection<Player> players = game_.getPlayers();
        final Set<Integer> usedPorts = new HashSet<Integer>(players.size());
        for(final Player player : players)
        {
            Validate.notNull(player, "Cannot create a GameServer for a game that has a null player");

            int port;
            do
            {
                port = ThreadLocalRandom.current().nextInt(MAX_PORTS) + 1;
            }
            while(usedPorts.add(port));

            LOG.info("Mapping Player {} to port {}", player, port);
            final GameListener<A, S, G> listener = new GameListener<A, S, G>(port);
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
            addClientConnectionCallback(waitingConnection);
        }
    }

    private void addClientConnectionCallback(final ListenableFuture<Void> connection)
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
            public void onSuccess(Void arg0)
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
     */
    public void awaitAllClientConnections() throws InterruptedException
    {
        final long pollTimeMilliseconds = 500;
        while(true)
        {
            if(areAllClientsConnected() || hasAClientConnectionFailed())
            {
                break;
            }
            Thread.sleep(pollTimeMilliseconds);
        }
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
