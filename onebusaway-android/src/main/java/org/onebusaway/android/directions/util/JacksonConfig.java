/*
 * Copyright 2013 University of South Florida
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.onebusaway.android.directions.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.module.SimpleModule;

import org.opentripplanner.api.ws.Response;
import org.opentripplanner.routing.patch.AlertHeaderText;

import android.content.Context;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.text.DecimalFormat;


/**
 * This class holds a static instance of a Jackson ObjectMapper and ObjectReader
 * that are configured for parsing server JSON responses.
 *
 * The ObjectMapper, ObjectReader, and XmlMapper are thread-safe after it is
 * configured: http://wiki.fasterxml.com/JacksonFAQThreadSafety
 *
 * ...so we can configure it once here and then use it in multiple fragments.
 *
 * @author Sean J. Barbeau
 */
public class JacksonConfig {

    // For JSON
    private static ObjectMapper mapper = null;

    private static ObjectReader reader = null;

    // For caching objects (ObjectMapper, ObjectReader, and XmlMapper) if
    // desired
    private static Context context = null;

    // Used to time cache read and write
    private static long cacheReadStartTime = 0;

    private static long cacheReadEndTime = 0;

    private static long cacheWriteStartTime = 0;

    private static long cacheWriteEndTime = 0;

    private static boolean usingCache = false;

    // Constants for defining which object type to read/write from/to cache
    private static final String OBJECT_READER = "ObjectReader";

    private static final String OBJECT_MAPPER = "ObjectMapper";

    private static final String CACHE_FILE_EXTENSION = ".cache";

    private static final String TAG = "JacksonConfig";

    // Used to format decimals to 3 places
    static DecimalFormat df = new DecimalFormat("#,###.###");

    // Private empty constructor since this object shouldn't be instantiated
    private JacksonConfig() {
    }

    /**
     * Returns true if the application is using a cache to read/write serialized
     * Jackson ObjectMapper/ObjectReader/XmlMapper to reduce cold-start latency,
     * false if it is not
     *
     * @return true if the application is using a cache to read/write serialized
     * Jackson ObjectMapper/ObjectReader/XmlMapper to reduce cold-start
     * latency, false if it is not
     */
    public static boolean isUsingCache() {
        // Check to see if the context is null. If it is, we can't cache data.
        if (usingCache && context != null) {
            return true;
        } else {
            if (!usingCache || context == null) {
                return false;
            }
        }

        // Should never reach here
        return usingCache;
    }

    /**
     * True if the application should use a cache to read/write serialized
     * Jackson ObjectMapper/ObjectReader/XmlMapper to reduce cold-start latency,
     * false if it should not
     *
     * @param usingCache True if the application should use a cache to read/write
     *                   serialized Jackson ObjectMapper/ObjectReader/XmlMapper to
     *                   reduce cold-start latency, false if it should not
     * @param context    Context that should be used to access the cache location.
     *                   getApplicationContext() is suggested, since the Jackson
     *                   Objects are thread-safe and static
     */
    public static void setUsingCache(boolean usingCache, Context context) {
        JacksonConfig.usingCache = usingCache;
        JacksonConfig.context = context;
    }

    /**
     * Returns a benchmark of the amount of time the last cache read took for
     * the ObjectMapper or ObjectReader or XmlReader (in nanoseconds)
     *
     * @return a benchmark of the amount of time the last cache read took for
     * the ObjectMapper or ObjectReader or XmlReader (in nanoseconds)
     */
    public static long getLastCacheReadTime() {
        return cacheReadEndTime - cacheReadStartTime;
    }

    /**
     * Returns a benchmark of the amount of time the last cache write took for
     * the ObjectMapper or ObjectReader or XmlReader (in nanoseconds)
     *
     * @return a benchmark of the amount of time the last cache write took for
     * the ObjectMapper or ObjectReader or XmlReader (in nanoseconds)
     */
    public static long getLastCacheWriteTime() {
        return cacheWriteEndTime - cacheWriteStartTime;
    }

    /**
     * Constructs a thread-safe instance of a Jackson ObjectMapper configured to
     * parse JSON responses from a OTP REST API.
     *
     * According to Jackson Best Practices
     * (http://wiki.fasterxml.com/JacksonBestPracticesPerformance), for
     * efficiency reasons you should use the ObjectReader (via
     * getObjectReaderInstance()) instead of the ObjectMapper.
     *
     * @return thread-safe ObjectMapper configured for OTP JSON responses
     * @deprecated
     */
    public synchronized static ObjectMapper getObjectMapperInstance() {
        return initObjectMapper();
    }

    /**
     * Constructs a thread-safe instance of a Jackson ObjectReader configured to
     * parse JSON responses from a Mobile OTP API
     *
     * According to Jackson Best Practices
     * (http://wiki.fasterxml.com/JacksonBestPracticesPerformance), this should
     * be more efficient than the ObjectMapper.
     *
     * @return thread-safe ObjectMapper configured for OTP JSON responses
     */
    public synchronized static ObjectReader getObjectReaderInstance() {
        if (reader == null) {
            /**
             * We don't have a reference to an ObjectReader, so we need to read
             * from cache or instantiate a new one
             */
            if (usingCache) {
                reader = (ObjectReader) readFromCache(OBJECT_READER);

                if (reader != null) {
                    // Successful read from the cache
                    return reader;
                }
            }

            /**
             * If we reach this point then we're either not reading from the
             * cache, there was nothing in the cache to retrieve, or there was
             * an error reading from the cache.
             *
             * Instantiate the object like normal.
             */
            reader = initObjectMapper().reader(Response.class);
        }
        return reader;
    }

    /**
     * Internal method used to init main ObjectMapper for JSON parsing
     *
     * @return initialized ObjectMapper ready for JSON parsing
     */
    private static ObjectMapper initObjectMapper() {
        if (mapper == null) {
            /**
             * We don't have a reference to an ObjectMapper, so we need to read
             * from cache or instantiate a new one
             */
            if (usingCache) {
                mapper = (ObjectMapper) readFromCache(OBJECT_MAPPER);

                if (mapper != null) {
                    // Successful read from the cache
                    return mapper;
                }
            }

            /**
             * If we reach this point then we're either not reading from the
             * cache, there was nothing in the cache to retrieve, or there was
             * an error reading from the cache.
             *
             * Instantiate the object like normal.
             */
            // Jackson configuration
            mapper = new ObjectMapper();

            mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
            mapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
            mapper.configure(DeserializationFeature.USE_JAVA_ARRAY_FOR_JSON_ARRAY, true);
            mapper.configure(DeserializationFeature.READ_ENUMS_USING_TO_STRING, true);
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            // AlertHeaderText class is out of date and will throw an error if we try to deserialize
            // from latest OTP. Simply ignore for now.
            SimpleModule module = new SimpleModule();
            module.addDeserializer(AlertHeaderText.class, new JsonDeserializer<AlertHeaderText>() {
                @Override
                public AlertHeaderText deserialize(JsonParser p, DeserializationContext ctxt)
                        throws IOException {
                    Log.d(TAG, "Ignoring AlertHeaderText object.");
                    return null;
                }
            });
            mapper.registerModule(module);
        }
        return mapper;
    }


    /**
     * Forces the write of a ObjectMapper or ObjectReader to the app
     * cache. The cache is used to reduce the cold-start delay for Jackson
     * parsing on future runs, after this VM instance is destroyed.
     *
     * Applications may call this after a JSON or XML call to the server to
     * attempt to hide the cache write latency from the user, instead of having
     * the cache write occur as part of the first request to use the
     * ObjectMapper or ObjectReader.
     *
     *
     * This method is non-blocking.
     *
     * @param object object to be written to the cache
     */
    public static void forceCacheWrite(final Serializable object) {
        if (isUsingCache()) {
            new Thread() {
                public void run() {
                    writeToCache(object);
                }
            }.start();
        } else {
            Log.w(TAG,
                    "App tried to force a cache write but caching is not activated.  If you want to use the cache, call JacksonConfig.setUsingCache(true, context) with a reference to your context.");
        }
    }

    /**
     * Forces the read of a ObjectMapper or ObjectReader from the
     * app cache to be stored as a static instance in this object. The cache is
     * used to reduce the cold-start delay for Jackson parsing on future runs,
     * after this VM instance is destroyed.
     *
     * Applications should call this on startup to attempt to hide the cache
     * read latency from the user, instead of having the cache read occur on the
     * first request to use the ObjectMapper or ObjectReader.
     *
     * This method is non-blocking.
     */
    public static void forceCacheRead() {
        if (isUsingCache()) {
            new Thread() {
                public void run() {
                    readFromCache(OBJECT_MAPPER);
                    readFromCache(OBJECT_READER);
                }
            }.start();
        } else {
            Log.w(TAG,
                    "App tried to force a cache write but caching is not activated.  If you want to use the cache, call JacksonConfig.setUsingCache(true, context) with a reference to your context.");
        }
    }

    /**
     * Write the given object to Android internal storage for this app
     *
     * @param object serializable object to be written to cache (ObjectReader,
     *               ObjectMapper, or XmlReader)
     * @return true if object was successfully written to cache, false if it was
     * not
     */
    private synchronized static boolean writeToCache(Serializable object) {

        FileOutputStream fileStream = null;
        ObjectOutputStream objectStream = null;
        String fileName = "";
        boolean success = false;

        if (context != null) {
            try {
                if (object instanceof ObjectMapper) {
                    fileName = OBJECT_MAPPER + CACHE_FILE_EXTENSION;
                }
                if (object instanceof ObjectReader) {
                    fileName = OBJECT_READER + CACHE_FILE_EXTENSION;
                }

                cacheWriteStartTime = System.nanoTime();
                fileStream = context.openFileOutput(fileName,
                        Context.MODE_PRIVATE);
                objectStream = new ObjectOutputStream(fileStream);
                objectStream.writeObject(object);
                objectStream.flush();
                fileStream.getFD().sync();
                cacheWriteEndTime = System.nanoTime();
                success = true;

                // Get size of serialized object
                long fileSize = context.getFileStreamPath(fileName).length();

                Log.d("TAG", "Wrote " + fileName + " to cache (" + fileSize
                        + " bytes) in " + df.format(getLastCacheWriteTime())
                        + " ms.");
            } catch (IOException e) {
                // Reset timestamps to show there was an error
                cacheWriteStartTime = 0;
                cacheWriteEndTime = 0;
                Log.e(TAG, "Couldn't write Jackson object '" + fileName + "' to cache: " + e);
            } finally {
                try {
                    if (objectStream != null) {
                        objectStream.close();
                    }
                    if (fileStream != null) {
                        fileStream.close();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error closing file connections: " + e);
                }
            }
        } else {
            Log.w(TAG,
                    "Can't write to cache - no context provided.  If you want to use the cache, call JacksonConfig.setUsingCache(true, context) with a reference to your context.");
        }

        return success;
    }

    /**
     * Read the given object from Android internal storage for this app
     *
     * @param objectType object type, defined by class constant Strings, to retrieve
     *                   from cache (ObjectReader, ObjectMapper, or XmlReader)
     * @return deserialized Object, or null if object couldn't be deserialized
     */
    private static synchronized Serializable readFromCache(String objectType) {

        FileInputStream fileStream = null;
        ObjectInputStream objectStream = null;

        // Holds object to be read from cache
        Serializable object = null;

        // Before reading from cache, check to make sure that we don't already
        // have the requested object in memory
        if (objectType.equalsIgnoreCase(OBJECT_MAPPER) && mapper != null) {
            return mapper;
        }
        if (objectType.equalsIgnoreCase(OBJECT_READER) && reader != null) {
            return reader;
        }

        if (context != null) {
            try {
                String fileName = objectType + CACHE_FILE_EXTENSION;

                cacheReadStartTime = System.nanoTime();
                fileStream = context.openFileInput(fileName);
                objectStream = new ObjectInputStream(fileStream);
                object = (Serializable) objectStream.readObject();
                cacheReadEndTime = System.nanoTime();

                // Get size of serialized object
                long fileSize = context.getFileStreamPath(fileName).length();

                Log.d("TAG", "Read " + fileName + " from cache (" + fileSize
                        + " bytes) in " + df.format(getLastCacheReadTime())
                        + " ms.");
            } catch (FileNotFoundException e) {
                Log.w(TAG, "Cache miss - Jackson object '" + objectType
                        + "' does not exist in app cache: " + e);
                return null;
            } catch (Exception e) {
                // Reset timestamps to show there was an error
                cacheReadStartTime = 0;
                cacheReadEndTime = 0;
                Log.e(TAG, "Couldn't read Jackson object '" + objectType + "' from cache: " + e);
            } finally {
                try {
                    if (objectStream != null) {
                        objectStream.close();
                    }
                    if (fileStream != null) {
                        fileStream.close();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error closing cache file connections: " + e);
                }
            }

            if (object instanceof ObjectMapper) {
                mapper = (ObjectMapper) object;
            }
            if (object instanceof ObjectReader) {
                reader = (ObjectReader) object;
            }

            return object;
        } else {
            Log.w(TAG,
                    "Couldn't read from cache - no context provided.  If you want to use the cache, call JacksonConfig.setUsingCache(true, context) with a reference to your context.");
            return null;
        }
    }
}
