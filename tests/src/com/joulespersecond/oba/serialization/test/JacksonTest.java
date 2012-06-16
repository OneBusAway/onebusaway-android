package com.joulespersecond.oba.serialization.test;

import com.joulespersecond.oba.ObaApi;
import com.joulespersecond.oba.request.ObaResponse;
import com.joulespersecond.oba.request.ObaStopsForLocationResponse;
import com.joulespersecond.oba.request.test.ObaTestCase;
import com.joulespersecond.oba.serialization.JacksonSerializer;

import org.codehaus.jackson.annotate.JsonPropertyOrder;

import android.util.Log;

import java.io.Reader;

public class JacksonTest extends ObaTestCase {
    protected JacksonSerializer mSerializer;
    private static final int mCode = 47421;
    private static final String mErrText = "Here is an error";

    public void testPrimitive() {
        mSerializer = (JacksonSerializer)JacksonSerializer.getInstance();
        String test = mSerializer.toJson("abc");
        assertEquals("\"abc\"", test);

        test = mSerializer.toJson("a\\b\\c");
        assertEquals("\"a\\\\b\\\\c\"", test);
    }

    public void testError() {
        mSerializer = (JacksonSerializer)JacksonSerializer.getInstance();
        ObaResponse response = mSerializer.createFromError(ObaResponse.class, mCode, mErrText);
        assertEquals(mCode, response.getCode());
        assertEquals(mErrText, response.getText());
    }

    public void testSerialization() {
        mSerializer = (JacksonSerializer)JacksonSerializer.getInstance();
        String errJson = mSerializer.serialize(new MockResponse());
        Log.d("*** test", errJson);
        String expected = String.format("{\"code\":%d,\"version\":\"2\",\"text\":\"%s\"}", mCode, mErrText);
        Log.d("*** expect", expected);
        assertEquals(expected, errJson);
    }

    public void testStopsForLocation() throws Exception {
        Reader reader = readResource(TEST_RAW_URI + "stops_for_location_downtown_seattle");
        ObaApi.SerializationHandler serializer = ObaApi.getSerializer(ObaStopsForLocationResponse.class);
        ObaStopsForLocationResponse response = serializer.deserialize(reader, ObaStopsForLocationResponse.class);
        assertNotNull(response);
    }

    @JsonPropertyOrder(value={"code", "version", "text"})
    public class MockResponse {
        @SuppressWarnings("unused")
        private final String version;
        @SuppressWarnings("unused")
        private final int code;
        @SuppressWarnings("unused")
        private final String text;

        protected MockResponse() {
            version = "2";
            code = mCode;
            text = mErrText;
        }
    }
}
