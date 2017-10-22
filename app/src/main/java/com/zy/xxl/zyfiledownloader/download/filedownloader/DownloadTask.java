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

import android.text.TextUtils;
import android.util.SparseArray;

import com.zy.xxl.zyfiledownloader.download.filedownloader.model.FileDownloadHeader;
import com.zy.xxl.zyfiledownloader.download.filedownloader.model.FileDownloadModel;
import com.zy.xxl.zyfiledownloader.download.filedownloader.model.FileDownloadStatus;
import com.zy.xxl.zyfiledownloader.download.filedownloader.util.FileDownloadLog;
import com.zy.xxl.zyfiledownloader.download.filedownloader.util.FileDownloadUtils;

import java.io.File;
import java.util.ArrayList;

/**
 * The download task.
 */

public class DownloadTask implements BaseDownloadTask, BaseDownloadTask.IRunningTask,
        DownloadTaskHunter.ICaptureTask {

    private final ITaskHunter mHunter;
    private final ITaskHunter.IMessageHandler mMessageHandler;
    private int mId;

    private ArrayList<FinishListener> mFinishListenerList;

    private final String mUrl;
    private String mPath;
    private String mFilename;
    private boolean mPathAsDirectory;

    private FileDownloadHeader mHeader;

    private FileDownloadListener mListener;

    private SparseArray<Object> mKeyedTags;
    private Object mTag;

    private int mAutoRetryTimes = 0;

    /**
     * If {@code true} will callback directly on the download thread(do not on post the message to
     * the ui thread
     * by {@link android.os.Handler#post(Runnable)}
     */
    private boolean mSyncCallback = false;

    private boolean mIsWifiRequired = false;

    public final static int DEFAULT_CALLBACK_PROGRESS_MIN_INTERVAL_MILLIS = 10;
    private int mCallbackProgressTimes = FileDownloadModel.DEFAULT_CALLBACK_PROGRESS_TIMES;
    private int mCallbackProgressMinIntervalMillis = DEFAULT_CALLBACK_PROGRESS_MIN_INTERVAL_MILLIS;

    private boolean mIsForceReDownload = false;

    volatile int mAttachKey = 0;
    private boolean mIsInQueueTask = false;

    DownloadTask(final String url) {
        this.mUrl = url;
        mPauseLock = new Object();
        final DownloadTaskHunter hunter = new DownloadTaskHunter(this, mPauseLock);

        mHunter = hunter;
        mMessageHandler = hunter;
    }

    //设置下载中刷新下载速度的最小间隔
    @Override
    public BaseDownloadTask setMinIntervalUpdateSpeed(int minIntervalUpdateSpeedMs) {
        mHunter.setMinIntervalUpdateSpeed(minIntervalUpdateSpeedMs);
        return this;
    }

    //下载文件的存储绝对路径
    @Override
    public BaseDownloadTask setPath(final String path) {
        return setPath(path, false);
    }

    //如果pathAsDirectory是true,path就是存储下载文件的文件目录(而不是路径)，
    // 此时默认情况下文件名filename将会默认从response#header中的contentDisposition中获得
    @Override
    public BaseDownloadTask setPath(final String path, final boolean pathAsDirectory) {
        this.mPath = path;
        if (FileDownloadLog.NEED_LOG) {
            FileDownloadLog.d(this, "setPath %s", path);
        }

        this.mPathAsDirectory = pathAsDirectory;
        if (pathAsDirectory) {
            /**
             * will be found before the callback of
             * {@link FileDownloadListener#connected(BaseDownloadTask, String, boolean, int, int)}
             */
            this.mFilename = null;
        } else {
            this.mFilename = new File(path).getName();
        }

        return this;
    }

    //设置监听，可以以相同监听组成队列
    @Override
    public BaseDownloadTask setListener(final FileDownloadListener listener) {
        this.mListener = listener;

        if (FileDownloadLog.NEED_LOG) {
            FileDownloadLog.d(this, "setListener %s", listener);
        }
        return this;
    }

    //设置整个下载过程中FileDownloadListener#progress最大回调次数
    @Override
    public BaseDownloadTask setCallbackProgressTimes(int callbackProgressCount) {
        this.mCallbackProgressTimes = callbackProgressCount;
        return this;
    }

    //设置每个FileDownloadListener#progress之间回调间隔(ms)
    @Override
    public BaseDownloadTask setCallbackProgressMinInterval(int minIntervalMillis) {
        this.mCallbackProgressMinIntervalMillis = minIntervalMillis;
        return this;
    }

    //忽略所有的FileDownloadListener#progress的回调
    @Override
    public BaseDownloadTask setCallbackProgressIgnored() {
        return setCallbackProgressTimes(-1);
    }

    //内部不会使用，在回调的时候用户自己使用
    @Override
    public BaseDownloadTask setTag(final Object tag) {
        this.mTag = tag;
        if (FileDownloadLog.NEED_LOG) {
            FileDownloadLog.d(this, "setTag %s", tag);
        }
        return this;
    }

    //用于存储任意的变量方便回调中使用，以key作为索引
    @Override
    public BaseDownloadTask setTag(final int key, final Object tag) {
        if (mKeyedTags == null) {
            mKeyedTags = new SparseArray<>(2);
        }
        mKeyedTags.put(key, tag);
        return this;
    }

    //强制重新下载，将会忽略检测文件是否健在
    @Override
    public BaseDownloadTask setForceReDownload(final boolean isForceReDownload) {
        this.mIsForceReDownload = isForceReDownload;
        return this;
    }

    @Override
    public BaseDownloadTask setFinishListener(final FinishListener finishListener) {
        addFinishListener(finishListener);
        return this;
    }

    //结束监听，仅包含结束(over(void))的监听
    @Override
    public BaseDownloadTask addFinishListener(final FinishListener finishListener) {
        if (mFinishListenerList == null) {
            mFinishListenerList = new ArrayList<>();
        }

        if (!mFinishListenerList.contains(finishListener)) {
            mFinishListenerList.add(finishListener);
        }
        return this;
    }


    @Override
    public boolean removeFinishListener(final FinishListener finishListener) {
        return mFinishListenerList != null && mFinishListenerList.remove(finishListener);
    }

    //当请求或下载或写文件过程中存在错误时，自动重试次数，默认为0次
    @Override
    public BaseDownloadTask setAutoRetryTimes(int autoRetryTimes) {
        this.mAutoRetryTimes = autoRetryTimes;
        return this;
    }

    //添加自定义的请求头参数，需要注意的是内部为了断点续传，
    // 在判断断点续传有效时会自动添加上(If-Match与Range参数)，
    // 请勿重复添加导致400或其他错误
    @Override
    public BaseDownloadTask addHeader(final String name, final String value) {
        checkAndCreateHeader();
        mHeader.add(name, value);
        return this;
    }

    @Override
    public BaseDownloadTask addHeader(final String line) {
        checkAndCreateHeader();
        mHeader.add(line);
        return this;
    }

    //删除由自定义添加上去请求参数为{name}的所有键对
    @Override
    public BaseDownloadTask removeAllHeaders(final String name) {
        if (mHeader == null) {
            synchronized (headerCreateLock) {
                // maybe invoking checkAndCreateHear and will to be available.
                if (mHeader == null) {
                    return this;
                }
            }
        }


        mHeader.removeAll(name);
        return this;
    }

    //如果设为true, 所有FileDownloadListener中的回调都会直接在下载线程中回调而不抛到ui线程, 默认为false
    @Override
    public BaseDownloadTask setSyncCallback(final boolean syncCallback) {
        this.mSyncCallback = syncCallback;
        return this;
    }

    //设置任务是否只允许在Wifi网络环境下进行下载。 默认值 false
    @Override
    public BaseDownloadTask setWifiRequired(boolean isWifiRequired) {
        this.mIsWifiRequired = isWifiRequired;
        return this;
    }


    @Override
    public int ready() {
        return asInQueueTask().enqueue();
    }

    //申明该任务将会是队列任务中的一个任务，并且转化为InQueueTask，
    // 之后可以调用InQueueTask#enqueue将该任务入队以便于接下来启动队列任务时，可以将该任务收编到队列中
    @Override
    public InQueueTask asInQueueTask() {
        return new InQueueTaskImpl(this);
    }

    @Override
    public boolean reuse() {
        if (isRunning()) {
            FileDownloadLog.w(this, "This task[%d] is running, if you want start the same task," +
                    " please create a new one by FileDownloader#create", getId());
            return false;
        }

        this.mAttachKey = 0;
        mIsInQueueTask = false;
        mIsMarkedAdded2List = false;
        mHunter.reset();

        return true;
    }

    //	判断当前的Task对象是否在引擎中启动过
    @Override
    public boolean isUsing() {
        return mHunter.getStatus() != FileDownloadStatus.INVALID_STATUS;
    }

    //当前任务是否正在运行
    @Override
    public boolean isRunning() {
        //noinspection SimplifiableIfStatement
        if (FileDownloader.getImpl().getLostConnectedHandler().isInWaitingList(this)) {
            return true;
        }

        return FileDownloadStatus.isIng(getStatus());
    }

    @Override
    public boolean isAttached() {
        return mAttachKey != 0;
    }

    @Override
    public int start() {
        if (mIsInQueueTask) {
            throw new IllegalStateException("If you start the task manually, it means this task " +
                    "doesn't belong to a queue, so you must not invoke BaseDownloadTask#ready() or" +
                    " InQueueTask#enqueue() before you start() this method. For detail: If this" +
                    " task doesn't belong to a queue, what is just an isolated task, you just need" +
                    " to invoke BaseDownloadTask#start() to start this task, that's all. In other" +
                    " words, If this task doesn't belong to a queue, you must not invoke" +
                    " BaseDownloadTask#ready() method or InQueueTask#enqueue() method before" +
                    " invoke BaseDownloadTask#start(), If you do that and if there is the same" +
                    " listener object to start a queue in another thread, this task may be " +
                    "assembled by the queue, in that case, when you invoke BaseDownloadTask#start()" +
                    " manually to start this task or this task is started by the queue, there is" +
                    " an exception buried in there, because this task object is started two times" +
                    " without declare BaseDownloadTask#reuse() : 1. you invoke " +
                    "BaseDownloadTask#start() manually; 2. the queue start this task automatically.");
        }

        return startTaskUnchecked();
    }

    //加入队列
    private int startTaskUnchecked() {
        if (isUsing()) {
            if (isRunning()) {
                throw new IllegalStateException(
                        FileDownloadUtils.formatString("This task is running %d, if you" +
                                " want to start the same task, please create a new one by" +
                                " FileDownloader.create", getId()));
            } else {
                throw new IllegalStateException("This task is dirty to restart, If you want to " +
                        "reuse this task, please invoke #reuse method manually and retry to " +
                        "restart again." + mHunter.toString());
            }
        }

        if (!isAttached()) {
            setAttachKeyDefault();
        }

        mHunter.intoLaunchPool();

        return getId();
    }

    // -------------- Another Operations ---------------------

    private final Object mPauseLock;

    //暂停下载任务(也可以理解为停止下载，但是在start的时候默认会断点续传)
    @Override
    public boolean pause() {
        synchronized (mPauseLock) {
            return mHunter.pause();
        }
    }

    @Override
    public boolean cancel() {
        return pause();
    }

    // ------------------- Get -----------------------

    @Override
    public int getId() {
        if (mId != 0) {
            return mId;
        }

        if (!TextUtils.isEmpty(mPath) && !TextUtils.isEmpty(mUrl)) {
            return mId = FileDownloadUtils.generateId(mUrl, mPath, mPathAsDirectory);
        }

        return 0;
    }

    /**
     * @deprecated Used {@link #getId()} instead.
     */
    @Override
    public int getDownloadId() {
        return getId();
    }

    @Override
    public String getUrl() {
        return mUrl;
    }

    @Override
    public int getCallbackProgressTimes() {
        return mCallbackProgressTimes;
    }

    @Override
    public int getCallbackProgressMinInterval() {
        return mCallbackProgressMinIntervalMillis;
    }

    @Override
    public String getPath() {
        return mPath;
    }

    @Override
    public boolean isPathAsDirectory() {
        return mPathAsDirectory;
    }

    @Override
    public String getFilename() {
        return mFilename;
    }

    //	获取目标文件的存储路径
    @Override
    public String getTargetFilePath() {
        return FileDownloadUtils.getTargetFilePath(getPath(), isPathAsDirectory(), getFilename());
    }

    @Override
    public FileDownloadListener getListener() {
        return mListener;
    }

    //获取已经下载的字节数
    @Override
    public int getSoFarBytes() {
        return getSmallFileSoFarBytes();
    }

    @Override
    public int getSmallFileSoFarBytes() {
        if (mHunter.getSofarBytes() > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) mHunter.getSofarBytes();
    }

    @Override
    public long getLargeFileSoFarBytes() {
        return mHunter.getSofarBytes();
    }

    @Override
    public int getTotalBytes() {
        return getSmallFileTotalBytes();
    }

    @Override
    public int getSmallFileTotalBytes() {
        if (mHunter.getTotalBytes() > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }

        return (int) mHunter.getTotalBytes();
    }

    @Override
    public long getLargeFileTotalBytes() {
        return mHunter.getTotalBytes();
    }

    //获取任务的下载速度, 下载过程中为实时速度，下载结束状态为平均速度
    @Override
    public int getSpeed() {
        return mHunter.getSpeed();
    }

    //获取当前的状态
    @Override
    public byte getStatus() {
        return mHunter.getStatus();
    }

    //	是否强制重新下载
    @Override
    public boolean isForceReDownload() {
        return this.mIsForceReDownload;
    }

    //	获取下载过程抛出的Throwable
    @Override
    public Throwable getEx() {
        return getErrorCause();
    }

    //
    @Override
    public Throwable getErrorCause() {
        return mHunter.getErrorCause();
    }

    //	判断是否是直接使用了旧文件(检测是有效文件)，没有启动下载
    @Override
    public boolean isReusedOldFile() {
        return mHunter.isReusedOldFile();
    }

    @Override
    public Object getTag() {
        return this.mTag;
    }

    @Override
    public Object getTag(int key) {
        return mKeyedTags == null ? null : mKeyedTags.get(key);
    }


    /**
     * 是否成功断点续传
     * @deprecated Use {@link #isResuming()} instead.
     */
    @Override
    public boolean isContinue() {
        return isResuming();
    }

    //isResuming
    @Override
    public boolean isResuming() {
        return mHunter.isResuming();
    }

    @Override
    public String getEtag() {
        return mHunter.getEtag();
    }

    @Override
    public int getAutoRetryTimes() {
        return this.mAutoRetryTimes;
    }

    @Override
    public int getRetryingTimes() {
        return mHunter.getRetryingTimes();
    }

    @Override
    public boolean isSyncCallback() {
        return mSyncCallback;
    }

    @Override
    public boolean isLargeFile() {
        return mHunter.isLargeFile();
    }

    @Override
    public boolean isWifiRequired() {
        return mIsWifiRequired;
    }

    private final Object headerCreateLock = new Object();

    private void checkAndCreateHeader() {
        if (mHeader == null) {
            synchronized (headerCreateLock) {
                if (mHeader == null) {
                    mHeader = new FileDownloadHeader();
                }
            }
        }
    }

    @Override
    public FileDownloadHeader getHeader() {
        return this.mHeader;
    }


    // why this? thread not safe: update,InQueueTask#enqueue, start, pause, start which influence of this
    // in the queue.
    // whether it has been added, whether or not it is removed.
    private volatile boolean mIsMarkedAdded2List = false;

    //加入队列
    @Override
    public void markAdded2List() {
        mIsMarkedAdded2List = true;
    }

    @Override
    public void free() {
        mHunter.free();
        if (FileDownloadList.getImpl().isNotContains(this)) {
            mIsMarkedAdded2List = false;
        }
    }

    @Override
    public void startTaskByQueue() {
        startTaskUnchecked();
    }

    @Override
    public void startTaskByRescue() {
        // In this case, we don't need to check, because, we just to rescue this task, it means this
        // task has already called start, but the filedownloader service didn't connected, and now,
        // the service is connected, so we just rescue this task.
        startTaskUnchecked();
    }

    @Override
    public Object getPauseLock() {
        return mPauseLock;
    }

    @Override
    public boolean isContainFinishListener() {
        return mFinishListenerList != null && mFinishListenerList.size() > 0;
    }


    @Override
    public boolean isMarkedAdded2List() {
        return this.mIsMarkedAdded2List;
    }

    @Override
    public IRunningTask getRunningTask() {
        return this;
    }

    @Override
    public void setFileName(String fileName) {
        mFilename = fileName;
    }

    @Override
    public ArrayList<FinishListener> getFinishListenerList() {
        return mFinishListenerList;
    }

    @Override
    public BaseDownloadTask getOrigin() {
        return this;
    }

    @Override
    public ITaskHunter.IMessageHandler getMessageHandler() {
        return mMessageHandler;
    }

    @Override
    public boolean is(int id) {
        return getId() == id;
    }

    @Override
    public boolean is(FileDownloadListener listener) {
        return getListener() == listener;
    }

    //是否结束
    @Override
    public boolean isOver() {
        return FileDownloadStatus.isOver(getStatus());
    }

    @Override
    public int getAttachKey() {
        return mAttachKey;
    }

    @Override
    public void setAttachKeyByQueue(int key) {
        this.mAttachKey = key;
    }

    //设置attackKey为默认key
    @Override
    public void setAttachKeyDefault() {
        final int attachKey;
        if (getListener() != null) {
            attachKey = getListener().hashCode();
        } else {
            attachKey = hashCode();
        }
        this.mAttachKey = attachKey;
    }

    @Override
    public String toString() {
        return FileDownloadUtils.formatString("%d@%s", getId(), super.toString());
    }

    //队列任务
    private final static class InQueueTaskImpl implements InQueueTask {
        private final DownloadTask mTask;

        private InQueueTaskImpl(DownloadTask task) {
            this.mTask = task;
            this.mTask.mIsInQueueTask = true;
        }

        @Override
        public int enqueue() {
            final int id = mTask.getId();

            if (FileDownloadLog.NEED_LOG) {
                FileDownloadLog.d(this, "add the task[%d] to the queue", id);
            }

            FileDownloadList.getImpl().addUnchecked(mTask);
            return id;
        }
    }
}
