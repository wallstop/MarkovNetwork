package tictactoe.model;

import java.util.List;
import java.util.stream.Collectors;

import tictactoe.TicTacToeAction;
import tictactoe.TicTacToeBoard;
import tictactoe.TicTacToeState;
import utils.Validate;

import com.fasterxml.jackson.annotation.JsonProperty;

public class StreamableState
{
    @JsonProperty("board")
    private StreamableBoard board_;

    @JsonProperty("isTerminal")
    private boolean terminal_;

    @JsonProperty("actions")
    private List<StreamableAction> actions_;

    StreamableState(final TicTacToeState state)
    {
        board_ = new StreamableBoard(state.getBoard());
        terminal_ = state.isTerminal();
        actions_ = state.getActions().stream()
                .map(action -> StreamableAction.fromTicTacToeAction(action))
                .collect(Collectors.toList());
    }

    public TicTacToeBoard getBoard()
    {
        return StreamableBoard.toTicTacToeBoard(board_);
    }

    public boolean isTerminal()
    {
        return terminal_;
    }

    public List<TicTacToeAction> getActions()
    {
        return actions_.stream().map(action -> StreamableAction.toTicTacToeAction(action))
                .collect(Collectors.toList());
    }

    public StreamableState fromTicTacToeState(final TicTacToeState state)
    {
        Validate.notNull(state, "Cannot create a StreamableState from a null TicTacToeState!");
        return new StreamableState(state);
    }

    public TicTacToeState fromStreamableState(final StreamableState state)
    {
        Validate.notNull(state, "Cannot create a TicTacToeState from a null StreamableState!");
        return new TicTacToeState(state.getBoard(), state.isTerminal(), state.getActions());
    }

}
