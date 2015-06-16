package core.utils;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

import core.Player;
import utils.SerializationUtils;
import utils.Vector2;

public class KeySerializationFactory
{
    public static Module newVector2SerializationModule()
    {
        return new SimpleModule().addKeyDeserializer(Vector2.class, newVector2KeyDeserializer())
                .addKeySerializer(Vector2.class, newVector2KeySerializer());
    }

    public static KeyDeserializer newVector2KeyDeserializer()
    {
        return new KeyDeserializer()
        {
            @Override
            public Vector2 deserializeKey(final String jsonKey, final DeserializationContext context)
            {
                return SerializationUtils.readValue(jsonKey, Vector2.class);
            }
        };
    }

    public static JsonSerializer<Vector2> newVector2KeySerializer()
    {
        return new JsonSerializer<Vector2>()
        {
            @Override
            public void serialize(final Vector2 vector2, final JsonGenerator generator,
                    final SerializerProvider serializers) throws IOException
            {
                final String json = SerializationUtils.writeValue(vector2);
                generator.writeFieldName(json);
            }
        };
    }

    public static Module newPlayerSerializationModule()
    {
        return new SimpleModule().addKeyDeserializer(Player.class,
                KeySerializationFactory.newPlayerKeyDeserializer()).addKeySerializer(Player.class,
                KeySerializationFactory.newPlayerKeySerializer());
    }

    public static KeyDeserializer newPlayerKeyDeserializer()
    {
        return new KeyDeserializer()
        {
            @Override
            public Player deserializeKey(final String jsonKey, final DeserializationContext context)
            {
                return SerializationUtils.readValue(jsonKey, Player.class);
            }
        };
    }

    public static JsonSerializer<Player> newPlayerKeySerializer()
    {
        return new JsonSerializer<Player>()
        {

            @Override
            public void serialize(final Player player, final JsonGenerator generator,
                    final SerializerProvider serializers) throws IOException
            {
                final String json = SerializationUtils.writeValue(player);
                generator.writeFieldName(json);
            }
        };
    }
}
