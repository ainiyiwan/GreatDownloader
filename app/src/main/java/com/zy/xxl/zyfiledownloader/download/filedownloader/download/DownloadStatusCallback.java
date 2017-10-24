/*
 * Copyright (c) 2015 LingoChamp Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zy.xxl.zyfiledownloader.download.filedownloader.download;

import android.database.sqlite.SQLiteFullException;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.SystemClock;

import com.zy.xxl.zyfiledownloader.download.filedownloader.exception.FileDownloadGiveUpRetryException;
import com.zy.xxl.zyfiledownloader.download.filedownloader.exception.FileDownloadOutOfSpaceException;
import com.zy.xxl.zyfiledownloader.download.filedownloader.message.MessageSnapshotFlow;
import com.zy.xxl.zyfiledownloader.download.filedownloader.message.MessageSnapshotTaker;
import com.zy.xxl.zyfiledownloader.download.filedownloader.model.FileDownloadModel;
import com.zy.xxl.zyfiledownloader.download.filedownloader.model.FileDownloadStatus;
import com.zy.xxl.zyfiledownloader.download.filedownloader.services.FileDownloadBroadcastHandler;
import com.zy.xxl.zyfiledownloader.download.filedownloader.services.FileDownloadDatabase;
import com.zy.xxl.zyfiledownloader.download.filedownloader.util.FileDownloadLog;
import com.zy.xxl.zyfiledownloader.download.filedownloader.util.FileDownloadProperties;
import com.zy.xxl.zyfiledownloader.download.filedownloader.util.FileDownloadUtils;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

import static com.zy.xxl.zyfiledownloader.download.filedownloader.download.FetchDataTask.BUFFER_SIZE;
import static com.zy.xxl.zyfiledownloader.download.filedownloader.model.FileDownloadModel
        .TOTAL_VALUE_IN_CHUNKED_RESOURCE;


/**
 * 已完成
 * 下载状态回调
 * handle all events sync to DB/filesystem and callback to user.
 */
// TODO: 2017/10/24 所有关于下载过程中的回调 以及temp的处理都在这里
public class DownloadStatusCallback implements Handler.Callback {

    private final FileDownloadModel model;
    private final FileDownloadDatabase database;
    private final ProcessParams processParams;
    private final int maxRetryTimes;


    private static final int CALLBACK_SAFE_MIN_INTERVAL_BYTES = 1;//byte
    private static final int CALLBACK_SAFE_MIN_INTERVAL_MILLIS = 5;//ms
    /**
     * 没有任何下载进度回调
     */
    private static final int NO_ANY_PROGRESS_CALLBACK = -1;

    private final int callbackProgressMinInterval;
    private final int callbackProgressMaxCount;
    private long callbackMinIntervalBytes;

    private Handler handler;
    private HandlerThread handlerThread;

    DownloadStatusCallback(FileDownloadModel model,
                           int maxRetryTimes, final int minIntervalMillis,
                           int callbackProgressMaxCount) {
        this.model = model;
        this.database = CustomComponentHolder.getImpl().getDatabaseInstance();
        this.callbackProgressMinInterval = minIntervalMillis < CALLBACK_SAFE_MIN_INTERVAL_MILLIS
                ? CALLBACK_SAFE_MIN_INTERVAL_MILLIS : minIntervalMillis;
        this.callbackProgressMaxCount = callbackProgressMaxCount;
        this.processParams = new ProcessParams();
        this.maxRetryTimes = maxRetryTimes;
    }

    public boolean isAlive() {
        return handlerThread != null && handlerThread.isAlive();
    }

    private volatile boolean handlingMessage = false;
    private volatile Thread parkThread;

    /**
     * 丢弃所有message
     */
    void discardAllMessage() {
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
            handlerThread.quit();

            parkThread = Thread.currentThread();
            while (handlingMessage) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(100));
            }
            parkThread = null;
        }
    }


    public void onPending() {
        model.setStatus(FileDownloadStatus.pending);

        // direct
        database.updatePending(model.getId());
        onStatusChanged(FileDownloadStatus.pending);
    }

    void onStartThread() {
        // direct
        model.setStatus(FileDownloadStatus.started);
        onStatusChanged(FileDownloadStatus.started);
    }

    void onConnected(boolean isResume, long totalLength, String etag, String fileName) throws
            IllegalArgumentException {
        final String oldEtag = model.getETag();
        if (oldEtag != null && !oldEtag.equals(etag)) throw
                new IllegalArgumentException(FileDownloadUtils.formatString("callback " +
                                "onConnected must with precondition（前提；先决条件） succeed, but the etag（头部） is changes(%s != %s)",
                        etag, oldEtag));

        // direct
        processParams.setResuming(isResume);

        model.setStatus(FileDownloadStatus.connected);
        model.setTotal(totalLength);
        model.setETag(etag);
        model.setFilename(fileName);

        database.updateConnected(model.getId(), totalLength, etag, fileName);
        onStatusChanged(FileDownloadStatus.connected);

        callbackMinIntervalBytes = calculateCallbackMinIntervalBytes(totalLength,
                callbackProgressMaxCount);
        needSetProcess = true;
    }

    void onMultiConnection() {
        handlerThread = new HandlerThread("source-status-callback");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper(), this);
    }

    private volatile long lastCallbackTimestamp = 0;

    private final AtomicLong callbackIncreaseBuffer = new AtomicLong();

    void onProgress(long increaseBytes) {
        callbackIncreaseBuffer.addAndGet(increaseBytes);
        model.increaseSoFar(increaseBytes);


        final long now = SystemClock.elapsedRealtime();

        final boolean isNeedCallbackToUser = isNeedCallbackToUser(now);

        if (handler == null) {
            // direct
            handleProgress(now, isNeedCallbackToUser);
        } else if (isNeedCallbackToUser) {
            // flow
            sendMessage(handler.obtainMessage(FileDownloadStatus.progress));
        }
    }

    void onRetry(Exception exception, int remainRetryTimes, long invalidIncreaseBytes) {
        this.callbackIncreaseBuffer.set(0);
        model.increaseSoFar(-invalidIncreaseBytes);

        if (handler == null) {
            // direct
            handleRetry(exception, remainRetryTimes);
        } else {
            // flow
            sendMessage(handler.obtainMessage(FileDownloadStatus.retry, remainRetryTimes, 0,
                    exception));
        }
    }


    void onPausedDirectly() {
        handlePaused();
    }

    void onErrorDirectly(Exception exception) {
        handleError(exception);
    }

    void onCompletedDirectly() throws IOException {
        if (interceptBeforeCompleted()) {
            return;
        }

        handleCompleted();
    }

    private final static String ALREADY_DEAD_MESSAGE = "require callback %d but the host thread of the flow has " +
            "already dead, what is occurred because of there are several reason can " +
            "final this flow on different thread.";

    /**
     * 发送消息
     * @param message
     */
    private synchronized void sendMessage(Message message) {

        if (!handlerThread.isAlive()) {
            if (FileDownloadLog.NEED_LOG) {
                FileDownloadLog.d(this, ALREADY_DEAD_MESSAGE, message.what);
            }
            return;
        }

        try {
            handler.sendMessage(message);
        } catch (IllegalStateException e) {
            if (!handlerThread.isAlive()) {
                if (FileDownloadLog.NEED_LOG) {
                    FileDownloadLog.d(this, ALREADY_DEAD_MESSAGE, message.what);
                }
            } else {
                // unknown error
                throw e;
            }
        }
    }

    /**
     * 计算最小的回调字节间隔 Interval（间隔；间距；幕间休息）
     * @param contentLength
     * @param callbackProgressMaxCount
     * @return
     */
    private static long calculateCallbackMinIntervalBytes(final long contentLength,
                                                          final long callbackProgressMaxCount) {
        if (callbackProgressMaxCount <= 0) {
            return NO_ANY_PROGRESS_CALLBACK;
        } else if (contentLength == TOTAL_VALUE_IN_CHUNKED_RESOURCE) {
            return CALLBACK_SAFE_MIN_INTERVAL_BYTES;
        } else {
            final long minIntervalBytes = contentLength / (callbackProgressMaxCount + 1);
            return minIntervalBytes <= 0 ? CALLBACK_SAFE_MIN_INTERVAL_BYTES : minIntervalBytes;
        }
    }

    /**
     * 异常过滤 filtrate（过滤）
     * @param ex
     * @return
     */
    private Exception exFiltrate(Exception ex) {
        final String tempPath = model.getTempFilePath();
        /**
         * Only handle the case of Chunked resource, if it is not chunked, has already been handled
         * in {@link #getOutputStream(boolean, long)}.
         */
        if ((model.isChunked() ||
                FileDownloadProperties.getImpl().FILE_NON_PRE_ALLOCATION)
                && ex instanceof IOException &&
                new File(tempPath).exists()) {
            // chunked
            final long freeSpaceBytes = FileDownloadUtils.getFreeSpaceBytes(tempPath);
            if (freeSpaceBytes <= BUFFER_SIZE) {
                // free space is not enough.
                long downloadedSize = 0;
                final File file = new File(tempPath);
                if (!file.exists()) {
                    FileDownloadLog.e(this, ex, "Exception with: free " +
                            "space isn't enough, and the target file not exist.");
                } else {
                    downloadedSize = file.length();
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
                    ex = new FileDownloadOutOfSpaceException(freeSpaceBytes, BUFFER_SIZE,
                            downloadedSize, ex);
                } else {
                    ex = new FileDownloadOutOfSpaceException(freeSpaceBytes, BUFFER_SIZE,
                            downloadedSize);
                }

            }
        }

        return ex;
    }

    /**
     * 处理数据库满了的异常
     * @param sqLiteFullException
     */
    private void handleSQLiteFullException(final SQLiteFullException sqLiteFullException) {
        final int id = model.getId();
        if (FileDownloadLog.NEED_LOG) {
            FileDownloadLog.d(this, "the data of the task[%d] is dirty, because the SQLite " +
                            "full exception[%s], so remove it from the database directly.",
                    id, sqLiteFullException.toString());
        }

        model.setErrMsg(sqLiteFullException.toString());
        model.setStatus(FileDownloadStatus.error);

        database.remove(id);
        database.removeConnections(id);
    }

    /**
     * 重命名temp文件
     * @throws IOException
     */
    private void renameTempFile() throws IOException {
        final String tempPath = model.getTempFilePath();
        final String targetPath = model.getTargetFilePath();

        final File tempFile = new File(tempPath);
        try {
            final File targetFile = new File(targetPath);

            if (targetFile.exists()) {
                final long oldTargetFileLength = targetFile.length();
                if (!targetFile.delete()) {
                    throw new IOException(FileDownloadUtils.formatString(
                            "Can't delete the old file([%s], [%d]), " +
                                    "so can't replace it with the new downloaded one.",
                            targetPath, oldTargetFileLength
                    ));
                } else {
                    FileDownloadLog.w(this, "The target file([%s], [%d]) will be replaced with" +
                                    " the new downloaded file[%d]",
                            targetPath, oldTargetFileLength, tempFile.length());
                }
            }

            if (!tempFile.renameTo(targetFile)) {
                throw new IOException(FileDownloadUtils.formatString(
                        "Can't rename the  temp downloaded file(%s) to the target file(%s)",
                        tempPath, targetPath
                ));
            }
        } finally {
            if (tempFile.exists()) {
                if (!tempFile.delete()) {
                    FileDownloadLog.w(this,
                            "delete the temp file(%s) failed, on completed downloading.",
                            tempPath);
                }
            }
        }
    }


    @Override
    public boolean handleMessage(Message msg) {
        handlingMessage = true;
        final int status = msg.what;

        try {
            switch (status) {
                case FileDownloadStatus.progress:
                    handleProgress(SystemClock.elapsedRealtime(), true);
                    break;
                case FileDownloadStatus.retry:
                    handleRetry((Exception) msg.obj, msg.arg1);
                    break;
            }
        } finally {
            handlingMessage = false;
            if (parkThread != null) LockSupport.unpark(parkThread);
        }


        return true;
    }

    private volatile boolean needSetProcess;

    /**
     * 处理progress状态
     * @param now
     * @param isNeedCallbackToUser
     */
    private void handleProgress(final long now,
                                final boolean isNeedCallbackToUser) {
        if (model.getSoFar() == model.getTotal()) {
            database.updateProgress(model.getId(), model.getSoFar());
            return;
        }

        if (needSetProcess) {
            needSetProcess = false;
            model.setStatus(FileDownloadStatus.progress);
        }

        if (isNeedCallbackToUser) {
            lastCallbackTimestamp = now;
            onStatusChanged(FileDownloadStatus.progress);
            callbackIncreaseBuffer.set(0);
        }
    }

    /**
     * 处理完成状态
     * @throws IOException
     */
    private void handleCompleted() throws IOException {
        renameTempFile();

        model.setStatus(FileDownloadStatus.completed);

        database.updateCompleted(model.getId(), model.getTotal());
        database.removeConnections(model.getId());

        onStatusChanged(FileDownloadStatus.completed);

        if (FileDownloadProperties.getImpl().BROADCAST_COMPLETED) {
            FileDownloadBroadcastHandler.sendCompletedBroadcast(model);
        }
    }

    /**
     * 完成前被干扰
     * @return
     */
    private boolean interceptBeforeCompleted() {
        if (model.isChunked()) {
            model.setTotal(model.getSoFar());
        } else if (model.getSoFar() != model.getTotal()) {
            onErrorDirectly(new FileDownloadGiveUpRetryException(
                    FileDownloadUtils.formatString("sofar[%d] not equal total[%d]",
                            model.getSoFar(), model.getTotal())));
            return true;
        }

        return false;
    }

    /**
     * 处理retry状态
     * @param exception
     * @param remainRetryTimes
     */
    private void handleRetry(final Exception exception, final int remainRetryTimes) {
        Exception processEx = exFiltrate(exception);
        processParams.setException(processEx);
        processParams.setRetryingTimes(maxRetryTimes - remainRetryTimes);

        model.setStatus(FileDownloadStatus.retry);
        model.setErrMsg(processEx.toString());

        database.updateRetry(model.getId(), processEx);
        onStatusChanged(FileDownloadStatus.retry);
    }

    private void handlePaused() {
        model.setStatus(FileDownloadStatus.paused);

        database.updatePause(model.getId(), model.getSoFar());
        onStatusChanged(FileDownloadStatus.paused);
    }

    /**
     * 处理错误状态
     * @param exception
     */
    private void handleError(Exception exception) {
        Exception errProcessEx = exFiltrate(exception);

        if (errProcessEx instanceof SQLiteFullException) {
            // If the error is sqLite full exception already, no need to  update it to the database
            // again.
            handleSQLiteFullException((SQLiteFullException) errProcessEx);
        } else {
            // Normal case.
            try {

                model.setStatus(FileDownloadStatus.error);
                model.setErrMsg(exception.toString());

                database.updateError(model.getId(), errProcessEx, model.getSoFar());
            } catch (SQLiteFullException fullException) {
                errProcessEx = fullException;
                handleSQLiteFullException((SQLiteFullException) errProcessEx);
            }
        }

        processParams.setException(errProcessEx);
        onStatusChanged(FileDownloadStatus.error);
    }

    private boolean isFirstCallback = true;

    /**
     * 是否需要回调给用户
     * @param now
     * @return
     */
    private boolean isNeedCallbackToUser(final long now) {
        if (isFirstCallback) {
            isFirstCallback = false;
            return true;
        }

        final long callbackTimeDelta = now - lastCallbackTimestamp;


        return (callbackMinIntervalBytes != NO_ANY_PROGRESS_CALLBACK && callbackIncreaseBuffer.get() >= callbackMinIntervalBytes)
                && (callbackTimeDelta >= callbackProgressMinInterval);
    }

    /**
     * 处理状态改变
     * @param status
     */
    private void onStatusChanged(final byte status) {
        // In current situation, it maybe invoke this method simultaneously（同时地） between #onPause() and
        // others.
        if (status == FileDownloadStatus.paused) {
            if (FileDownloadLog.NEED_LOG) {
                /**
                 * Already paused or the current status is paused.
                 *
                 * We don't need to call-back to Task in here, because the pause status has
                 * already handled by {@link BaseDownloadTask#pause()} manually.
                 *
                 * In some case, it will arrive here by High concurrent cause.  For performance
                 * more make sense.
                 *
                 * High concurrent cause.
                 */
                FileDownloadLog.d(this, "High concurrent（并发的；一致的；同时发生的） cause, Already paused and we don't " +
                        "need to call-back to Task in here, %d", model.getId());
            }
            return;
        }

        MessageSnapshotFlow.getImpl().inflow(
                MessageSnapshotTaker.take(status, model, processParams));
    }

    public static class ProcessParams {
        private boolean isResuming;
        private Exception exception;
        private int retryingTimes;

        void setResuming(boolean isResuming) {
            this.isResuming = isResuming;
        }

        public boolean isResuming() {
            return this.isResuming;
        }

        void setException(Exception exception) {
            this.exception = exception;
        }

        void setRetryingTimes(int retryingTimes) {
            this.retryingTimes = retryingTimes;
        }

        public Exception getException() {
            return exception;
        }

        public int getRetryingTimes() {
            return retryingTimes;
        }
    }
}
