package tictactoe;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import core.Player;
import core.Policy;
import core.network.GameClient;
import core.network.GameServer;
import core.policies.RandomPolicy;

public class TicTacToeServerTest
{

    public static void main(String args[]) throws InterruptedException, UnknownHostException,
            IOException
    {
        final Map<Player, Policy<TicTacToeState, TicTacToeAction>> policies = new HashMap<>();
        policies.put(new Player("Player 1"), new RandomPolicy<>());
        policies.put(new Player("Player 2"), new RandomPolicy<>());
        final TicTacToeState initialState = new TicTacToeState(policies.keySet());
        final TicTacToeRules rules = new TicTacToeRules();

        final GameServer<TicTacToeState, TicTacToeAction, TicTacToeRules> gameServer = new GameServer<>(
                rules, policies.keySet(), TicTacToeAction.class);

        final Map<Player, Integer> playersToPorts = ImmutableMap.<Player, Integer> builder()
                .putAll(gameServer.getPlayersToPorts()).build();
        final Map<Player, GameClient<TicTacToeState, TicTacToeAction, TicTacToeRules>> playersToClients = Maps
                .newHashMap();
        for(final Map.Entry<Player, Integer> playerToPort : playersToPorts.entrySet())
        {
            final Player player = playerToPort.getKey();
            final Integer port = playerToPort.getValue();
            final GameClient<TicTacToeState, TicTacToeAction, TicTacToeRules> client = new GameClient<>(
                    rules, policies.get(player), port, TicTacToeState.class);
            playersToClients.put(player, client);
            new Thread(client).start();
        }
        gameServer.awaitAllClientConnections();

        /*
         * TODO: Actually play game. Maybe this is the time to turn automator
         * into an interface, have game server implement it?
         */

    }
}
