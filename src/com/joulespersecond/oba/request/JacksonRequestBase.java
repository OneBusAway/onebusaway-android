package com.joulespersecond.oba.request;

import com.joulespersecond.oba.ObaApi;
import com.joulespersecond.oba.ObaHelp;

import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.introspect.VisibilityChecker;
import org.codehaus.jackson.node.TreeTraversingParser;

import android.net.Uri;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;

/*
 * Experiment with Jackson library for deserialization
 * So far, much faster than Json with same functionality
 */
public class JacksonRequestBase
    extends RequestBase {

    protected JacksonRequestBase(Uri uri) {
        super(uri);
    }

    private static final ObjectMapper jacksonMapper = new ObjectMapper();
    static {
        jacksonMapper.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES,false);
        jacksonMapper.setVisibilityChecker(
                VisibilityChecker.Std.defaultInstance()
                .withFieldVisibility(JsonAutoDetect.Visibility.ANY));
    }

    public static JsonParser getJsonParser(Reader reader)
            throws IOException, JsonProcessingException {
        TreeTraversingParser parser = new TreeTraversingParser(jacksonMapper.readTree(reader));
        parser.setCodec(jacksonMapper);
        return parser;
    }

    @Override
    protected <T> T call(Class<T> cls) {
        try {
            Reader reader = ObaHelp.getUri(mUri);
            T t = getJsonParser(reader).readValueAs(cls);
            if (t == null) {
                // TODO: test switching from Gson for errors
                t = createFromError(cls, ObaApi.OBA_INTERNAL_ERROR, "Json error");
            }
            return t;
        }
        catch (FileNotFoundException e) {
            return createFromError(cls, ObaApi.OBA_NOT_FOUND, e.toString());
        }
        catch (JsonProcessingException e) {
            return createFromError(cls, ObaApi.OBA_INTERNAL_ERROR, e.toString());
        }
        catch (IOException e) {
            return createFromError(cls, ObaApi.OBA_IO_EXCEPTION, e.toString());
        }
    }
}
