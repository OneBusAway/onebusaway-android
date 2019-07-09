/*
 * Copyright (C) 2019 University of South Florida
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.android.travelbehavior.io;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class TravelBehaviorFileSaverExecutorManager {

    private final ThreadPoolExecutor mThreadPoolExecutor;

    private final BlockingQueue<Runnable> mBlockingQueue;

    private static final int CORE_POOL_SIZE = 5;
    private static final int MAX_POOL_SIZE = 5;
    private static final int KEEP_ALIVE_TIME = 50;

    private static TravelBehaviorFileSaverExecutorManager mManager = null;

    static {
        mManager = new TravelBehaviorFileSaverExecutorManager();
    }

    private TravelBehaviorFileSaverExecutorManager(){
        mBlockingQueue = new LinkedBlockingQueue<Runnable>();
        mThreadPoolExecutor = new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE,
                KEEP_ALIVE_TIME, TimeUnit.MILLISECONDS, mBlockingQueue);
    }

    public static TravelBehaviorFileSaverExecutorManager getInstance(){
        return mManager;
    }

    public void runTask(Runnable task){
        mThreadPoolExecutor.execute(task);
    }

    public ThreadPoolExecutor getThreadPoolExecutor() {
        return mThreadPoolExecutor;
    }
}
