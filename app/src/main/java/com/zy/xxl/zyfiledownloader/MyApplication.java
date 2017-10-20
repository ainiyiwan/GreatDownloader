package com.zy.xxl.zyfiledownloader;

import android.app.Application;
import android.content.Context;

import com.zy.xxl.zyfiledownloader.download.filedownloader.FileDownloader;
import com.zy.xxl.zyfiledownloader.download.filedownloader.connection.FileDownloadUrlConnection;
import com.zy.xxl.zyfiledownloader.download.filedownloader.util.FileDownloadLog;

import java.net.Proxy;

/**
 * Author ： zhangyang
 * Date   ： 2017/10/19
 * Email  :  18610942105@163.com
 * Description  :
 */

public class MyApplication extends Application {
    public static Context CONTEXT;
    private final static String TAG = "FileDownloadApplication";

    @Override
    public void onCreate() {
        super.onCreate();
        // for demo.
        CONTEXT = this;

        // just for open the log in this demo project.
        FileDownloadLog.NEED_LOG = BuildConfig.DOWNLOAD_NEED_LOG;

        /**
         * just for cache Application's Context, and ':filedownloader' progress will NOT be launched
         * by below code, so please do not worry about performance.
         * @see FileDownloader#init(Context)
         */
        FileDownloader.setupOnApplicationOnCreate(this)
                .connectionCreator(new FileDownloadUrlConnection
                        .Creator(new FileDownloadUrlConnection.Configuration()
                        .connectTimeout(15_000) // set connection timeout.
                        .readTimeout(15_000) // set read timeout.
                        .proxy(Proxy.NO_PROXY) // set proxy
                ))
                .commit();

    }
}
