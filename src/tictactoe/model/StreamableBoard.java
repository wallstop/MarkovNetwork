package tictactoe.model;

import java.util.Map;
import java.util.stream.Collectors;

import tictactoe.TicTacToeBoard;
import tictactoe.TicTacToeMark;
import utils.StreamableVector2;
import utils.Validate;
import utils.Vector2;

import com.fasterxml.jackson.annotation.JsonProperty;

public class StreamableBoard
{
    @JsonProperty("width")
    private int width_;

    @JsonProperty("height")
    private int height_;

    @JsonProperty("countToWin")
    private int nInARow_;

    @JsonProperty("board")
    private Map<StreamableVector2, TicTacToeMark> board_;

    StreamableBoard(final TicTacToeBoard board)
    {
        board_ = board
                .getBoardAsMap()
                .entrySet()
                .stream()
                .collect(
                        Collectors.toMap(entry -> StreamableVector2.fromVector2(entry.getKey()),
                                entry -> entry.getValue()));
        nInARow_ = board.getContiguousMovesToWin();
        width_ = board.getWidth();
        height_ = board.getHeight();
    }

    public int getWidth()
    {
        return width_;
    }

    public int getHeight()
    {
        return height_;
    }

    public int getContiguousMovesToWin()
    {
        return nInARow_;
    }

    public Map<Vector2, TicTacToeMark> getBoardAsMap()
    {
        return board_
                .entrySet()
                .stream()
                .collect(
                        Collectors.toMap(entry -> StreamableVector2.toVector2(entry.getKey()),
                                entry -> entry.getValue()));
    }

    public static StreamableBoard fromTicTacToeBoard(final TicTacToeBoard board)
    {
        Validate.notNull(board, "Cannot create a StreamableBoard from a null TicTacToeBoard!");
        return new StreamableBoard(board);
    }

    public static TicTacToeBoard toTicTacToeBoard(final StreamableBoard board)
    {
        Validate.notNull(board, "Cannot create a TicTacToeBoard from a null StreamableBoard!");
        return new TicTacToeBoard(board.getWidth(), board.getHeight(),
                board.getContiguousMovesToWin(), board.getBoardAsMap());
    }
}
