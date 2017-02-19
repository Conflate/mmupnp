/*
 * Copyright(C) 2016 大前良介(OHMAE Ryosuke)
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.mm2d.upnp;

import net.mm2d.util.Log;
import net.mm2d.util.TextUtils;

import java.io.IOException;
import java.net.InterfaceAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Serviceを表すクラス。
 *
 * @author <a href="mailto:ryo@mm2d.net">大前良介(OHMAE Ryosuke)</a>
 */
public class Service {
    private static final String TAG = Service.class.getSimpleName();

    /**
     * DeviceDescriptionのパース時に使用するビルダー
     */
    public static class Builder {
        private Device mDevice;
        private String mServiceType;
        private String mServiceId;
        private String mScpdUrl;
        private String mControlUrl;
        private String mEventSubUrl;
        private String mDescription;
        private List<Action.Builder> mActionBuilderList;
        private List<StateVariable.Builder> mVariableBuilderList;

        /**
         * インスタンス作成
         */
        public Builder() {
        }

        /**
         * このServiceを保持するDeviceを登録する。
         *
         * @param device このServiceを保持するDevice
         * @return Builder
         */
        @Nonnull
        public Builder setDevice(@Nonnull Device device) {
            mDevice = device;
            return this;
        }

        /**
         * serviceTypeを登録する。
         *
         * @param serviceType serviceType
         * @return Builder
         */
        @Nonnull
        public Builder setServiceType(@Nonnull String serviceType) {
            mServiceType = serviceType;
            return this;
        }

        /**
         * serviceIdを登録する
         *
         * @param serviceId serviceId
         * @return Builder
         */
        @Nonnull
        public Builder setServiceId(@Nonnull String serviceId) {
            mServiceId = serviceId;
            return this;
        }

        /**
         * SCPDURLを登録する
         *
         * @param scpdUrl ScpdURL
         * @return Builder
         */
        @Nonnull
        public Builder setScpdUrl(@Nonnull String scpdUrl) {
            mScpdUrl = scpdUrl;
            return this;
        }

        public String getScpdUrl() {
            return mScpdUrl;
        }

        /**
         * controlURLを登録する。
         *
         * @param controlUrl controlURL
         * @return Builder
         */
        @Nonnull
        public Builder setControlUrl(@Nonnull String controlUrl) {
            mControlUrl = controlUrl;
            return this;
        }

        /**
         * eventSubURLを登録する。
         *
         * @param eventSubUrl eventSubURL
         * @return Builder
         */
        @Nonnull
        public Builder setEventSubUrl(@Nonnull String eventSubUrl) {
            mEventSubUrl = eventSubUrl;
            return this;
        }

        /**
         * Description XMLを登録する。
         *
         * @param description Description XML全内容
         * @return Builder
         */
        public Builder setDescription(String description) {
            mDescription = description;
            return this;
        }

        /**
         * 全ActionのBuilderを登録する。
         *
         * @param actionBuilderList Serviceで定義されている全ActionのBuilder
         * @return Builder
         */
        public Builder setActionBuilderList(List<Action.Builder> actionBuilderList) {
            mActionBuilderList = actionBuilderList;
            return this;
        }

        /**
         * 全StateVariableのBuilderを登録する。
         *
         * @param variableBuilderList Serviceで定義されている全StateVariableのBuilder
         * @return Builder
         */
        public Builder setVariableBuilderList(List<StateVariable.Builder> variableBuilderList) {
            mVariableBuilderList = variableBuilderList;
            return this;
        }

        /**
         * Serviceのインスタンスを作成する。
         *
         * @return Serviceのインスタンス
         * @throws IllegalStateException 必須パラメータが設定されていない場合
         */
        @Nonnull
        public Service build() throws IllegalStateException {
            if (mDevice == null) {
                throw new IllegalStateException("device must be set.");
            }
            if (mServiceType == null) {
                throw new IllegalStateException("serviceType must be set.");
            }
            if (mServiceId == null) {
                throw new IllegalStateException("serviceId must be set.");
            }
            if (mScpdUrl == null) {
                throw new IllegalStateException("SCPDURL must be set.");
            }
            if (mControlUrl == null) {
                throw new IllegalStateException("controlURL must be set.");
            }
            if (mEventSubUrl == null) {
                throw new IllegalStateException("eventSubURL must be set.");
            }
            if (mDescription == null) {
                throw new IllegalStateException("description must be set.");
            }
            if (mActionBuilderList == null) {
                mActionBuilderList = Collections.emptyList();
            }
            if (mVariableBuilderList == null) {
                mVariableBuilderList = Collections.emptyList();
            }
            return new Service(this);
        }
    }

    private static final long DEFAULT_SUBSCRIPTION_TIMEOUT = TimeUnit.SECONDS.toMillis(300);
    @Nonnull
    private final ControlPoint mControlPoint;
    @Nonnull
    private final Device mDevice;
    @Nonnull
    private final String mDescription;
    @Nonnull
    private final String mServiceType;
    @Nonnull
    private final String mServiceId;
    @Nonnull
    private final String mScpdUrl;
    @Nonnull
    private final String mControlUrl;
    @Nonnull
    private final String mEventSubUrl;
    @Nullable
    private List<Action> mActionList;
    @Nonnull
    private final Map<String, Action> mActionMap;
    @Nullable
    private List<StateVariable> mStateVariableList;
    @Nonnull
    private final Map<String, StateVariable> mStateVariableMap;
    private long mSubscriptionStart;
    private long mSubscriptionTimeout;
    private long mSubscriptionExpiryTime;
    @Nullable
    private String mSubscriptionId;
    @Nonnull
    private HttpClientFactory mHttpClientFactory = new HttpClientFactory();

    private Service(@Nonnull Builder builder) {
        mDevice = builder.mDevice;
        mControlPoint = mDevice.getControlPoint();
        mServiceType = builder.mServiceType;
        mServiceId = builder.mServiceId;
        mScpdUrl = builder.mScpdUrl;
        mControlUrl = builder.mControlUrl;
        mEventSubUrl = builder.mEventSubUrl;
        mDescription = builder.mDescription;
        mStateVariableMap = new LinkedHashMap<>();
        for (StateVariable.Builder variableBuilder : builder.mVariableBuilderList) {
            final StateVariable variable = variableBuilder.setService(this).build();
            mStateVariableMap.put(variable.getName(), variable);
        }
        mActionMap = new LinkedHashMap<>();
        for (final Action.Builder actionBuilder : builder.mActionBuilderList) {
            for (final Argument.Builder argumentBuilder : actionBuilder.getArgumentBuilderList()) {
                final String name = argumentBuilder.getRelatedStateVariableName();
                final StateVariable variable = mStateVariableMap.get(name);
                if (variable == null) {
                    throw new IllegalArgumentException();
                }
                argumentBuilder.setRelatedStateVariable(variable);
            }
            final Action action = actionBuilder.setService(this).build();
            mActionMap.put(action.getName(), action);
        }
    }

    /**
     * このServiceを保持するDeviceを返す。
     *
     * @return このServiceを保持するDevice
     */
    @Nonnull
    public Device getDevice() {
        return mDevice;
    }

    /**
     * URL関連プロパティの値からURLに変換する。
     *
     * @param url URLプロパティ値
     * @return URLオブジェクト
     * @throws MalformedURLException 不正なURL
     * @see Device#getAbsoluteUrl(String)
     */
    @Nonnull
    URL getAbsoluteUrl(@Nonnull String url) throws MalformedURLException {
        return mDevice.getAbsoluteUrl(url);
    }

    /**
     * serviceTypeを返す。
     *
     * <p>Required. UPnP service type. Shall not contain a hash character (#, 23 Hex in UTF-8). Single URI.
     * <ul>
     * <li>For standard service types defined by a UPnP Forum working committee, shall begin with
     * "urn:schemas-upnp-org:service:" followed by the standardized service type suffix, colon, and an integer service
     * version i.e. urn:schemas-upnp-org:device:serviceType:ver.
     * The highest supported version of the service type shall be specified.
     * <li>For non-standard service types specified by UPnP vendors, shall begin with "urn:", followed by a
     * Vendor Domain Name, followed by ":service:", followed by a service type suffix, colon,
     * and an integer service version, i.e., "urn:domain-name:service:serviceType:ver".
     * Period characters in the Vendor Domain Name shall be replaced with hyphens in accordance with RFC 2141.
     * The highest supported version of the service type shall be specified.
     * </ul>
     * <p>The service type suffix defined by a UPnP Forum working committee or specified by a UPnP vendor shall be
     * &lt;= 64 characters, not counting the version suffix and separating colon.
     *
     * @return serviceType
     */
    @Nonnull
    public String getServiceType() {
        return mServiceType;
    }

    /**
     * serviceIdを返す。
     *
     * <p>Required. Service identifier. Shall be unique within this device description. Single URI.
     * <ul>
     * <li>For standard services defined by a UPnP Forum working committee, shall begin with "urn:upnp-org:serviceId:"
     * followed by a service ID suffix i.e. urn:upnp-org:serviceId:serviceID.
     * If this instance of the specified service type (i.e. the &lt;serviceType&gt; element above) corresponds to one of
     * the services defined by the specified device type (i.e. the &lt;deviceType&gt; element above), then the value of
     * the service ID suffix shall be the service ID defined by the device type for this instance of the service.
     * Otherwise, the value of the service ID suffix is vendor defined. (Note that upnp-org is used instead of
     * schemas-upnp- org in this case because an XML schema is not defined for each service ID.)
     * <li>For non-standard services specified by UPnP vendors, shall begin with “urn:”, followed by a Vendor Domain
     * Name, followed by ":serviceId:", followed by a service ID suffix, i.e., "urn:domain- name:serviceId:serviceID".
     * If this instance of the specified service type (i.e. the &lt;serviceType&gt; element above) corresponds to one of
     * the services defined by the specified device type (i.e. the &lt;deviceType&gt; element above), then the value of
     * the service ID suffix shall be the service ID defined by the device type for this instance of the service.
     * Period characters in the Vendor Domain Name shall be replaced with hyphens in accordance with RFC 2141.
     * </ul>
     * <p>The service ID suffix defined by a UPnP Forum working committee or specified by a UPnP vendor shall be &lt;= 64
     * characters.
     *
     * @return serviceId
     */
    @Nonnull
    public String getServiceId() {
        return mServiceId;
    }

    /**
     * SCPDURLを返す。
     *
     * <p>Required. URL for service description. (See clause 2.5, “Service description” below.) shall be relative to
     * the URL at which the device description is located in accordance with clause 5 of RFC 3986. Specified by
     * UPnP vendor. Single URL.
     *
     * @return SCPDURL
     */
    @Nonnull
    public String getScpdUrl() {
        return mScpdUrl;
    }

    /**
     * controlURLを返す。
     *
     * <p>Required. URL for control (see clause 3, "Control"). shall be relative to the URL at which the device
     * description is located in accordance with clause 5 of RFC 3986. Specified by UPnP vendor. Single URL.
     *
     * @return controlURL
     */
    @Nonnull
    public String getControlUrl() {
        return mControlUrl;
    }

    /**
     * eventSubURLを返す。
     *
     * <p>Required. URL for eventing (see clause 4, "Eventing"). shall be relative to the URL at which the device
     * description is located in accordance with clause 5 of RFC 3986. shall be unique within the device;
     * any two services shall not have the same URL for eventing. If the service has no evented variables,
     * this element shall be present but shall be empty (i.e., &lt;eventSubURL\&gt;&lt;/eventSubURL&gt;.)
     * Specified by UPnP vendor. Single URL.
     *
     * @return eventSubURL
     */
    @Nonnull
    public String getEventSubUrl() {
        return mEventSubUrl;
    }

    /**
     * ServiceDescriptionのXMLを返す。
     *
     * @return ServiceDescription
     */
    @Nonnull
    public String getDescription() {
        return mDescription;
    }

    /**
     * このサービスが保持する全Actionのリストを返す。
     *
     * <p>リストは変更不可。
     *
     * @return 全Actionのリスト
     */
    @Nonnull
    public List<Action> getActionList() {
        if (mActionList == null) {
            final List<Action> list = new ArrayList<>(mActionMap.values());
            mActionList = Collections.unmodifiableList(list);
        }
        return mActionList;
    }

    /**
     * 名前から該当するActionを探す。
     *
     * <p>見つからない場合はnullが返る。
     *
     * @param name Action名
     * @return 該当するAction、見つからない場合null
     */
    @Nullable
    public Action findAction(@Nonnull String name) {
        return mActionMap.get(name);
    }

    /**
     * 全StateVariableのリストを返す。
     *
     * @return 全StateVariableのリスト
     */
    @Nonnull
    public List<StateVariable> getStateVariableList() {
        if (mStateVariableList == null) {
            final List<StateVariable> list = new ArrayList<>(mStateVariableMap.values());
            mStateVariableList = Collections.unmodifiableList(list);
        }
        return mStateVariableList;
    }

    /**
     * 名前から該当するStateVariableを探す。
     *
     * <p>見つからない場合はnullが返る。
     *
     * @param name StateVariable名
     * @return 該当するStateVariable、見つからない場合null
     */
    @Nullable
    public StateVariable findStateVariable(@Nullable String name) {
        return mStateVariableMap.get(name);
    }

    @Nonnull
    private String getCallback() {
        final StringBuilder sb = new StringBuilder();
        sb.append("<http://");
        final SsdpMessage ssdp = mDevice.getSsdpMessage();
        final InterfaceAddress ifa = ssdp.getInterfaceAddress();
        //noinspection ConstantConditions : 受信したデータの場合はnullではない
        sb.append(ifa.getAddress().getHostAddress());
        final int port = mControlPoint.getEventPort();
        if (port != Http.DEFAULT_PORT) {
            sb.append(':');
            sb.append(String.valueOf(port));
        }
        sb.append("/>");
        return sb.toString();
    }

    private static long parseTimeout(@Nonnull HttpResponse response) {
        final String timeout = TextUtils.toLowerCase(response.getHeader(Http.TIMEOUT));
        if (TextUtils.isEmpty(timeout) || timeout.contains("infinite")) {
            // infiniteはUPnP2.0でdeprecated扱い、有限な値にする。
            return DEFAULT_SUBSCRIPTION_TIMEOUT;
        }
        final String prefix = "second-";
        final int pos = timeout.indexOf(prefix);
        if (pos < 0) {
            return DEFAULT_SUBSCRIPTION_TIMEOUT;
        }
        final String secondSection = timeout.substring(pos + prefix.length());
        try {
            final int second = Integer.parseInt(secondSection);
            return TimeUnit.SECONDS.toMillis(second);
        } catch (final NumberFormatException e) {
            Log.w(TAG, e);
        }
        return DEFAULT_SUBSCRIPTION_TIMEOUT;
    }

    /**
     * HttpClientのファクトリークラスを変更する。
     *
     * @param factory ファクトリークラス
     */
    void setHttpClientFactory(@Nonnull HttpClientFactory factory) {
        mHttpClientFactory = factory;
    }

    @Nonnull
    private HttpClient createHttpClient() {
        return mHttpClientFactory.createHttpClient(false);
    }

    /**
     * Subscribeの実行
     *
     * @return 成功時true
     * @throws IOException 通信エラー
     */
    public boolean subscribe() throws IOException {
        return subscribe(false);
    }

    /**
     * Subscribeの実行
     *
     * @param keepRenew trueを指定すると成功後、Expire前に定期的にrenewを行う。
     * @return 成功時true
     * @throws IOException 通信エラー
     */
    public boolean subscribe(boolean keepRenew) throws IOException {
        if (TextUtils.isEmpty(mEventSubUrl)) {
            return false;
        }
        if (!TextUtils.isEmpty(mSubscriptionId)) {
            if (renewSubscribeInner()) {
                mControlPoint.registerSubscribeService(this, keepRenew);
                return true;
            }
            return false;
        }
        return subscribeInner(keepRenew);
    }

    private boolean subscribeInner(boolean keepRenew) throws IOException {
        final HttpClient client = createHttpClient();
        final HttpRequest request = makeSubscribeRequest();
        final HttpResponse response = client.post(request);
        if (response.getStatus() != Http.Status.HTTP_OK) {
            Log.w(TAG, "subscribe request:" + request.toString() + "\nresponse:" + response.toString());
            return false;
        }
        if (parseSubscribeResponse(response)) {
            mControlPoint.registerSubscribeService(this, keepRenew);
            return true;
        }
        return false;
    }

    private boolean parseSubscribeResponse(@Nonnull HttpResponse response) {
        final String sid = response.getHeader(Http.SID);
        final long timeout = parseTimeout(response);
        if (TextUtils.isEmpty(sid) || timeout == 0) {
            Log.w(TAG, "subscribe response:" + response.toString());
            return false;
        }
        mSubscriptionId = sid;
        mSubscriptionStart = System.currentTimeMillis();
        mSubscriptionTimeout = timeout;
        mSubscriptionExpiryTime = mSubscriptionStart + mSubscriptionTimeout;
        return true;
    }

    @Nonnull
    private HttpRequest makeSubscribeRequest() throws IOException {
        final HttpRequest request = new HttpRequest();
        request.setMethod(Http.SUBSCRIBE);
        request.setUrl(getAbsoluteUrl(mEventSubUrl), true);
        request.setHeader(Http.NT, Http.UPNP_EVENT);
        request.setHeader(Http.CALLBACK, getCallback());
        request.setHeader(Http.TIMEOUT, "Second-300");
        request.setHeader(Http.CONTENT_LENGTH, "0");
        return request;
    }

    /**
     * RenewSubscribeを実行する
     *
     * @return 成功時true
     * @throws IOException 通信エラー
     */
    boolean renewSubscribe() throws IOException {
        if (TextUtils.isEmpty(mEventSubUrl)) {
            return false;
        }
        if (TextUtils.isEmpty(mSubscriptionId)) {
            return subscribeInner(false);
        }
        return renewSubscribeInner();
    }

    private boolean renewSubscribeInner() throws IOException {
        final HttpClient client = createHttpClient();
        //noinspection ConstantConditions
        final HttpRequest request = makeRenewSubscribeRequest(mSubscriptionId);
        final HttpResponse response = client.post(request);
        if (response.getStatus() != Http.Status.HTTP_OK) {
            Log.w(TAG, "renewSubscribe request:" + request.toString() + "\nresponse:" + response.toString());
            return false;
        }
        return parseRenewSubscribeResponse(response);
    }

    private boolean parseRenewSubscribeResponse(@Nonnull HttpResponse response) {
        final String sid = response.getHeader(Http.SID);
        final long timeout = parseTimeout(response);
        if (!TextUtils.equals(sid, mSubscriptionId) || timeout == 0) {
            Log.w(TAG, "renewSubscribe response:" + response.toString());
            return false;
        }
        mSubscriptionStart = System.currentTimeMillis();
        mSubscriptionTimeout = timeout;
        mSubscriptionExpiryTime = mSubscriptionStart + mSubscriptionTimeout;
        return true;
    }

    @Nonnull
    private HttpRequest makeRenewSubscribeRequest(@Nonnull String subscriptionId) throws IOException {
        final HttpRequest request = new HttpRequest();
        request.setMethod(Http.SUBSCRIBE);
        request.setUrl(getAbsoluteUrl(mEventSubUrl), true);
        request.setHeader(Http.SID, subscriptionId);
        request.setHeader(Http.TIMEOUT, "Second-300");
        request.setHeader(Http.CONTENT_LENGTH, "0");
        return request;
    }

    /**
     * Unsubscribeを実行する
     *
     * @return 成功時true
     * @throws IOException 通信エラー
     */
    public boolean unsubscribe() throws IOException {
        if (TextUtils.isEmpty(mEventSubUrl) || TextUtils.isEmpty(mSubscriptionId)) {
            return false;
        }
        final HttpClient client = new HttpClient(false);
        final HttpRequest request = makeUnsubscribeRequest(mSubscriptionId);
        final HttpResponse response = client.post(request);
        if (response.getStatus() != Http.Status.HTTP_OK) {
            Log.w(TAG, "unsubscribe request:" + request.toString() + "\nresponse:" + response.toString());
            return false;
        }
        mControlPoint.unregisterSubscribeService(this);
        mSubscriptionId = null;
        mSubscriptionStart = 0;
        mSubscriptionTimeout = 0;
        mSubscriptionExpiryTime = 0;
        return true;
    }

    private HttpRequest makeUnsubscribeRequest(@Nonnull String subscriptionId) throws IOException {
        final HttpRequest request = new HttpRequest();
        request.setMethod(Http.UNSUBSCRIBE);
        request.setUrl(getAbsoluteUrl(mEventSubUrl), true);
        request.setHeader(Http.SID, subscriptionId);
        request.setHeader(Http.CONTENT_LENGTH, "0");
        return request;
    }

    /**
     * Subscribeの期限切れ通知
     */
    void expired() {
        mSubscriptionId = null;
        mSubscriptionStart = 0;
        mSubscriptionTimeout = 0;
        mSubscriptionExpiryTime = 0;
    }

    /**
     * SID(SubscriptionID)を返す。
     *
     * @return SubscriptionID
     */
    @Nullable
    public String getSubscriptionId() {
        return mSubscriptionId;
    }

    /**
     * Subscriptionの開始時刻
     *
     * @return Subscriptionの開始時刻
     */
    public long getSubscriptionStart() {
        return mSubscriptionStart;
    }

    /**
     * Subscriptionの有効期間
     *
     * @return Subscriptionの有効期間
     */
    public long getSubscriptionTimeout() {
        return mSubscriptionTimeout;
    }

    /**
     * Subscriptionの有効期限
     *
     * @return Subscriptionの有効期限
     */
    public long getSubscriptionExpiryTime() {
        return mSubscriptionExpiryTime;
    }

    @Override
    public int hashCode() {
        return mDevice.hashCode() + mServiceId.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof Service)) {
            return false;
        }
        Service service = (Service) obj;
        return mDevice.equals(service.getDevice()) && mServiceId.equals(service.getServiceId());
    }
}
