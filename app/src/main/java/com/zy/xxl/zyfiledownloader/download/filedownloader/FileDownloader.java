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

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;

import com.zy.xxl.zyfiledownloader.download.filedownloader.download.CustomComponentHolder;
import com.zy.xxl.zyfiledownloader.download.filedownloader.event.DownloadServiceConnectChangedEvent;
import com.zy.xxl.zyfiledownloader.download.filedownloader.model.FileDownloadStatus;
import com.zy.xxl.zyfiledownloader.download.filedownloader.model.FileDownloadTaskAtom;
import com.zy.xxl.zyfiledownloader.download.filedownloader.services.DownloadMgrInitialParams;
import com.zy.xxl.zyfiledownloader.download.filedownloader.util.FileDownloadHelper;
import com.zy.xxl.zyfiledownloader.download.filedownloader.util.FileDownloadLog;
import com.zy.xxl.zyfiledownloader.download.filedownloader.util.FileDownloadUtils;

import java.io.File;
import java.util.List;

/**
 * 第一个类
 * The basic entrance for FileDownloader.
 *
 * @see com.zy.xxl.zyfiledownloader.download.filedownloader.services.FileDownloadService The service for FileDownloader.
 * @see com.zy.xxl.zyfiledownloader.download.filedownloader.util.FileDownloadProperties
 */
@SuppressWarnings("WeakerAccess")
public class FileDownloader {

    /**
     * You can invoke this method anytime before you using the FileDownloader.
     * <p>
     * If you want to register your own customize components please using {@link #setupOnApplicationOnCreate(Application)}
     * on the {@link Application#onCreate()} instead.
     *
     * @param context the context of Application or Activity etc..
     */
    public static void setup(Context context) {
        FileDownloadHelper.holdContext(context.getApplicationContext());
    }

    /**
     * Using this method to setup the FileDownloader only you want to register your own customize
     * components for Filedownloader, otherwise just using {@link #setup(Context)} instead.
     * <p/>
     * Please invoke this method on the {@link Application#onCreate()} because of the customize
     * components must be assigned before FileDownloader is running.
     *
     * <p/>
     * Such as:
     * <p/>
     * class MyApplication extends Application {
     *     ...
     *     public void onCreate() {
     *          ...
     *          FileDownloader.setupOnApplicationOnCreate(this)
     *              .idGenerator(new MyIdGenerator())
     *              .database(new MyDatabase())
     *              ...
     *              .commit();
     *          ...
     *     }
     *     ...
     * }
     * @param application the application.
     * @return the customize components maker.
     * 自定义组件
     */
    public static DownloadMgrInitialParams.InitCustomMaker setupOnApplicationOnCreate(Application application) {
        final Context context = application.getApplicationContext();
        FileDownloadHelper.holdContext(context);

        DownloadMgrInitialParams.InitCustomMaker customMaker = new DownloadMgrInitialParams.InitCustomMaker();
        CustomComponentHolder.getImpl().setInitCustomMaker(customMaker);

        return customMaker;
    }

    /**
     * @deprecated please use {@link #setup(Context)} instead.
     */
    public static void init(final Context context) {
        if (context == null)
            throw new IllegalArgumentException("the provided context must not be null!");

        setup(context);
    }


    /**
     * @deprecated please using {@link #setupOnApplicationOnCreate(Application)} instead.
     * 已经不推荐使用
     */
    public static void init(final Context context,
                            final DownloadMgrInitialParams.InitCustomMaker maker) {
        if (FileDownloadLog.NEED_LOG) {
            FileDownloadLog.d(FileDownloader.class, "init Downloader with params: %s %s",
                    context, maker);
        }

        if (context == null)
            throw new IllegalArgumentException("the provided context must not be null!");

        FileDownloadHelper.holdContext(context.getApplicationContext());

        CustomComponentHolder.getImpl().setInitCustomMaker(maker);
    }

    //生成实例 为什么不用单例模式呢？
    private final static class HolderClass {
        private final static FileDownloader INSTANCE = new FileDownloader();
    }

    public static FileDownloader getImpl() {
        return HolderClass.INSTANCE;
    }

    /**
     * 为了避免掉帧，这里是设置了最多每interval毫秒抛一个消息到ui线程(使用Handler)，
     * 防止由于回调的过于频繁导致ui线程被ddos导致掉帧。
     * 默认值: 10ms. 如果设置小于0，将会失效，也就是说每个回调都直接抛一个消息到ui线程
     * For avoiding missing screen frames.
     * <p/>
     * This mechanism is used for avoid methods in {@link FileDownloadListener} is invoked too frequent
     * in result the system missing screen frames in the main thread.
     * <p>
     * We wrap the message package which size is {@link FileDownloadMessageStation#SUB_PACKAGE_SIZE},
     * and post the package to the main thread with the interval:
     * {@link FileDownloadMessageStation#INTERVAL} milliseconds.
     * <p/>
     * The default interval is 10ms, if {@code intervalMillisecond} equal to or less than 0, each
     * callback in {@link FileDownloadListener} will be posted to the main thread immediately.
     *
     * @param intervalMillisecond The time interval between posting two message packages.
     * @see #enableAvoidDropFrame()
     * @see #disableAvoidDropFrame()
     * @see #setGlobalHandleSubPackageSize(int)
     */
    public static void setGlobalPost2UIInterval(final int intervalMillisecond) {
        FileDownloadMessageStation.INTERVAL = intervalMillisecond;
    }

    /**
     * 为了避免掉帧, 如果上面的方法设置的间隔是一个小于0的数，这个packageSize将不会生效。packageSize这个值是为了避免在ui线程中一次处理过多回调，
     * 结合上面的间隔，就是每个interval毫秒间隔抛一个消息到ui线程，而每个消息在ui线程中处理packageSize个回调。默认值: 5
     * For avoiding missing screen frames.
     * <p/>
     * This mechanism is used for avoid methods in {@link FileDownloadListener} is invoked too frequent
     * in result the system missing screen frames in the main thread.
     * <p>
     * We wrap the message package which size is {@link FileDownloadMessageStation#SUB_PACKAGE_SIZE},
     * and post the package to the main thread with the interval:
     * {@link FileDownloadMessageStation#INTERVAL} milliseconds.
     * <p>
     * The default count of message for a message package is 5.
     *
     * @param packageSize The count of message for a message package.
     * @see #setGlobalPost2UIInterval(int)
     */
    public static void setGlobalHandleSubPackageSize(final int packageSize) {
        if (packageSize <= 0) {
            throw new IllegalArgumentException("sub package size must more than 0");
        }
        FileDownloadMessageStation.SUB_PACKAGE_SIZE = packageSize;
    }

    /**
     * 开启 避免掉帧处理。就是将抛消息到ui线程的间隔设为默认值10ms,
     * 很明显会影响的是回调不会立马通知到监听器(FileDownloadListener)中，默认值是: 最多10ms处理5个回调到监听器中
     * Avoid missing screen frames, this leads to all callbacks in {@link FileDownloadListener} do
     * not be invoked at once when it has already achieved to ensure callbacks don't be too frequent.
     *
     * @see #isEnabledAvoidDropFrame()
     * @see #setGlobalPost2UIInterval(int)
     */
    public static void enableAvoidDropFrame() {
        setGlobalPost2UIInterval(FileDownloadMessageStation.DEFAULT_INTERVAL);
    }

    /**
     * 关闭 避免掉帧处理。就是将抛消息到ui线程的间隔设置-1(无效值)，这个就是让每个回调都会抛一个消息ui线程中，可能引起掉帧
     * Disable avoiding missing screen frames, let all callbacks in {@link FileDownloadListener}
     * can be invoked at once when it achieve.
     *
     * @see #isEnabledAvoidDropFrame()
     * @see #setGlobalPost2UIInterval(int)
     */
    public static void disableAvoidDropFrame() {
        setGlobalPost2UIInterval(-1);
    }

    /**
     * 是否开启了 避免掉帧处理。默认是开启的
     * @return {@code true} if enabled the function of avoiding missing screen frames.
     * @see #enableAvoidDropFrame()
     * @see #disableAvoidDropFrame()
     * @see #setGlobalPost2UIInterval(int)
     */
    public static boolean isEnabledAvoidDropFrame() {
        return FileDownloadMessageStation.isIntervalValid();
    }

    /**
     * Create a download task.
     * 新建一个下载任务
     */
    public BaseDownloadTask create(final String url) {
        return new DownloadTask(url);
    }

    /**
     * 启动是相同监听器的任务，串行/并行启动
     * Start the download queue by the same listener.
     *
     * @param listener Used to assemble tasks which is bound by the same {@code listener}
     * @param isSerial Whether start tasks one by one rather than parallel.
     * @return {@code true} if start tasks successfully.
     */
    public boolean start(final FileDownloadListener listener, final boolean isSerial) {

        if (listener == null) {
            FileDownloadLog.w(this, "Tasks with the listener can't start, because the listener " +
                    "provided is null: [null, %B]", isSerial);
            return false;
        }


        return isSerial ?
                getQueuesHandler().startQueueSerial(listener) :
                getQueuesHandler().startQueueParallel(listener);
    }


    /**
     * 暂停启动相同监听器的任务
     * Pause the download queue by the same {@code listener}.
     *
     * @param listener the listener.
     * @see #pause(int)
     */
    public void pause(final FileDownloadListener listener) {
        FileDownloadTaskLauncher.getImpl().expire(listener);
        final List<BaseDownloadTask.IRunningTask> taskList =
                FileDownloadList.getImpl().copy(listener);
        for (BaseDownloadTask.IRunningTask task : taskList) {
            task.getOrigin().pause();
        }
    }

    private Runnable pauseAllRunnable;

    /**
     * Pause all tasks running in FileDownloader.
     * 暂停所有任务
     */
    // TODO: 2017/10/22 开始所有任务 可以仿照这个类去做 
    public void pauseAll() {
        FileDownloadTaskLauncher.getImpl().expireAll();
        final BaseDownloadTask.IRunningTask[] downloadList = FileDownloadList.getImpl().copy();
        for (BaseDownloadTask.IRunningTask task : downloadList) {
            task.getOrigin().pause();
        }
        // double check, for case: File Download progress alive but ui progress has died and relived,
        // so FileDownloadList not always contain all running task exactly.
        if (FileDownloadServiceProxy.getImpl().isConnected()) {
            FileDownloadServiceProxy.getImpl().pauseAllTasks();
        } else {
            if (pauseAllRunnable == null) {
                pauseAllRunnable = new Runnable() {
                    @Override
                    public void run() {
                        FileDownloadServiceProxy.getImpl().pauseAllTasks();
                    }
                };
            }
            FileDownloadServiceProxy.getImpl().bindStartByContext(FileDownloadHelper.getAppContext(), pauseAllRunnable);
        }

    }

    /**
     * Pause downloading tasks with the {@code id}.
     * 	暂停downloadId的任务
     *
     * @param id the {@code id} .
     * @return The size of tasks has been paused.
     * @see #pause(FileDownloadListener)
     */
    public int pause(final int id) {
        List<BaseDownloadTask.IRunningTask> taskList = FileDownloadList.getImpl().getDownloadingList(id);
        if (null == taskList || taskList.isEmpty()) {
            FileDownloadLog.w(this, "request pause but not exist %d", id);
            return 0;
        }

        for (BaseDownloadTask.IRunningTask task : taskList) {
            task.getOrigin().pause();
        }

        return taskList.size();
    }

    /**
     * 强制清理ID为downloadId的任务在fileDownloader中的数据
     *
     * Clear the data with the provided {@code id}.
     * Normally used to deleting the data in filedownloader database, when it is paused or in
     * downloading status. If you want to re-download it clearly.
     * <p/>
     * <strong>Note:</strong> YOU NO NEED to clear the data when it is already completed downloading,
     * because the data would be deleted when it completed downloading automatically by FileDownloader.
     * <p>
     * If there are tasks with the {@code id} in downloading, will be paused first;
     * If delete the data with the {@code id} in the filedownloader database successfully, will try
     * to delete its intermediate downloading file and downloaded file.
     *
     * @param id             the download {@code id}.
     * @param targetFilePath the target path.
     * @return {@code true} if the data with the {@code id} in filedownloader database was deleted,
     * and tasks with the {@code id} was paused; {@code false} otherwise.
     */
    public boolean clear(final int id, final String targetFilePath) {
        pause(id);

        if (FileDownloadServiceProxy.getImpl().clearTaskData(id)) {
            // delete the task data in the filedownloader database successfully or no data with the
            // id in filedownloader database.
            final File intermediateFile = new File(FileDownloadUtils.getTempPath(targetFilePath));
            if (intermediateFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                intermediateFile.delete();
            }

            final File targetFile = new File(targetFilePath);
            if (targetFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                targetFile.delete();
            }

            return true;
        }

        return false;
    }

    /**
     * 清空filedownloader数据库中的所有数据
     * Clear all data in the filedownloader database.
     * <p>
     * <strong>Note:</strong> Normally, YOU NO NEED to clearAllTaskData manually, because the
     * FileDownloader will maintain those data to ensure only if the data available for resuming
     * can be kept automatically.
     *
     * @see #clear(int, String)
     */
    public void clearAllTaskData() {
        pauseAll();

        FileDownloadServiceProxy.getImpl().clearAllTaskData();
    }

    /**
     * 获得下载Id为downloadId的soFarBytes
     * Get downloaded bytes so far by the downloadId.
     */
    public long getSoFar(final int downloadId) {
        BaseDownloadTask.IRunningTask task = FileDownloadList.getImpl().get(downloadId);
        if (task == null) {
            return FileDownloadServiceProxy.getImpl().getSofar(downloadId);
        }

        return task.getOrigin().getLargeFileSoFarBytes();
    }

    /**
     * 获得下载Id为downloadId的totalBytes
     * Get the total bytes of the target file for the task with the {code id}.
     */
    public long getTotal(final int id) {
        BaseDownloadTask.IRunningTask task = FileDownloadList.getImpl().get(id);
        if (task == null) {
            return FileDownloadServiceProxy.getImpl().getTotal(id);
        }

        return task.getOrigin().getLargeFileTotalBytes();
    }

    /**
     * 获取不包含已完成状态的下载状态(如果任务已经下载完成，将收到INVALID)
     * @param id The downloadId.
     * @return The downloading status without cover the completed status (if completed you will receive
     * {@link FileDownloadStatus#INVALID_STATUS} ).
     * @see #getStatus(String, String)
     * @see #getStatus(int, String)
     */
    public byte getStatusIgnoreCompleted(final int id) {
        return getStatus(id, null);
    }

    /**
     * 获取下载状态
     * @param url  The downloading URL.
     * @param path The downloading file's path.
     * @return The downloading status.
     * @see #getStatus(int, String)
     * @see #getStatusIgnoreCompleted(int)
     */
    public byte getStatus(final String url, final String path) {
        return getStatus(FileDownloadUtils.generateId(url, path), path);
    }

    /**
     * 获取下载状态
     * @param id   The downloadId.
     * @param path The target file path.
     * @return the downloading status.
     * @see FileDownloadStatus
     * @see #getStatus(String, String)
     * @see #getStatusIgnoreCompleted(int)
     */
    public byte getStatus(final int id, final String path) {
        byte status;
        BaseDownloadTask.IRunningTask task = FileDownloadList.getImpl().get(id);
        if (task == null) {
            status = FileDownloadServiceProxy.getImpl().getStatus(id);
        } else {
            status = task.getOrigin().getStatus();
        }

        if (path != null && status == FileDownloadStatus.INVALID_STATUS) {
            if (FileDownloadUtils.isFilenameConverted(FileDownloadHelper.getAppContext()) &&
                    new File(path).exists()) {
                status = FileDownloadStatus.completed;
            }
        }

        return status;
    }

    /**
     * 替换监听借口
     * Find the running task by {@code url} and default path, and replace its listener with
     * the new one {@code listener}.
     *
     * @return The target task's DownloadId, if not exist target task, and replace failed, will be 0.
     * @see #replaceListener(int, FileDownloadListener)
     * @see #replaceListener(String, String, FileDownloadListener)
     */
    public int replaceListener(String url, FileDownloadListener listener) {
        return replaceListener(url, FileDownloadUtils.getDefaultSaveFilePath(url), listener);
    }

    /**
     * Find the running task by {@code url} and {@code path}, and replace its listener with
     * the new one {@code listener}.
     *
     * @return The target task's DownloadId, if not exist target task, and replace failed, will be 0.
     * @see #replaceListener(String, FileDownloadListener)
     * @see #replaceListener(int, FileDownloadListener)
     */
    public int replaceListener(String url, String path, FileDownloadListener listener) {
        return replaceListener(FileDownloadUtils.generateId(url, path), listener);
    }

    /**
     * Find the running task by {@code id}, and replace its listener width the new one
     * {@code listener}.
     *
     * @return The target task's DownloadId, if not exist target task, and replace failed, will be 0.
     * @see #replaceListener(String, FileDownloadListener)
     * @see #replaceListener(String, String, FileDownloadListener)
     */
    public int replaceListener(int id, FileDownloadListener listener) {
        final BaseDownloadTask.IRunningTask task = FileDownloadList.getImpl().get(id);
        if (task == null) {
            return 0;
        }

        task.getOrigin().setListener(listener);
        return task.getOrigin().getId();
    }

    /**
     * 主动启动下载进程(可事先调用该方法(可以不调用)，保证第一次下载的时候没有启动进程的速度消耗)
     * Start and bind the FileDownloader service.
     * <p>
     * <strong>Tips:</strong> The FileDownloader service will start and bind automatically when any task
     * is request to start.
     *
     * @see #bindService(Runnable)
     * @see #isServiceConnected()
     * @see #addServiceConnectListener(FileDownloadConnectListener)
     */
    public void bindService() {
        if (!isServiceConnected()) {
            FileDownloadServiceProxy.getImpl().bindStartByContext(FileDownloadHelper.getAppContext());
        }
    }

    /**
     * Start and bind the FileDownloader service and run {@code runnable} as soon as the binding is
     * successful.
     * <p>
     * <strong>Tips:</strong> The FileDownloader service will start and bind automatically when any task
     * is request to start.
     *
     * @param runnable the command will be executed as soon as the FileDownloader Service is
     *                 successfully bound.
     * @see #isServiceConnected()
     * @see #bindService()
     * @see #addServiceConnectListener(FileDownloadConnectListener)
     */
    public void bindService(final Runnable runnable) {
        if (isServiceConnected()) {
            runnable.run();
        } else {
            FileDownloadServiceProxy.getImpl().
                    bindStartByContext(FileDownloadHelper.getAppContext(), runnable);
        }
    }

    /**
     * Unbind and stop the downloader service.
     */
    public void unBindService() {
        if (isServiceConnected()) {
            FileDownloadServiceProxy.getImpl().unbindByContext(FileDownloadHelper.getAppContext());
        }
    }

    /**
     * 如果任务栈中没有任务 解绑任务
     * Unbind and stop the downloader service when there is no task running in the FileDownloader.
     *
     * @return {@code true} if unbind and stop the downloader service successfully, {@code false}
     * there are some tasks running in the FileDownloader.
     */
    public boolean unBindServiceIfIdle() {
        // check idle
        if (!isServiceConnected()) {
            return false;
        }

        if (FileDownloadList.getImpl().isEmpty()
                && FileDownloadServiceProxy.getImpl().isIdle()) {
            unBindService();
            return true;
        }

        return false;
    }

    /**
     * 是否已经启动并且连接上下载进程(可参考任务管理demo中的使用)
     * @return {@code true} if the downloader service has been started and connected.
     */
    public boolean isServiceConnected() {
        return FileDownloadServiceProxy.getImpl().isConnected();
    }

    /**
     * 添加下载服务连接变化的监听器
     * Add the listener for listening when the status of connection with the downloader service is
     * changed.
     *
     * @param listener The downloader service connection listener.
     * @see #removeServiceConnectListener(FileDownloadConnectListener)
     */
    public void addServiceConnectListener(final FileDownloadConnectListener listener) {
        FileDownloadEventPool.getImpl().addListener(DownloadServiceConnectChangedEvent.ID
                , listener);
    }

    /**
     * Remove the listener for listening when the status of connection with the downloader service is
     * changed.
     *
     * @param listener The downloader service connection listener.
     * @see #addServiceConnectListener(FileDownloadConnectListener)
     */
    public void removeServiceConnectListener(final FileDownloadConnectListener listener) {
        FileDownloadEventPool.getImpl().removeListener(DownloadServiceConnectChangedEvent.ID
                , listener);
    }

    /**
     * 设置FileDownloadService为前台模式，保证用户从最近应用列表移除应用以后下载服务不会被杀
     * Start the {@code notification} with the {@code id}. This will let the downloader service
     * change to a foreground service.
     * <p>
     * In foreground status, will save the FileDownloader alive, even user kill the application from
     * recent apps.
     * <p/>
     * Make FileDownloader service run in the foreground, supplying the ongoing
     * notification to be shown to the user while in this state.
     * By default FileDownloader services are background, meaning that if the system needs to
     * kill them to reclaim more memory (such as to display a large page in a
     * web browser), they can be killed without too much harm.  You can set this
     * flag if killing your service would be disruptive to the user, such as
     * if your service is performing background downloading, so the user
     * would notice if their app stopped downloading.
     *
     * @param id           The identifier for this notification as per
     *                     {@link NotificationManager#notify(int, Notification)
     *                     NotificationManager.notify(int, Notification)}; must not be 0.
     * @param notification The notification to be displayed.
     * @see #stopForeground(boolean)
     */
    public void startForeground(int id, Notification notification) {
        FileDownloadServiceProxy.getImpl().startForeground(id, notification);
    }

    /**
     * Remove the downloader service from the foreground state, allowing it to be killed if
     * more memory is needed.
     *
     * @param removeNotification {@code true} if the notification previously provided
     *                           to {@link #startForeground} will be removed. {@code false} it will
     *                           be remained until a later call removes it (or the service is destroyed).
     * @see #startForeground(int, Notification)
     */
    public void stopForeground(boolean removeNotification) {
        FileDownloadServiceProxy.getImpl().stopForeground(removeNotification);
    }

    /**
     * 用于告诉FileDownloader引擎，以指定Url与Path的任务已经通过其他方式(非FileDownloader)下载完成
     * @param url        The url of the completed task.
     * @param path       The absolute path of the completed task's save file.
     * @param totalBytes The content-length of the completed task, the length of the file in the
     *                   {@code path} must be equal to this value.
     * @return Whether is successful to set the task completed. If the {@code path} not exist will be
     * false; If the length of the file in {@code path} is not equal to {@code totalBytes} will be
     * false; If the task with {@code url} and {@code path} is downloading will be false. Otherwise
     * will be true.
     * @see FileDownloadUtils#isFilenameConverted(Context)
     * <p>
     * <p/>
     * Recommend used to telling the FileDownloader Engine that the task with the {@code url}  and
     * the {@code path} has already completed downloading, in case of your task has already
     * downloaded by other ways(not by FileDownloader Engine), and after success to set the task
     * completed, FileDownloader will check the task with {@code url} and the {@code path} whether
     * completed by {@code totalBytes}.
     * <p/>
     * Otherwise, If FileDownloader Engine isn't know your task's status, whatever your task with
     * the {@code url} and the {@code path} has already downloaded in other way, FileDownloader
     * Engine will ignore the exist file and redownload it, because FileDownloader Engine don't know
     * the exist file whether it is valid.
     * @see #setTaskCompleted(List)
     * @deprecated If you invoked this method, please remove the code directly feel free, it doesn't
     * need any longer. In new mechanism(filedownloader 0.3.3 or higher), FileDownloader doesn't store
     * completed tasks in Database anymore, because all downloading files have temp a file name.
     */
    @SuppressWarnings("UnusedParameters")
    public boolean setTaskCompleted(String url, String path, long totalBytes) {
        FileDownloadLog.w(this, "If you invoked this method, please remove it directly feel free, " +
                "it doesn't need any longer");
        return true;
    }

    /**
     * 用于告诉FileDownloader引擎，指定的一系列的任务都已经通过其他方式(非FileDownloader)下载完成
     * Recommend used to telling the FileDownloader Engine that a bulk(大量) of tasks have already
     * downloaded by other ways(not by the FileDownloader Engine).
     * <p/>
     * The FileDownloader Engine need to know the status of completed, because if you want to
     * download any tasks, FileDownloader Engine judges whether the task need downloads or not
     * according its status which existed in DB.
     *
     * @param taskAtomList The bulk of tasks.
     * @return Whether is successful to update all tasks' status to the Filedownloader Engine. If
     * one task atom among them is not match the Rules in
     * FileDownloadMgr#obtainCompletedTaskShelfModel(String, String, long)
     * will receive false, and non of them would be updated to DB.
     * @see #setTaskCompleted(String, String, long)
     * @deprecated If you invoked this method, please remove the code directly feel free, it doesn't
     * need any longer. In new mechanism(filedownloader 0.3.3 or higher), FileDownloader doesn't store
     * completed tasks in Database anymore, because all downloading files have temp a file name.
     */
    @SuppressWarnings("UnusedParameters")
    public boolean setTaskCompleted(@SuppressWarnings("deprecation") List<FileDownloadTaskAtom> taskAtomList) {
        FileDownloadLog.w(this, "If you invoked this method, please remove it directly feel free, " +
                "it doesn't need any longer");
        return true;
    }

    /**
     * 设置最大并行下载的数目(网络下载线程数), [1,12]
     * Set the maximum count of the network thread, what is the number of simultaneous downloads in
     * FileDownloader.
     *
     * @param count the number of simultaneous downloads, scope: [1, 12].
     * @return whether is successful to set the max network thread count.
     * If there are any actively executing tasks in FileDownloader, you will receive a warn
     * priority log int the logcat and this operation would be failed.
     */
    public boolean setMaxNetworkThreadCount(final int count) {
        if (!FileDownloadList.getImpl().isEmpty()) {
            FileDownloadLog.w(this, "Can't change the max network thread count, because there " +
                    "are actively executing tasks in FileDownloader, please try again after all" +
                    " actively executing tasks are completed or invoking FileDownloader#pauseAll" +
                    " directly.");
            return false;
        }

        return FileDownloadServiceProxy.getImpl().setMaxNetworkThreadCount(count);
    }

    /**
     * If the FileDownloader service is not started and connected, FileDownloader will try to start
     * it and try to bind with it. The current thread will also be blocked until the FileDownloader
     * service is started and a connection is established, and then the request you
     * invoke in {@link FileDownloadLine} will be executed.
     * <p>
     * If the FileDownloader service has been started and connected, the request you invoke in
     * {@link FileDownloadLine} will be executed immediately.
     * <p>
     * <strong>Note:</strong> FileDownloader can not block the main thread, because the system is
     * also call-backs the {@link ServiceConnection#onServiceConnected(ComponentName, IBinder)}
     * method in the main thread.
     * <p>
     * <strong>Tips:</strong> The FileDownloader service will start and bind automatically when any
     * task is request to start.
     *
     * @see FileDownloadLine
     * @see #bindService(Runnable)
     */
    public FileDownloadLine insureServiceBind() {
        return new FileDownloadLine();
    }

    /**
     * If the FileDownloader service is not started and connected will return {@code false} immediately,
     * and meanwhile FileDownloader will try to start FileDownloader service and try to bind with it,
     * and after it is bound successfully the request you invoke in {@link FileDownloadLineAsync}
     * will be executed automatically.
     * <p>
     * If the FileDownloader service has been started and connected, the request you invoke in
     * {@link FileDownloadLineAsync} will be executed immediately.
     *
     * @see FileDownloadLineAsync
     * @see #bindService(Runnable)
     */
    public FileDownloadLineAsync insureServiceBindAsync() {
        return new FileDownloadLineAsync();
    }

    private final static Object INIT_QUEUES_HANDLER_LOCK = new Object();
    private IQueuesHandler mQueuesHandler;

    //获取队列处理单例
    IQueuesHandler getQueuesHandler() {
        if (mQueuesHandler == null) {
            synchronized (INIT_QUEUES_HANDLER_LOCK) {
                if (mQueuesHandler == null) {
                    mQueuesHandler = new QueuesHandler();
                }
            }
        }
        return mQueuesHandler;
    }

    private final static Object INIT_LOST_CONNECTED_HANDLER_LOCK = new Object();
    private ILostServiceConnectedHandler mLostConnectedHandler;

    ILostServiceConnectedHandler getLostConnectedHandler() {
        if (mLostConnectedHandler == null) {
            synchronized (INIT_LOST_CONNECTED_HANDLER_LOCK) {
                if (mLostConnectedHandler == null) {
                    mLostConnectedHandler = new LostServiceConnectedHandler();
                    addServiceConnectListener((FileDownloadConnectListener) mLostConnectedHandler);
                }
            }
        }

        return mLostConnectedHandler;
    }


}
