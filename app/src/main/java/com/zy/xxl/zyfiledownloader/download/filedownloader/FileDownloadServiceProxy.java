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

import com.zy.xxl.zyfiledownloader.download.filedownloader.model.FileDownloadHeader;
import com.zy.xxl.zyfiledownloader.download.filedownloader.services.FDServiceSharedHandler;
import com.zy.xxl.zyfiledownloader.download.filedownloader.util.FileDownloadProperties;


/**
 * 已完成
 * FileDownloadService的代理类
 * The proxy used for executing the action from FileDownloader to FileDownloadService.
 *
 * @see FileDownloadServiceSharedTransmit In case of FileDownloadService runs in the main process. 运行在主进程
 * @see FileDownloadServiceUIGuard In case of FileDownloadService runs in the separate `:filedownloader` process. 运行在独立进程
 * <p/>
 * You can add a command `process.non-separate=true` to `/filedownloader.properties` to make the
 * FileDownloadService runs in the main process, and by default the FileDownloadService runs in the
 * separate `:filedownloader` process.
 */
public class FileDownloadServiceProxy implements IFileDownloadServiceProxy {

    private final static class HolderClass {
        private final static FileDownloadServiceProxy INSTANCE = new FileDownloadServiceProxy();
    }

    public static FileDownloadServiceProxy getImpl() {
        return HolderClass.INSTANCE;
    }

    public static FDServiceSharedHandler.FileDownloadServiceSharedConnection getConnectionListener() {
        if (getImpl().handler instanceof FileDownloadServiceSharedTransmit) {
            return (FDServiceSharedHandler.FileDownloadServiceSharedConnection) getImpl().handler;
        }
        return null;
    }

    private final IFileDownloadServiceProxy handler;

    private FileDownloadServiceProxy() {
        handler = FileDownloadProperties.getImpl().PROCESS_NON_SEPARATE ?
                new FileDownloadServiceSharedTransmit() :
                new FileDownloadServiceUIGuard();
    }

    @Override
    public boolean start(String url, String path, boolean pathAsDirectory, int callbackProgressTimes,
                         int callbackProgressMinIntervalMillis,
                         int autoRetryTimes, boolean forceReDownload, FileDownloadHeader header,
                         boolean isWifiRequired) {
        return handler.start(url, path, pathAsDirectory, callbackProgressTimes,
                callbackProgressMinIntervalMillis, autoRetryTimes, forceReDownload, header,
                isWifiRequired);
    }

    @Override
    public boolean pause(int id) {
        return handler.pause(id);
    }

    @Override
    public boolean isDownloading(String url, String path) {
        return handler.isDownloading(url, path);
    }

    @Override
    public long getSofar(int id) {
        return handler.getSofar(id);
    }

    @Override
    public long getTotal(int id) {
        return handler.getTotal(id);
    }

    //获取指定ID的任务状态
    @Override
    public byte getStatus(int id) {
        return handler.getStatus(id);
    }

    @Override
    public void pauseAllTasks() {
        handler.pauseAllTasks();
    }

    @Override
    public boolean isIdle() {
        return handler.isIdle();
    }

    //服务是否连接上
    @Override
    public boolean isConnected() {
        return handler.isConnected();
    }

    //通过context绑定任务
    @Override
    public void bindStartByContext(Context context) {
        handler.bindStartByContext(context);
    }

    //通过context绑定任务并且 立即运行
    @Override
    public void bindStartByContext(Context context, Runnable connectedRunnable) {
        handler.bindStartByContext(context, connectedRunnable);
    }

    //解绑任务
    @Override
    public void unbindByContext(Context context) {
        handler.unbindByContext(context);
    }


    //设置为前台服务
    @Override
    public void startForeground(int notificationId, Notification notification) {
        handler.startForeground(notificationId, notification);
    }

    @Override
    public void stopForeground(boolean removeNotification) {
        handler.stopForeground(removeNotification);
    }

    //设置最大下载数
    @Override
    public boolean setMaxNetworkThreadCount(int count) {
        return handler.setMaxNetworkThreadCount(count);
    }

    //清除指定ID的任务的数据
    @Override
    public boolean clearTaskData(int id) {
        return handler.clearTaskData(id);
    }

    //清除任务栈所有任务的数据
    @Override
    public void clearAllTaskData() {
        handler.clearAllTaskData();
    }
}
