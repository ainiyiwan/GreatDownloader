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
import com.zy.xxl.zyfiledownloader.download.filedownloader.util.FileDownloadLog;

/**
 * 已完成
 * Normally flow: {@link #pending} -> {@link #started} -> {@link #connected} -> {@link #progress}  ->
 * {@link #blockComplete} -> {@link #completed}
 * <p/>
 * Maybe over with: {@link #paused}/{@link #completed}/{@link #error}/{@link #warn}
 * <p/>
 * If the task has already downloaded and exist, you will only receive follow callbacks:
 * {@link #blockComplete} ->{@link #completed}
 *
 * @see FileDownloadLargeFileListener
 * @see com.zy.xxl.zyfiledownloader.download.filedownloader.notification.FileDownloadNotificationListener
 * @see BaseDownloadTask#setSyncCallback(boolean)
 */
// TODO: 2017/10/23 重要的类 流程都在这里
@SuppressWarnings({"WeakerAccess", "UnusedParameters"})
public abstract class FileDownloadListener {

    public FileDownloadListener() {
    }

    /**
     * 此方法已经不推荐使用 不再设置优先级
     * @param priority not handle priority any more
     * @deprecated not handle priority any more
     */
    public FileDownloadListener(int priority) {
        FileDownloadLog.w(this, "not handle priority any more");
    }

    /**
     *listener：监听器（Listener）: 当一个事件发生的时候，你希望获得这个事件发生的详细信息，而并不想干预这个事件本身的进程，这就要用到监听器。
     * 监听器是否已经无效
     * Whether this listener has already invalidated（使无效） to receive callbacks.
     *
     * @return {@code true} If you don't want to receive any callbacks for this listener.
     */
    protected boolean isInvalid() {
        return false;
    }

    /**
     * Enqueue, and pending, waiting for {@link #started(BaseDownloadTask)}.
     *
     * @param task       The task
     * @param soFarBytes Already downloaded bytes stored in the db
     * @param totalBytes Total bytes stored in the db
     * @see IFileDownloadMessenger#notifyPending(MessageSnapshot) (MessageSnapshot)
     */
    protected abstract void pending(final BaseDownloadTask task, final int soFarBytes,
                                    final int totalBytes);

    /**
     * Finish pending, and start the download runnable.
     *
     * @param task Current task.
     * @see IFileDownloadMessenger#notifyStarted(MessageSnapshot)
     */
    protected void started(final BaseDownloadTask task) {
    }

    /**
     * Already connected to the server, and received the Http-response.
     *
     * @param task       The task
     * @param etag       ETag
     * @param isContinue Is resume from breakpoint
     * @param soFarBytes Number of bytes download so far
     * @param totalBytes Total size of the download in bytes
     * @see IFileDownloadMessenger#notifyConnected(MessageSnapshot)
     */
    protected void connected(final BaseDownloadTask task, final String etag,
                             final boolean isContinue, final int soFarBytes, final int totalBytes) {

    }

    /**
     * Fetching datum（数据，资料） from network and Writing to the local disk.
     *
     * @param task       The task
     * @param soFarBytes Number of bytes download so far
     * @param totalBytes Total size of the download in bytes
     * @see IFileDownloadMessenger#notifyProgress(MessageSnapshot)
     */
    protected abstract void progress(final BaseDownloadTask task, final int soFarBytes,
                                     final int totalBytes);

    /**
     * Unlike other methods in {@link #FileDownloadListener}, BlockComplete is executed in other
     * thread than main as default, when you receive this execution, it means has already completed
     * downloading, but just block the execution of {@link #completed(BaseDownloadTask)}. therefore,
     * you can unzip or do some ending operation before {@link #completed(BaseDownloadTask)} in other
     * thread.
     *
     * @param task the current task
     * @throws Throwable if any {@code throwable} is thrown in this method, you will receive the
     *                   callback method of {@link #error(BaseDownloadTask, Throwable)} with the
     *                   {@code throwable} parameter instead of the {@link #completed(BaseDownloadTask)}.
     * @see IFileDownloadMessenger#notifyBlockComplete(MessageSnapshot)
     */
    protected void blockComplete(final BaseDownloadTask task) throws Throwable {
    }

    /**
     * Occur a exception and has chance{@link BaseDownloadTask#setAutoRetryTimes(int)} to retry and
     * start Retry.
     *
     * @param task          The task
     * @param ex            Why retry
     * @param retryingTimes How many times will retry
     * @param soFarBytes    Number of bytes download so far
     * @see IFileDownloadMessenger#notifyRetry(MessageSnapshot)
     */
    protected void retry(final BaseDownloadTask task, final Throwable ex, final int retryingTimes,
                         final int soFarBytes) {
    }

    // ======================= The task is over, if execute below methods =======================

    /**
     * Achieve complete ceremony.
     * <p/>
     * Complete downloading.
     *
     * @param task The task
     * @see IFileDownloadMessenger#notifyCompleted(MessageSnapshot)
     * @see #blockComplete(BaseDownloadTask)
     */
    protected abstract void completed(final BaseDownloadTask task);

    /**
     * Task is paused, the vast（巨大的） majority（多数） （vast majority绝大多数，大部份） of cases is invoking the {@link BaseDownloadTask#pause()}
     * manually（手动的）.
     *
     * @param task       The task
     * @param soFarBytes Number of bytes download so far
     * @param totalBytes Total size of the download in bytes
     * @see IFileDownloadMessenger#notifyPaused(MessageSnapshot) (com.liulishuo.filedownloader.message.MessageSnapshot)
     */
    protected abstract void paused(final BaseDownloadTask task, final int soFarBytes,
                                   final int totalBytes);

    /**
     * Occur a exception, but don't has any chance to retry.
     *
     * @param task The task
     * @param e    Any throwable on download pipeline
     * @see IFileDownloadMessenger#notifyError(MessageSnapshot) (com.liulishuo.filedownloader.message.MessageSnapshot)
     * @see com.zy.xxl.zyfiledownloader.download.filedownloader.exception.FileDownloadHttpException
     * @see com.zy.xxl.zyfiledownloader.download.filedownloader.exception.FileDownloadGiveUpRetryException
     * @see com.zy.xxl.zyfiledownloader.download.filedownloader.exception.FileDownloadOutOfSpaceException
     */
    protected abstract void error(final BaseDownloadTask task, final Throwable e);

    /**
     * There has already had some same Tasks(Same-URL & Same-SavePath) in Pending-Queue or is
     * running.
     * 调用warn 原因就是已经有这个任务
     * @param task The task
     * @see IFileDownloadMessenger#notifyWarn(MessageSnapshot) (com.liulishuo.filedownloader.message.MessageSnapshot)
     */
    protected abstract void warn(final BaseDownloadTask task);

}
