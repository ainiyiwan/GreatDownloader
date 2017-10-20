// IFileDownloadIPCCallback.aidl
package com.zy.xxl.zyfiledownloader.i;

// Declare any non-default types here with import statements
import com.zy.xxl.zyfiledownloader.download.filedownloader.message.MessageSnapshot;

interface IFileDownloadIPCCallback {
    oneway void callback(in MessageSnapshot snapshot);
}

