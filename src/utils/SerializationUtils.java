package utils;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;

import core.utils.SerializationFactory;

/**
 * Helper class to read & write our objects into and out of JSON
 * 
 * Throws RuntimeExceptions if it can't properly parse the JSON/class
 * 
 * @author wallstop
 *
 */
public class SerializationUtils
{
    private static final Logger LOG = LoggerFactory.getLogger(SerializationUtils.class);

    private static final ObjectMapper MAPPER = newDefaultMapper();
    static
    {
        MAPPER.registerModule(SerializationFactory.newVector2SerializationModule());
        MAPPER.registerModule(SerializationFactory.newPlayerSerializationModule());
        MAPPER.registerModule(SerializationFactory.newScoreSerializationModule());
    }

    public static ObjectMapper newDefaultMapper()
    {
        final ObjectMapper defaultMapper = new ObjectMapper();
        defaultMapper.setVisibilityChecker(defaultMapper.getSerializationConfig()
                .getDefaultVisibilityChecker().withFieldVisibility(Visibility.ANY)
                .withGetterVisibility(Visibility.NONE).withSetterVisibility(Visibility.NONE)
                .withIsGetterVisibility(Visibility.NONE).withCreatorVisibility(Visibility.NONE));
        return defaultMapper;
    }

    public static void registerModules(final Module... modules)
    {
        Validate.notNull(modules, "Cannot register a null module!");
        MAPPER.registerModules(modules);
    }

    public static <T> T readValue(final String json, final Class<T> clazz)
    {
        try
        {
            return MAPPER.readValue(json, clazz);
        }
        catch(IOException e)
        {
            LOG.error("Could not convert {} into {}", new Object[] { json, clazz }, e);
            throw new IllegalArgumentException(e);
        }
    }

    public static <T> String writeValue(final T value)
    {
        try
        {
            return MAPPER.writeValueAsString(value);
        }
        catch(JsonProcessingException e)
        {
            LOG.error("Could not convert {} into json", value, e);
            throw new IllegalArgumentException(e);
        }
    }

}
