/*
 * Copyright(C) 2016 大前良介(OHMAE Ryosuke)
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.mm2d.upnp;

/**
 * HttpClientを作成するファクトリークラス。
 *
 * @author <a href="mailto:ryo@mm2d.net">大前良介(OHMAE Ryosuke)</a>
 */
public class HttpClientFactory {
    /**
     * HttpClientを作成する。
     *
     * @param keepAlive keep-alive通信を行う場合true
     * @return HttpClientのインスタンス
     * @see HttpClient#HttpClient(boolean)
     */
    public HttpClient createHttpClient(boolean keepAlive) {
        return new HttpClient(keepAlive);
    }
}
