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

package com.zy.xxl.zyfiledownloader.download.filedownloader.services;


import android.text.TextUtils;

import com.zy.xxl.zyfiledownloader.download.filedownloader.IThreadPoolMonitor;
import com.zy.xxl.zyfiledownloader.download.filedownloader.download.CustomComponentHolder;
import com.zy.xxl.zyfiledownloader.download.filedownloader.download.DownloadLaunchRunnable;
import com.zy.xxl.zyfiledownloader.download.filedownloader.model.ConnectionModel;
import com.zy.xxl.zyfiledownloader.download.filedownloader.model.FileDownloadHeader;
import com.zy.xxl.zyfiledownloader.download.filedownloader.model.FileDownloadModel;
import com.zy.xxl.zyfiledownloader.download.filedownloader.model.FileDownloadStatus;
import com.zy.xxl.zyfiledownloader.download.filedownloader.util.FileDownloadHelper;
import com.zy.xxl.zyfiledownloader.download.filedownloader.util.FileDownloadLog;
import com.zy.xxl.zyfiledownloader.download.filedownloader.util.FileDownloadUtils;

import java.util.List;

/**
 * 已完成
 * The downloading manager in FileDownloadService, which is used to control all download-inflow.
 * <p/>
 * Handling real {@link #start(String, String, boolean, int, int, int, boolean, FileDownloadHeader,
 * boolean)}.
 *
 * @see FileDownloadThreadPool
 * @see DownloadLaunchRunnable
 * @see com.zy.xxl.zyfiledownloader.download.filedownloader.download.DownloadRunnable
 */
class FileDownloadManager implements IThreadPoolMonitor {
    private final FileDownloadDatabase mDatabase;
    private final FileDownloadThreadPool mThreadPool;

    public FileDownloadManager() {
        final CustomComponentHolder holder = CustomComponentHolder.getImpl();
        this.mDatabase = holder.getDatabaseInstance();
        this.mThreadPool = new FileDownloadThreadPool(holder.getMaxNetworkThreadCount());
    }

    // TODO: 2017/10/24 没看明白 需要心平气和的时候多卡几遍
    // synchronize for safe: check downloading, check resume, update data, execute runnable
    public synchronized void start(final String url, final String path, final boolean pathAsDirectory,
                                   final int callbackProgressTimes,
                                   final int callbackProgressMinIntervalMillis,
                                   final int autoRetryTimes, final boolean forceReDownload,
                                   final FileDownloadHeader header, final boolean isWifiRequired) {
        if (FileDownloadLog.NEED_LOG) {
            FileDownloadLog.d(this, "request start the task with url(%s) path(%s) isDirectory(%B)",
                    url, path, pathAsDirectory);
        }

        final int id = FileDownloadUtils.generateId(url, path, pathAsDirectory);
        FileDownloadModel model = mDatabase.find(id);

        List<ConnectionModel> dirConnectionModelList = null;

        if (!pathAsDirectory && model == null) {
            // try dir data.
            final int dirCaseId = FileDownloadUtils.generateId(url, FileDownloadUtils.getParent(path),
                    true);
            model = mDatabase.find(dirCaseId);
            if (model != null && path.equals(model.getTargetFilePath())) {
                if (FileDownloadLog.NEED_LOG) {
                    FileDownloadLog.d(this, "task[%d] find model by dirCaseId[%d]", id, dirCaseId);
                }

                dirConnectionModelList = mDatabase.findConnectionModel(dirCaseId);
            }
        }

        if (FileDownloadHelper.inspectAndInflowDownloading(id, model, this, true)) {
            if (FileDownloadLog.NEED_LOG) {
                FileDownloadLog.d(this, "has already started download %d", id);
            }
            return;
        }

        final String targetFilePath = model != null ? model.getTargetFilePath() :
                FileDownloadUtils.getTargetFilePath(path, pathAsDirectory, null);
        if (FileDownloadHelper.inspectAndInflowDownloaded(id, targetFilePath, forceReDownload,
                true)) {
            if (FileDownloadLog.NEED_LOG) {
                FileDownloadLog.d(this, "has already completed downloading %d", id);
            }
            return;
        }

        final long sofar = model != null ? model.getSoFar() : 0;
        final String tempFilePath = model != null ? model.getTempFilePath() :
                FileDownloadUtils.getTempPath(targetFilePath);
        if (FileDownloadHelper.inspectAndInflowConflictPath(id, sofar, tempFilePath, targetFilePath,
                this)) {
            if (FileDownloadLog.NEED_LOG) {
                FileDownloadLog.d(this, "there is an another task with the same target-file-path %d %s",
                        id, targetFilePath);
                // because of the file is dirty for this task.
                if (model != null) {
                    mDatabase.remove(id);
                    mDatabase.removeConnections(id);
                }
            }
            return;
        }

        // real start
        // - create model
        boolean needUpdate2DB;
        if (model != null &&
                (model.getStatus() == FileDownloadStatus.paused ||
                        model.getStatus() == FileDownloadStatus.error ||
                        model.getStatus() == FileDownloadStatus.pending ||
                        model.getStatus() == FileDownloadStatus.started ||
                        model.getStatus() == FileDownloadStatus.connected) // FileDownloadRunnable invoke
            // #isBreakpointAvailable to determine whether it is really invalid.
                ) {
            if (model.getId() != id) {
                // in try dir case.
                mDatabase.remove(model.getId());
                mDatabase.removeConnections(model.getId());

                model.setId(id);
                model.setPath(path, pathAsDirectory);
                if (dirConnectionModelList != null) {
                    for (ConnectionModel connectionModel : dirConnectionModelList) {
                        connectionModel.setId(id);
                        mDatabase.insertConnectionModel(connectionModel);
                    }
                }

                needUpdate2DB = true;
            } else {
                if (!TextUtils.equals(url, model.getUrl())) {
                    // for cover the case of reusing the downloaded processing with the different url( using with idGenerator ).
                    model.setUrl(url);
                    needUpdate2DB = true;
                } else {
                    needUpdate2DB = false;
                }
            }
        } else {
            if (model == null) {
                model = new FileDownloadModel();
            }
            model.setUrl(url);
            model.setPath(path, pathAsDirectory);

            model.setId(id);
            model.setSoFar(0);
            model.setTotal(0);
            model.setStatus(FileDownloadStatus.pending);
            model.setConnectionCount(1);
            needUpdate2DB = true;
        }

        // - update model to db
        if (needUpdate2DB) {
            mDatabase.update(model);
        }

        final DownloadLaunchRunnable.Builder builder = new DownloadLaunchRunnable.Builder();

        final DownloadLaunchRunnable runnable =
                builder.setModel(model)
                        .setHeader(header)
                        .setThreadPoolMonitor(this)
                        .setMinIntervalMillis(callbackProgressMinIntervalMillis)
                        .setCallbackProgressMaxCount(callbackProgressTimes)
                        .setForceReDownload(forceReDownload)
                        .setWifiRequired(isWifiRequired)
                        .setMaxRetryTimes(autoRetryTimes)
                        .build();

        // - execute
        mThreadPool.execute(runnable);

    }

    public boolean isDownloading(String url, String path) {
        return isDownloading(FileDownloadUtils.generateId(url, path));
    }

    public boolean isDownloading(int id) {
        return isDownloading(mDatabase.find(id));
    }

    public boolean pause(final int id) {
        if (FileDownloadLog.NEED_LOG) {
            FileDownloadLog.d(this, "request pause the task %d", id);
        }

        final FileDownloadModel model = mDatabase.find(id);
        if (model == null) {
            return false;
        }

        mThreadPool.cancel(id);
        return true;
    }

    /**
     * 暂停所有线程
     * Pause all running task
     */
    public void pauseAll() {
        List<Integer> list = mThreadPool.getAllExactRunningDownloadIds();

        if (FileDownloadLog.NEED_LOG) {
            FileDownloadLog.d(this, "pause all tasks %d", list.size());
        }

        for (Integer id : list) {
            pause(id);
        }
    }


    public long getSoFar(final int id) {
        final FileDownloadModel model = mDatabase.find(id);
        if (model == null) {
            return 0;
        }

        final int connectionCount = model.getConnectionCount();
        if (connectionCount <= 1) {
            return model.getSoFar();
        } else {
            final List<ConnectionModel> modelList = mDatabase.findConnectionModel(id);
            if (modelList == null || modelList.size() != connectionCount) {
                return 0;
            } else {
                return ConnectionModel.getTotalOffset(modelList);
            }
        }
    }

    public long getTotal(final int id) {
        final FileDownloadModel model = mDatabase.find(id);
        if (model == null) {
            return 0;
        }

        return model.getTotal();
    }

    public byte getStatus(final int id) {
        final FileDownloadModel model = mDatabase.find(id);
        if (model == null) {
            return FileDownloadStatus.INVALID_STATUS;
        }

        return model.getStatus();
    }

    /**
     * 线程池是否闲置
     * @return
     */
    public boolean isIdle() {
        return mThreadPool.exactSize() <= 0;
    }


    /**
     * 设置最大并行下载的数目(网络下载线程数), [1,12]
     * @param count
     * @return
     */
    public synchronized boolean setMaxNetworkThreadCount(int count) {
        return mThreadPool.setMaxNetworkThreadCount(count);
    }

    /**
     * 是否正在下载
     * @param model
     * @return
     */
    @Override
    public boolean isDownloading(FileDownloadModel model) {
        if (model == null) {
            return false;
        }

        final boolean isInPool = mThreadPool.isInThreadPool(model.getId());
        boolean isDownloading;

        do {
            if (FileDownloadStatus.isOver(model.getStatus())) {

                //noinspection RedundantIfStatement
                if (isInPool) {
                    // already finished, but still in the pool.
                    // handle as downloading.
                    isDownloading = true;
                } else {
                    // already finished, and not in the pool.
                    // make sense.
                    isDownloading = false;

                }
            } else {
                if (isInPool) {
                    // not finish, in the pool.
                    // make sense.
                    isDownloading = true;
                } else {
                    // not finish, but not in the pool.
                    // beyond expectation.
                    FileDownloadLog.e(this, "%d status is[%s](not finish) & but not in the pool",
                            model.getId(), model.getStatus());
                    // handle as not in downloading, going to re-downloading.
                    isDownloading = false;

                }
            }
        } while (false);

        return isDownloading;
    }

    /**
     * 找到相同缓存路径下的任务
     * @param tempFilePath
     * @param excludeId
     * @return
     */
    @Override
    public int findRunningTaskIdBySameTempPath(String tempFilePath, int excludeId) {
        return mThreadPool.findRunningTaskIdBySameTempPath(tempFilePath, excludeId);
    }


    public boolean clearTaskData(int id) {
        if (id == 0) {
            FileDownloadLog.w(this, "The task[%d] id is invalid, can't clear it.", id);
            return false;
        }

        if (isDownloading(id)) {
            FileDownloadLog.w(this, "The task[%d] is downloading, can't clear it.", id);
            return false;
        }

        mDatabase.remove(id);
        mDatabase.removeConnections(id);
        return true;
    }

    public void clearAllTaskData() {
        mDatabase.clear();
    }
}

