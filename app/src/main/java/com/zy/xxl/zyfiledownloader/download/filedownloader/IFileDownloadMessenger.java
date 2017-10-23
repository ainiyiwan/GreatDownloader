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


import com.zy.xxl.zyfiledownloader.download.filedownloader.message.MessageSnapshot;
import com.zy.xxl.zyfiledownloader.download.filedownloader.model.FileDownloadModel;

/**
 * @see com.zy.xxl.zyfiledownloader.download.filedownloader.model.FileDownloadStatus
 */
// TODO: 2017/10/23 整个类的方法 以及它指向的需要阅读的类 估计这个类会延续到最后
interface IFileDownloadMessenger {

    /**
     * The task is just received to handle.
     * <p/>
     * FileDownloader accept the task.
     *
     * @return Whether allow it to begin.
     */
    boolean notifyBegin();

    /**
     * The task is pending.
     * <p/>
     * enqueue, and pending( 未决定的；行将发生的), waiting.
     *
     * @see com.zy.xxl.zyfiledownloader.download.filedownloader.services.FileDownloadThreadPool
     */
    void notifyPending(MessageSnapshot snapshot);

    /**
     * The download runnable of the task has started running.
     * <p/>
     * Finish pending, and start download runnable.
     *
     * @see com.zy.xxl.zyfiledownloader.download.filedownloader.download.DownloadStatusCallback#onStartThread()
     */
    void notifyStarted(MessageSnapshot snapshot);

    /**
     * The task is running.
     * <p/>
     * Already connected to the server, and received the Http-response.
     *
     * @see com.zy.xxl.zyfiledownloader.download.filedownloader.download.DownloadStatusCallback#onConnected(boolean, long, String, String)
     */
    void notifyConnected(MessageSnapshot snapshot);

    /**
     * The task is running.
     * <p/>
     * Fetching datum, and write to local disk.
     *
     * @see com.zy.xxl.zyfiledownloader.download.filedownloader.download.DownloadStatusCallback#onProgress(long)
     */
    void notifyProgress(MessageSnapshot snapshot);

    /**
     * The task is running.
     * <p/>
     * Already completed download, and block the current thread to do something, such as unzip,etc.
     *
     * @see com.zy.xxl.zyfiledownloader.download.filedownloader.download.DownloadStatusCallback#onCompletedDirectly()
     */
    void notifyBlockComplete(MessageSnapshot snapshot);

    /**
     * The task over.
     * <p/>
     * Occur a exception when downloading, but has retry
     * chance {@link BaseDownloadTask#setAutoRetryTimes(int)}, so retry(re-connect,re-download).
     */
    void notifyRetry(MessageSnapshot snapshot);

    /**
     * The task over.
     * <p/>
     * There has already had some same Tasks(Same-URL & Same-SavePath) in Pending-Queue or is
     * running.
     * 有相同的人物 或者URL 或者保存路径在等待队列 或者已经在运行
     *
     * @see com.zy.xxl.zyfiledownloader.download.filedownloader.util.FileDownloadHelper#inspectAndInflowDownloading(int, FileDownloadModel, IThreadPoolMonitor, boolean) (int, FileDownloadModel, IThreadPoolMonitor, boolean)
     */
    void notifyWarn(MessageSnapshot snapshot);

    /**
     * The task is over.
     * <p/>
     * Occur a exception, but don't has any chance to retry.
     *
     * @see com.zy.xxl.zyfiledownloader.download.filedownloader.download.DownloadStatusCallback#onErrorDirectly(Exception)
     * @see com.zy.xxl.zyfiledownloader.download.filedownloader.exception.FileDownloadHttpException
     * @see com.zy.xxl.zyfiledownloader.download.filedownloader.exception.FileDownloadOutOfSpaceException
     * @see com.zy.xxl.zyfiledownloader.download.filedownloader.exception.FileDownloadGiveUpRetryException
     */
    void notifyError(MessageSnapshot snapshot);

    /**
     * The task is over.
     * <p/>
     * Pause manually by {@link BaseDownloadTask#pause()}.
     *
     * @see BaseDownloadTask#pause()
     */
    void notifyPaused(MessageSnapshot snapshot);

    /**
     * The task is over.
     * <p/>
     * Achieve complete ceremony.
     *
     * @see com.zy.xxl.zyfiledownloader.download.filedownloader.download.DownloadStatusCallback#onCompletedDirectly()
     */
    void notifyCompleted(MessageSnapshot snapshot);

    /**
     * Handover(移交) a message to {@link FileDownloadListener}.
     * @see FileDownloadListener
     */
    void handoverMessage();

    /**
     * @return {@code true} if handover a message to {@link FileDownloadListener} directly(do not
     * need to post the callback to the main thread).
     * @see BaseDownloadTask#isSyncCallback()
     */
    boolean handoverDirectly();

    /**
     * @param task Re-appointment(连任；复职) for this task, when this messenger has already accomplished the
     *             old one.
     */
    void reAppointment(BaseDownloadTask.IRunningTask task, BaseDownloadTask.LifeCycleCallback callback);

    /**
     * The 'block completed'(status) message will be handover in the non-UI thread and block the
     * 'completed'(status) message.
     *
     * @return {@code true} if the status of the current message is
     * {@link com.zy.xxl.zyfiledownloader.download.filedownloader.model.FileDownloadStatus#blockComplete}.
     */
    boolean isBlockingCompleted();

    /**
     * Discard(抛弃；放弃；丢弃) this messenger.
     * <p>
     * If this messenger is discarded, all messages sent by this messenger or feature messages
     * handled by this messenger will be discard, no longer callback to the target Listener.
     */
    void discard();
}
