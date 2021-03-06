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

package com.zy.xxl.zyfiledownloader.download.filedownloader.connection;


import com.zy.xxl.zyfiledownloader.download.filedownloader.download.CustomComponentHolder;
import com.zy.xxl.zyfiledownloader.download.filedownloader.util.FileDownloadLog;
import com.zy.xxl.zyfiledownloader.download.filedownloader.util.FileDownloadUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 已完成
 * Handle redirect case.
 */
public class RedirectHandler {

    /**
     * 最大重定向次数
     */
    private final static int MAX_REDIRECT_TIMES = 10;

    /**
     * The target resource resides temporarily（ 临时地，临时） under a different URI and the user agent MUST NOT
     * change the request method if it performs an automatic redirection to that URI.
     */
    private final static int HTTP_TEMPORARY_REDIRECT = 307;
    /**
     * The target resource has been assigned a new permanent URI and any future references to this
     * resource ought to use one of the enclosed URIs.
     */
    private final static int HTTP_PERMANENT_REDIRECT = 308;


    public static FileDownloadConnection process(final Map<String, List<String>> requestHeaderFields,
                                                 final FileDownloadConnection connection,
                                                 List<String> redirectedUrlList)
            throws IOException, IllegalAccessException {

        int code = connection.getResponseCode();
        String location = connection.getResponseHeaderField("Location");

        List<String> redirectLocationList = new ArrayList<>();
        int redirectTimes = 0;
        FileDownloadConnection redirectConnection = connection;

        while (isRedirect(code)) {
            if (location == null) {
                throw new IllegalAccessException(FileDownloadUtils.
                        formatString("receive %d (redirect) but the location is null with response [%s]",
                                code, redirectConnection.getResponseHeaderFields()));
            }

            if (FileDownloadLog.NEED_LOG) {
                FileDownloadLog.d(RedirectHandler.class, "redirect to %s with %d, %s",
                        location, code, redirectLocationList);
            }

            redirectConnection.ending();
            redirectConnection =
                    buildRedirectConnection(requestHeaderFields, location);
            redirectLocationList.add(location);

            redirectConnection.execute();
            code = redirectConnection.getResponseCode();
            location = redirectConnection.getResponseHeaderField("Location");

            if (++redirectTimes >= MAX_REDIRECT_TIMES) {
                throw new IllegalAccessException(
                        FileDownloadUtils.formatString("redirect too many times! %s", redirectLocationList));
            }
        }

        if (redirectedUrlList != null) {
            redirectedUrlList.addAll(redirectLocationList);
        }

        return redirectConnection;
    }

    /**
     * 是否重定向
     * 这类状态码代表需要客户端采取进一步的操作才能完成请求。通常，这些状态码用来重定向，后续的请求地址（重定向目标）在本次响应的 Location 域中指明。
     * @param code
     * @return
     */
    private static boolean isRedirect(int code) {
        return code == HttpURLConnection.HTTP_MOVED_PERM
                || code == HttpURLConnection.HTTP_MOVED_TEMP
                || code == HttpURLConnection.HTTP_SEE_OTHER
                || code == HttpURLConnection.HTTP_MULT_CHOICE
                || code == HTTP_TEMPORARY_REDIRECT
                || code == HTTP_PERMANENT_REDIRECT;
    }

    /**
     * 为请求添加头部
     * @param requestHeaderFields
     * @param newUrl
     * @return
     * @throws IOException
     */
    private static FileDownloadConnection buildRedirectConnection(Map<String, List<String>> requestHeaderFields,
                                                                  String newUrl) throws IOException {
        FileDownloadConnection redirectConnection = CustomComponentHolder.getImpl().
                createConnection(newUrl);

        String name;
        List<String> list;

        Set<Map.Entry<String, List<String>>> entries = requestHeaderFields.entrySet();
        for (Map.Entry<String, List<String>> e : entries) {
            name = e.getKey();
            list = e.getValue();
            if (list != null) {
                for (String value : list) {
                    redirectConnection.addHeader(name, value);
                }
            }
        }

        return redirectConnection;
    }
}
