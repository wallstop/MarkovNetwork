package core.network;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.Validate;
import core.Action;
import core.Game;
import core.Player;
import core.Policy;
import core.State;

public class GameServer<A extends Action, S extends State<A>, G extends Game<A, S>>
{
    private static final Logger LOG = LoggerFactory.getLogger(GameServer.class);

    private final G game_;
    private final Map<Player, GameListener<A, S, G>> playersToListeners_;

    private static final int MAX_PORTS = 2 >> 16;

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

            final GameListener<A, S, G> listener = new GameListener<A, S, G>(port);
            playersToListeners_.put(player, listener);
        }

        /*
         * Not sure of the best way to properly set up clients & severs
         * currently. In my mind, this would be done by some kind of startup
         * script, probably not pure java code. DUNNO LOL
         * 
         * For now we'll assume that the caller will be doing something like:
         * GameServer myGameServer = new GameServer(TicTacToe);
         * Map<Player, Int> playersToPorts = myGameServer.getPlayersToPorts();
         * ... // run off and create and initialize clients
         * ... // figure out some way for GameServer to block until all clients are connected
         * ... // Start game
         * ???
         * Profit
         */
    }

}
