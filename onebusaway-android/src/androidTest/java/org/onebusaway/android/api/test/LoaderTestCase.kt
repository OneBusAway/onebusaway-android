package org.onebusaway.android.api.test

import android.os.AsyncTask
import android.os.Handler
import android.os.Looper
import androidx.loader.content.Loader
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
abstract class LoaderTestCase {
    fun <T> getLoaderResultSynchronously(loader: Loader<T>): T {
        val queue = ArrayBlockingQueue<T>(1)
        val listener = object : Loader.OnLoadCompleteListener<T> {
            override fun onLoadComplete(completedLoader: Loader<T>, data: T?) {
                completedLoader.unregisterListener(this)
                completedLoader.stopLoading()
                completedLoader.reset()
                queue.add(requireNotNull(data))
            }
        }
        Handler(Looper.getMainLooper()).post {
            loader.registerListener(0, listener)
            loader.startLoading()
        }
        return try {
            // Bounded so a loader that never completes fails the test instead of hanging the run.
            queue.poll(LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                ?: throw AssertionError("loader produced no result within ${LOAD_TIMEOUT_SECONDS}s")
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw RuntimeException("waiting thread interrupted", exception)
        }
    }

    companion object {
        private const val LOAD_TIMEOUT_SECONDS = 30L

        init {
            @Suppress("DEPRECATION")
            object : AsyncTask<Void, Void, Void>() {
                @Suppress("OVERRIDE_DEPRECATION")
                override fun doInBackground(vararg args: Void?): Void? = null
            }
        }
    }
}
