(ns clj-httpc.util
  "Helper functions for the HTTP client."
  (:require
    [clojure.contrib.logging :as log])
  (:import
    [clj_httpc TrustEveryoneSSLSocketFactory]
    [org.apache.commons.codec.binary Base64]
    [java.net URLEncoder]
    [java.io ByteArrayInputStream]
    [java.io ByteArrayOutputStream]
    [java.util.zip InflaterInputStream]
    [java.util.zip DeflaterInputStream]
    [java.util.zip GZIPInputStream]
    [java.util.zip GZIPOutputStream]
    [org.apache.commons.io IOUtils]
    [org.apache.http.params BasicHttpParams]
    [org.apache.http HttpVersion]
    [org.apache.http.client.methods HttpGet]
    [org.apache.http.client.methods HttpPut]
    [org.apache.http.client.methods HttpPost]
    [org.apache.http.client.methods HttpDelete]
    [org.apache.http.client.methods HttpHead]
    [org.apache.http.client.params CookiePolicy]
    [org.apache.http.client.params HttpClientParams]
    [org.apache.http.conn.params ConnManagerParams]
    [org.apache.http.conn.scheme PlainSocketFactory]
    [org.apache.http.conn.scheme Scheme]
    [org.apache.http.conn.scheme SchemeRegistry]
    [org.apache.http.conn.ssl SSLSocketFactory]
    [org.apache.http.impl.client DefaultHttpClient]
    [org.apache.http.impl.conn.tsccm ThreadSafeClientConnManager]
    [org.apache.http.params BasicHttpParams]
    [org.apache.http.params HttpProtocolParams]
    [org.apache.http.params HttpConnectionParams]
    [org.apache.http.protocol HttpContext]
    [org.apache.http.protocol BasicHttpContext]
    [org.apache.http.protocol HTTP]))

(defn utf8-bytes
  "Returns the UTF-8 bytes corresponding to the given string."
  [#^String s]
  (.getBytes s "UTF-8"))

(defn utf8-string
  "Returns the String corresponding to the UTF-8 decoding of the given bytes."
  [#^"[B" b]
  (String. b "UTF-8"))

(defn url-encode
  "Returns an UTF-8 URL encoded version of the given string."
  [unencoded]
  (URLEncoder/encode unencoded "UTF-8"))

(defn base64-encode
  "Encode an array of bytes into a base64 encoded string."
  [unencoded]
  (utf8-string (Base64/encodeBase64 unencoded)))

(defn gunzip
  "Returns a gunzip'd version of the given byte array."
  [b]
  (IOUtils/toByteArray (GZIPInputStream. (ByteArrayInputStream. b))))

(defn gzip
  "Returns a gzip'd version of the given byte array."
  [b]
  (let [baos (ByteArrayOutputStream.)
        gos  (GZIPOutputStream. baos)]
    (IOUtils/copy (ByteArrayInputStream. b) gos)
    (.close gos)
    (.toByteArray baos)))

(defn inflate
  "Returns a zlib inflate'd version of the given byte array."
  [b]
  (IOUtils/toByteArray (InflaterInputStream. (ByteArrayInputStream. b))))

(defn deflate
  "Returns a deflate'd version of the given byte array."
  [b]
  (IOUtils/toByteArray (DeflaterInputStream. (ByteArrayInputStream. b))))

(defn timestamp
  "Adds timestamps to response hash."
  [resp]
  (let [start (resp :start-time)
        end (System/currentTimeMillis)]
    (assoc resp
           :end-time end
           :time (- end start))))

(defn create-http-response
  "Create a basic http response map from a url."
  [url]
  {:url url
   :start-time (System/currentTimeMillis)
   :status nil
   :headers nil
   :body nil
   :redirects #{}
   :exception nil})

(defn create-http-params
  "A better way to get your default params (without jar introspection).

  For an explanation of each parameter:
  See <http://hc.apache.org/httpcomponents-client-ga/httpclient/apidocs/org/apache/http/client/params/ClientPNames.html>
  See <http://hc.apache.org/httpcomponents-core-ga/httpcore/apidocs/org/apache/http/params/CoreConnectionPNames.html>
  See <http://hc.apache.org/httpcomponents-core-ga/httpcore/apidocs/org/apache/http/params/CoreProtocolPNames.html>

  For information on setting each parameter:
  See <http://hc.apache.org/httpcomponents-client-ga/httpclient/apidocs/org/apache/http/client/params/HttpClientParams.html>
  See <http://hc.apache.org/httpcomponents-core-ga/httpcore/apidocs/org/apache/http/params/HttpConnectionParams.html>
  See <http://hc.apache.org/httpcomponents-core-ga/httpcore/apidocs/org/apache/http/params/HttpProtocolParams.html>"
  []
  (doto (BasicHttpParams.)
    (HttpProtocolParams/setUserAgent "clj-httpc")
    (HttpProtocolParams/setVersion HttpVersion/HTTP_1_1)
    (HttpProtocolParams/setContentCharset HTTP/DEFAULT_CONTENT_CHARSET)
    (HttpConnectionParams/setSocketBufferSize 8192)
    (HttpClientParams/setCookiePolicy CookiePolicy/BROWSER_COMPATIBILITY)
    ; Do not use Expect: 100-Continue handshake because we normally do not send
    ; a request body.
    (HttpProtocolParams/setUseExpectContinue false)
    ; Disable Nagle's algorithm; it is useful only if we intend to transmit a
    ; lot of small packets of data.
    (HttpConnectionParams/setTcpNoDelay true)
    ; According to the docs, this check can add up to 30 ms overhead per request
    ; and should be disabled for performance critical applications.
    (HttpConnectionParams/setStaleCheckingEnabled false)
    ; Set a timeout for connecting and waiting for data.
    (HttpConnectionParams/setConnectionTimeout 10000)
    (HttpConnectionParams/setSoTimeout 10000)
    ; Tweak this to be the number of fetchers; we want a sustained 600 fetches
    ; per second, so here's hoping.
    (ConnManagerParams/setMaxTotalConnections 600)))

(defn create-scheme-registry
  "Support the http and https schemes."
  []
  (let [http (Scheme. "http" (PlainSocketFactory/getSocketFactory) 80)
        https (Scheme. "https" (SSLSocketFactory/getSocketFactory) 443)]
    (doto (SchemeRegistry.)
      (.register http)
      (.register https))))

(defn create-http-client
  "Create an http-client."
  ([http-params scheme-registry]
    (let [manager (ThreadSafeClientConnManager. http-params scheme-registry)]
      (DefaultHttpClient. manager http-params)))
  ([]
    (create-http-client (create-http-params) (create-scheme-registry))))

(defn create-http-url
  "Create the URI as a String."
  [scheme server-name server-port query-string uri]
  (str scheme "://" server-name
       (if server-port (str ":" server-port))
       uri
       (if query-string (str "?" query-string))))

(defn create-http-request
  "Create the HTTP request based on the method."
  [request-method #^String http-url]
  (case request-method
    :get    (HttpGet. http-url)
    :head   (HttpHead. http-url)
    :put    (HttpPut. http-url)
    :post   (HttpPost. http-url)
    :delete (HttpDelete. http-url)))

(defn create-error-response
  "Create an error response to return in case of an exception."
  [resp redirects exception & {:keys [log-fn status] :or {log-fn #(log/info %)}}]
  (let [r (assoc (timestamp resp)
                 :exception exception
                 :redirects redirects
                 :status (if status status (resp :status)))]
    (log-fn r)
    r))
