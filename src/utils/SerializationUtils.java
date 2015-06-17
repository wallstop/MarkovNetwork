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
 * Helper class to read & write our objects into and out of JSON Throws
 * RuntimeExceptions if it can't properly parse the JSON/class
 *
 * @author wallstop
 */
public final class SerializationUtils
{
    private static final Logger LOG = LoggerFactory.getLogger(SerializationUtils.class);

    private static final ObjectMapper MAPPER = newDefaultMapper();
    static
    {
        /*
         * Set up core class serializers. While Jackson normally does a great
         * job of automagically constructing classes, it has an edge case for
         * Map Keys. As such, we need to specify exactly how to serialize &
         * deserialize Map Keys for every core class, or we'll run into a ton of
         * json parsing exceptions
         */
        registerModules(SerializationFactory.newVector2SerializationModule());
        registerModules(SerializationFactory.newPlayerSerializationModule());
        registerModules(SerializationFactory.newScoreSerializationModule());
    }

    /**
     * Returns an un-decorated ObjectMapper similar to the one that this class
     * uses, but without the Modules. The visibility settings between
     * SerializationUtil's internal mapper and this one will be similar.
     *
     * @return An ObjectMapper with configured visibilities.
     */
    public static ObjectMapper newDefaultMapper()
    {
        final ObjectMapper defaultMapper = new ObjectMapper();
        defaultMapper.setVisibilityChecker(defaultMapper.getSerializationConfig()
                .getDefaultVisibilityChecker().withFieldVisibility(Visibility.ANY)
                .withGetterVisibility(Visibility.NONE).withSetterVisibility(Visibility.NONE)
                .withIsGetterVisibility(Visibility.NONE).withCreatorVisibility(Visibility.NONE));
        return defaultMapper;
    }

    /**
     * @param modules
     */
    public static void registerModules(final Module... modules)
    {
        Validate.notNull(modules, "Cannot register a null module!");
        LOG.info("Registering modules {}", modules);
        MAPPER.registerModules(modules);
    }

    /**
     * @param json
     * @param clazz
     * @return
     */
    public static <T> T readValue(final String json, final Class<T> clazz)
    {
        try
        {
            return MAPPER.readValue(json, clazz);
        }
        catch(final IOException e)
        {
            LOG.error("Could not convert {} into {}", new Object[] { json, clazz }, e);
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Converts the provided value to it's corresponding JSON string. Assumes
     * that the provided value can be properly JSON-ified
     *
     * @param value
     *            non-null value to turn into a JSON string
     * @return Value as a JSON string
     * @throws IllegalArgumentException
     *             if the value could not be mapped to a JSON string.
     */
    public static <T> String writeValue(final T value)
    {
        try
        {
            return MAPPER.writeValueAsString(value);
        }
        catch(final JsonProcessingException e)
        {
            LOG.error("Could not convert {} into json", value, e);
            throw new IllegalArgumentException(e);
        }
    }

}
