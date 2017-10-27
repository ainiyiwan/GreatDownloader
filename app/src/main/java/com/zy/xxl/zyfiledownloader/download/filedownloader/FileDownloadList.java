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
import com.zy.xxl.zyfiledownloader.download.filedownloader.message.MessageSnapshotTaker;
import com.zy.xxl.zyfiledownloader.download.filedownloader.model.FileDownloadStatus;
import com.zy.xxl.zyfiledownloader.download.filedownloader.util.FileDownloadLog;

import java.util.ArrayList;
import java.util.List;

/**
 * 已完成
 * Storing all tasks in processing in the Main-Process.
 */
@SuppressWarnings("UnusedReturnValue")
public class FileDownloadList {

    private final static class HolderClass {
        private final static FileDownloadList INSTANCE = new FileDownloadList();
    }

    public static FileDownloadList getImpl() {
        return HolderClass.INSTANCE;
    }

    private final ArrayList<BaseDownloadTask.IRunningTask> mList;

    private FileDownloadList() {
        mList = new ArrayList<>();
    }

    boolean isEmpty() {
        return mList.isEmpty();
    }

    int size() {
        return mList.size();
    }

    /**
     * @param id download id
     * @return get counts os same id
     */
    int count(final int id) {
        int size = 0;
        synchronized (mList) {
            for (BaseDownloadTask.IRunningTask task : mList) {
                if (task.is(id)) {
                    size++;
                }
            }
        }
        return size;
    }

    //获取正在运行的指定ID的任务
    public BaseDownloadTask.IRunningTask get(final int id) {
        synchronized (mList) {
            for (BaseDownloadTask.IRunningTask task : mList) {
                // when FileDownloadMgr#isDownloading
                if (task.is(id)) {
                    return task;
                }
            }
        }
        return null;
    }

    /**
     * 获取到服务的队列
     * @param id
     * @return
     */
    List<BaseDownloadTask.IRunningTask> getReceiveServiceTaskList(final int id){
        final List<BaseDownloadTask.IRunningTask> list = new ArrayList<>();
        synchronized (this.mList) {
            for (BaseDownloadTask.IRunningTask task : this.mList) {
                if (task.is(id) && !task.isOver()) {

                    final byte status = task.getOrigin().getStatus();
                    if (status != FileDownloadStatus.INVALID_STATUS &&
                            status != FileDownloadStatus.toLaunchPool) {

                        list.add(task);
                    }
                }
            }
        }

        return list;
    }

    //获取下载队列中任务为指定ID的任务
    List<BaseDownloadTask.IRunningTask> getDownloadingList(final int id) {
        final List<BaseDownloadTask.IRunningTask> list = new ArrayList<>();
        synchronized (this.mList) {
            for (BaseDownloadTask.IRunningTask task : this.mList) {
                if (task.is(id) &&
                        !task.isOver()) {
                    list.add(task);
                }
            }
        }

        return list;
    }

    boolean isNotContains(final BaseDownloadTask.IRunningTask download) {
        return mList.isEmpty() || !mList.contains(download);
    }

    //获取相同监听器的任务集合
    List<BaseDownloadTask.IRunningTask> copy(final FileDownloadListener listener) {
        final List<BaseDownloadTask.IRunningTask> targetList = new ArrayList<>();
        synchronized (mList) {
            // Prevent size changing
            for (BaseDownloadTask.IRunningTask task : mList) {
                if (task.is(listener)) {
                    targetList.add(task);
                }
            }
            return targetList;
        }
    }

    //开启一个集合的任务
    List<BaseDownloadTask.IRunningTask> assembleTasksToStart(int attachKey,
                                                             FileDownloadListener listener) {
        final List<BaseDownloadTask.IRunningTask> targetList = new ArrayList<>();
        synchronized (mList) {
            // Prevent size changing
            for (BaseDownloadTask.IRunningTask task : mList) {
                if (task.getOrigin().getListener() == listener && !task.getOrigin().isAttached()) {
                    task.setAttachKeyByQueue(attachKey);
                    targetList.add(task);
                }
            }
            return targetList;
        }
    }

    BaseDownloadTask.IRunningTask[] copy() {
        synchronized (mList) {
            // Prevent size changing
            BaseDownloadTask.IRunningTask[] copy = new BaseDownloadTask.IRunningTask[mList.size()];
            return mList.toArray(copy);
        }
    }

    /**
     * Divert（转移；使…欢娱；使…转向） all data in list 2 destination list （Duplicate(复制；使加倍)）
     */
    void divertAndIgnoreDuplicate(@SuppressWarnings("SameParameterValue") final List<BaseDownloadTask.IRunningTask>
                                          destination) {
        synchronized (mList) {
            for (BaseDownloadTask.IRunningTask iRunningTask : mList) {
                if (!destination.contains(iRunningTask)) {
                    destination.add(iRunningTask);
                }
            }
            mList.clear();
        }
    }

    /**
     * @param willRemoveDownload will be remove
     */
    public boolean remove(final BaseDownloadTask.IRunningTask willRemoveDownload,
                          MessageSnapshot snapshot) {
        final byte removeByStatus = snapshot.getStatus();
        boolean succeed;
        synchronized (mList) {
            succeed = mList.remove(willRemoveDownload);
        }
        if (FileDownloadLog.NEED_LOG) {
            if (mList.size() == 0) {
                FileDownloadLog.v(this, "remove %s left %d %d",
                        willRemoveDownload, removeByStatus, mList.size());
            }
        }

        if (succeed) {
            final IFileDownloadMessenger messenger = willRemoveDownload.getMessageHandler().
                    getMessenger();
            // Notify 2 Listener
            switch (removeByStatus) {
                case FileDownloadStatus.warn:
                    messenger.notifyWarn(snapshot);
                    break;
                case FileDownloadStatus.error:
                    messenger.notifyError(snapshot);
                    break;
                case FileDownloadStatus.paused:
                    messenger.notifyPaused(snapshot);
                    break;
                case FileDownloadStatus.completed:
                    messenger.notifyBlockComplete(MessageSnapshotTaker.takeBlockCompleted(snapshot));
                    break;
            }

        } else {
            FileDownloadLog.e(this, "remove error, not exist: %s %d", willRemoveDownload,
                    removeByStatus);
        }

        return succeed;
    }

    void add(final BaseDownloadTask.IRunningTask task) {
        if (!task.getOrigin().isAttached()) {
            // if this task didn't attach to any key, this task must be an isolated task, so we
            // generate a key and attache it to this task, make sure this task not be assembled by
            // a queue.
            task.setAttachKeyDefault();
        }

        if (task.getMessageHandler().getMessenger().notifyBegin()) {
            addUnchecked(task);
        }
    }

    /**
     *  将任务加入队列
     * This method generally used for enqueuing the task which will be assembled（组装，集合） by a queue.
     *
     * @see BaseDownloadTask.InQueueTask#enqueue()
     */
    void addUnchecked(final BaseDownloadTask.IRunningTask task) {
        if (task.isMarkedAdded2List()) {
            return;
        }

        synchronized (mList) {
            if (mList.contains(task)) {
                FileDownloadLog.w(this, "already has %s", task);
            } else {
                task.markAdded2List();
                mList.add(task);
                if (FileDownloadLog.NEED_LOG) {
                    FileDownloadLog.v(this, "add list in all %s %d %d", task,
                            task.getOrigin().getStatus(), mList.size());
                }
            }
        }
    }
}
