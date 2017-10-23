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

package com.zy.xxl.zyfiledownloader.download.filedownloader.message;

/**
 * 已完成
 * If the snapshot implement this interface, it will be flowed directly, it means that it would be
 * callback to the message station synchronize, not through the keep-flow-thread-pool.
 * 干啥的 只知道 如果继承这个 说明Callback调用这个message的时候是异步的
 */
public interface IFlowDirectly {
}
