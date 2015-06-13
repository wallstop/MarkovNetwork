package utils;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class StreamableVector2
{
    @JsonProperty("x")
    private int x_;

    @JsonProperty("y")
    private int y_;

    StreamableVector2(final Vector2 vector)
    {
        x_ = vector.getX();
        y_ = vector.getY();
    }

    public int getX()
    {
        return x_;
    }

    public int getY()
    {
        return y_;
    }

    public static StreamableVector2 fromVector2(final Vector2 vector)
    {
        Validate.notNull(vector, "Cannot create a StreamableVector2 from a null Vector2!");
        return new StreamableVector2(vector);
    }

    public static Vector2 toVector2(final StreamableVector2 vector)
    {
        Validate.notNull(vector, "Cannot create a Vector2 from a null StreamableVector2!");
        return new Vector2(vector.getX(), vector.getY());
    }
}
