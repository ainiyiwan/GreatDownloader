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

import android.app.Notification;
import android.content.Context;
import android.content.Intent;

import com.zy.xxl.zyfiledownloader.download.filedownloader.event.DownloadServiceConnectChangedEvent;
import com.zy.xxl.zyfiledownloader.download.filedownloader.model.FileDownloadHeader;
import com.zy.xxl.zyfiledownloader.download.filedownloader.services.FDServiceSharedHandler;
import com.zy.xxl.zyfiledownloader.download.filedownloader.services.FileDownloadService;
import com.zy.xxl.zyfiledownloader.download.filedownloader.util.DownloadServiceNotConnectedHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * 已完成
 * 其实就是个判断类 如果Service没有开启就打印Log
 * This transmit（传输；传播；发射；传达；遗传） layer is used for the FileDownloader-Process is shared the main process.
 * <p/>
 * If you want use this transmit and want the FileDownloadService share the main process, not in the
 * separate process, just add a command `process.non-separate=true` in `/filedownloader.properties`.
 *
 * @see FileDownloadServiceUIGuard
 */
class FileDownloadServiceSharedTransmit implements
        IFileDownloadServiceProxy, FDServiceSharedHandler.FileDownloadServiceSharedConnection {

    private final static Class<?> SERVICE_CLASS = FileDownloadService.SharedMainProcessService.class;

    @Override
    public boolean start(String url, String path, boolean pathAsDirectory, int callbackProgressTimes,
                         int callbackProgressMinIntervalMillis,
                         int autoRetryTimes, boolean forceReDownload, FileDownloadHeader header,
                         boolean isWifiRequired) {
        if (!isConnected()) {
            return DownloadServiceNotConnectedHelper.start(url, path, pathAsDirectory);
        }

        handler.start(url, path, pathAsDirectory, callbackProgressTimes, callbackProgressMinIntervalMillis,
                autoRetryTimes, forceReDownload, header, isWifiRequired);
        return true;
    }

    @Override
    public boolean pause(int id) {
        if (!isConnected()) {
            return DownloadServiceNotConnectedHelper.pause(id);
        }

        return handler.pause(id);
    }

    @Override
    public boolean isDownloading(String url, String path) {
        if (!isConnected()) {
            return DownloadServiceNotConnectedHelper.isDownloading(url, path);
        }

        return handler.checkDownloading(url, path);
    }

    @Override
    public long getSofar(int id) {
        if (!isConnected()) {
            return DownloadServiceNotConnectedHelper.getSofar(id);
        }

        return handler.getSofar(id);
    }

    @Override
    public long getTotal(int id) {
        if (!isConnected()) {
            return DownloadServiceNotConnectedHelper.getTotal(id);
        }

        return handler.getTotal(id);
    }

    @Override
    public byte getStatus(int id) {
        if (!isConnected()) {
            return DownloadServiceNotConnectedHelper.getStatus(id);
        }

        return handler.getStatus(id);
    }

    @Override
    public void pauseAllTasks() {
        if (!isConnected()) {
            DownloadServiceNotConnectedHelper.pauseAllTasks();
            return;
        }

        handler.pauseAllTasks();
    }

    @Override
    public boolean isIdle() {
        if (!isConnected()) {
            return DownloadServiceNotConnectedHelper.isIdle();
        }

        return handler.isIdle();
    }

    @Override
    public boolean isConnected() {
        return handler != null;
    }

    @Override
    public void bindStartByContext(Context context) {
        bindStartByContext(context, null);
    }

    private final ArrayList<Runnable> connectedRunnableList = new ArrayList<>();

    @Override
    public void bindStartByContext(Context context, Runnable connectedRunnable) {
        if (connectedRunnable != null) {
            if (!connectedRunnableList.contains(connectedRunnable)) {
                connectedRunnableList.add(connectedRunnable);
            }
        }
        Intent i = new Intent(context, SERVICE_CLASS);
        context.startService(i);
    }

    @Override
    public void unbindByContext(Context context) {
        Intent i = new Intent(context, SERVICE_CLASS);
        context.stopService(i);
        handler = null;
    }

    @Override
    public void startForeground(int notificationId, Notification notification) {
        if (!isConnected()) {
            DownloadServiceNotConnectedHelper.startForeground(notificationId, notification);
            return;
        }

        handler.startForeground(notificationId, notification);
    }

    @Override
    public void stopForeground(boolean removeNotification) {
        if (!isConnected()) {
            DownloadServiceNotConnectedHelper.stopForeground(removeNotification);
            return;
        }

        handler.stopForeground(removeNotification);
    }

    @Override
    public boolean setMaxNetworkThreadCount(int count) {
        if (!isConnected()) {
            return DownloadServiceNotConnectedHelper.setMaxNetworkThreadCount(count);
        }

        return handler.setMaxNetworkThreadCount(count);
    }

    @Override
    public boolean clearTaskData(int id) {
        if (!isConnected()) {
            return DownloadServiceNotConnectedHelper.clearTaskData(id);
        }
        return handler.clearTaskData(id);
    }

    @Override
    public void clearAllTaskData() {
        if (!isConnected()) {
            DownloadServiceNotConnectedHelper.clearAllTaskData();
            return;
        }

        handler.clearAllTaskData();
    }

    private FDServiceSharedHandler handler;

    @Override
    public void onConnected(final FDServiceSharedHandler handler) {
        this.handler = handler;
        @SuppressWarnings("unchecked") final List<Runnable> runnableList =
                (List<Runnable>) connectedRunnableList.clone();
        connectedRunnableList.clear();
        for (Runnable runnable : runnableList) {
            runnable.run();
        }

        FileDownloadEventPool.getImpl().
                asyncPublishInNewThread(new DownloadServiceConnectChangedEvent(
                        DownloadServiceConnectChangedEvent.ConnectStatus.connected,
                        SERVICE_CLASS));
    }

    @Override
    public void onDisconnected() {
        this.handler = null;
        FileDownloadEventPool.getImpl().
                asyncPublishInNewThread(new DownloadServiceConnectChangedEvent(
                        DownloadServiceConnectChangedEvent.ConnectStatus.disconnected,
                        SERVICE_CLASS));
    }


}
