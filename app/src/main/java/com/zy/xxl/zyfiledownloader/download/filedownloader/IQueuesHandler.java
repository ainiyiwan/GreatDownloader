/*
 * Copyright (c) 2015 LingoChamp Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zy.xxl.zyfiledownloader.download.filedownloader;

import java.util.List;

/**
 * 已完成
 * The interface for handle affairs(事物) of queues.
 * 处理事物的队列
 */

public interface IQueuesHandler {

    /**
     * 并行
     * Start tasks which the same {@code listener} as a queue, and execute theme in parallel.
     *
     * @param listener Used to assemble tasks which is bound by the same {@code listener}
     * @return Whether start tasks successfully.
     */
    boolean startQueueParallel(FileDownloadListener listener);

    /**
     * 串行
     * Start tasks which the same {@code listener} as a queue, and execute theme one by one.
     *
     * @param listener Used to assemble tasks which is bound by the same {@code listener}
     * @return Whether start tasks successfully.
     */
    boolean startQueueSerial(FileDownloadListener listener);

    void freezeAllSerialQueues();

    void unFreezeSerialQueues(List<Integer> attachKeyList);

    int serialQueueSize();

    boolean contain(int attachKey);
}
