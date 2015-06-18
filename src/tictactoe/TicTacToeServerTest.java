package tictactoe;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import core.Player;
import core.Policy;
import core.network.GameClient;
import core.network.NetworkAutomator;
import core.policies.RandomPolicy;

public class TicTacToeServerTest
{
    public static void main(final String args[])
    {
        final Map<Player, Policy<TicTacToeState, TicTacToeAction>> playersToPolicies = new HashMap<>();
        playersToPolicies.put(new Player("Player 1"), new RandomPolicy<>());
        playersToPolicies.put(new Player("Player 2"), new RandomPolicy<>());
        final TicTacToeRules rules = new TicTacToeRules();
        final NetworkAutomator<TicTacToeState, TicTacToeAction, TicTacToeRules> gameServer = new NetworkAutomator<>(
                rules, playersToPolicies.keySet(), TicTacToeAction.class);

        final Map<Player, Integer> playersToPorts = ImmutableMap.<Player, Integer> builder()
                .putAll(gameServer.getPlayersToPorts()).build();
        final Map<Player, GameClient<TicTacToeState, TicTacToeAction, TicTacToeRules>> playersToClients = Maps
                .newHashMap();
        final Collection<Thread> threads = Lists.newArrayList();
        for(final Map.Entry<Player, Integer> playerToPort : playersToPorts.entrySet())
        {
            final Player player = playerToPort.getKey();
            final Integer port = playerToPort.getValue();
            final GameClient<TicTacToeState, TicTacToeAction, TicTacToeRules> client = new GameClient<>(
                    rules, playersToPolicies.get(player), port, TicTacToeState.class);
            playersToClients.put(player, client);
            final Thread clientRunner = new Thread(client);
            threads.add(clientRunner);
            clientRunner.start();
        }

        gameServer.playGameToCompletion();
        gameServer.shutdown();

        threads.forEach(thread -> thread.interrupt());
    }
}
