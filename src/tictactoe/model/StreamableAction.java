package tictactoe.model;

import tictactoe.TicTacToeAction;
import tictactoe.TicTacToeMark;
import utils.StreamableVector2;
import utils.Validate;
import utils.Vector2;

import com.fasterxml.jackson.annotation.JsonProperty;

public class StreamableAction
{
    @JsonProperty("Position")
    private StreamableVector2 position_;

    @JsonProperty("Mark")
    private TicTacToeMark mark_;

    StreamableAction(final TicTacToeAction action)
    {
        final StreamableVector2 position = StreamableVector2.fromVector2(action.getPosition());
        position_ = position;
        mark_ = action.getMark();
    }

    public Vector2 getPosition()
    {
        return StreamableVector2.toVector2(position_);
    }

    public TicTacToeMark getMark()
    {
        return mark_;
    }

    public static TicTacToeAction toTicTacToeAction(final StreamableAction action)
    {
        Validate.notNull(action, "Cannot create a TicTacToeAction from a null StreamableAction!");
        return new TicTacToeAction(action.getPosition(), action.getMark());
    }

    public static StreamableAction fromTicTacToeAction(final TicTacToeAction action)
    {
        Validate.notNull(action, "Cannot create a StreamableAction from a null TicTacToeAction!");
        return new StreamableAction(action);
    }
}
