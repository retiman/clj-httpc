(ns clj-httpc.core
  "Core HTTP request/response implementation."
  (:refer-clojure :exclude (time))
  (:require
    [clj-httpc.content :as content]
    [clojure.contrib.logging :as log])
  (:import
    [clj_httpc EntityUtils]
    [clj_httpc LoggingRedirectHandler]
    [java.io InterruptedIOException]
    [java.net SocketException]
    [java.net UnknownHostException]
    [org.apache.http HttpEntityEnclosingRequest]
    [org.apache.http HttpResponse]
    [org.apache.http HttpVersion]
    [org.apache.http Header]
    [org.apache.http.client HttpClient]
    [org.apache.http.client.methods HttpGet]
    [org.apache.http.client.methods HttpPut]
    [org.apache.http.client.methods HttpPost]
    [org.apache.http.client.methods HttpDelete]
    [org.apache.http.client.methods HttpHead]
    [org.apache.http.client.methods HttpUriRequest]
    [org.apache.http.client.params CookiePolicy]
    [org.apache.http.client.params HttpClientParams]
    [org.apache.http.conn.params ConnManagerParams]
    [org.apache.http.conn.scheme PlainSocketFactory]
    [org.apache.http.conn.scheme Scheme]
    [org.apache.http.conn.scheme SchemeRegistry]
    [org.apache.http.conn.ssl SSLSocketFactory]
    [org.apache.http.entity ByteArrayEntity]
    [org.apache.http.impl.client DefaultHttpClient]
    [org.apache.http.impl.conn.tsccm ThreadSafeClientConnManager]
    [org.apache.http.params BasicHttpParams]
    [org.apache.http.params HttpProtocolParams]
    [org.apache.http.params HttpConnectionParams]
    [org.apache.http.protocol HttpContext]
    [org.apache.http.protocol BasicHttpContext]
    [org.apache.http.protocol HTTP]))

(defn create-http-response
  "Create a basic http response map from a uri.  A 0 (zero) status indicates
  that some sort of error unrelated to the HTTP spec has occurred."
  [uri]
  {:uri uri
   :url uri
   :time (System/currentTimeMillis)
   :status 0
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

(def #^HttpClient *http-client* (DefaultHttpClient.))

(defn- parse-headers [#^HttpResponse http-resp]
  "Parse headers from a hash."
  (into {} (map (fn [#^Header h] [(.toLowerCase (.getName h)) (.getValue h)])
                (iterator-seq (.headerIterator http-resp)))))

(defn- abort-request?
  "Aborts the request if content types don't match or if the content length is
  too long."
  [request-method headers #^HttpResponse http-resp http-params]
  (let [length (get http-params content/limit)]
    (and (= request-method :get)
         (or (nil? (.getEntity http-resp))
             (not (content/matches-acceptable? headers http-resp))
             (content/over-limit? http-resp length)))))

(defn- create-error-response
  "Create an error response to return in case of an exception."
  [http-req http-resp {:keys [exception log-fn status abort?]
                       :or {log-fn #(log/info %) abort? false}}]
  (let [error-status (if status status (http-resp :status))
        error-resp (assoc http-resp :exception exception :status error-status)]
    (log-fn error-resp)
    (if abort? (.abort http-req))
    error-resp))

(defn shutdown
  "Add a shutdown hook to shutdown the connection manager before your
  application exits."
  []
  (.. *http-client*
    (getConnectionManager)
    (shutdown)))

(defn request
  "Executes the HTTP request corresponding to the given Ring request map and
  returns the Ring response map corresponding to the resulting HTTP response.

  Note that where Ring uses InputStreams for the request and response bodies,
  the clj-httpc uses ByteArrays for the bodies."
  [{:keys [request-method scheme server-name server-port uri query-string
           headers content-type character-encoding http-params body]}]
  (let [http-url (create-http-url scheme
                                  server-name
                                  server-port
                                  query-string
                                  uri)
        http-context #^HttpContext (BasicHttpContext.)
        http-req #^HttpUriRequest (create-http-request request-method http-url)
        redirect-handler (LoggingRedirectHandler.)
        resp (create-http-response http-url)]
    (try
      ; Set the redirect handler
      (.setRedirectHandler *http-client* redirect-handler)
      ; Add content-type and character encoding
      (if (and content-type character-encoding)
        (.addHeader http-req "Content-Type"
                    (str content-type "; charset=" character-encoding)))
      (if (and content-type (not character-encoding))
        (.addHeader http-req "Content-Type" content-type))
      (.addHeader http-req "Connection" "close")
      ; Add user specified headers
      (doseq [header headers]
        (.addHeader http-req (first header) (last header)))
      ; Add user specified parameters
      (doseq [param http-params]
        (.. *http-client* (getParams) (setParameter (first param) (last param))))
      ; Check for a request body
      (if body
        (let [http-body (ByteArrayEntity. body)]
          (.setEntity #^HttpEntityEnclosingRequest http-req http-body)))
      ; Execute the request
      (let [http-resp #^HttpResponse (.execute *http-client* http-req http-context)
            http-entity (.getEntity http-resp)
            limit (get http-params content/limit)
            aborted? (abort-request? request-method headers http-resp http-params)
            body (if aborted?
                   (.abort http-req)
                   (if http-entity (EntityUtils/toByteArray http-entity limit)))]
        (assoc resp :status (if aborted?
                              0
                              (.getStatusCode (.getStatusLine http-resp)))
                    :headers (parse-headers http-resp)
                    :redirects (into #{} (.getURIs redirect-handler))
                    :body body))
      (catch UnknownHostException e
        (create-error-response http-req resp {:exception e}))
      (catch SocketException e
        (create-error-response http-req resp {:exception e :status 408 :abort? true}))
      (catch InterruptedIOException e
        (create-error-response http-req resp {:exception e :abort? true}))
      (catch Exception e
        (create-error-response http-req resp {:exception e
                                              :abort? true
                                              :log-fn #(log/error %)})))))

(defn with-http-client
  "Evaluates a function with *http-client* bound to http-client."
  [http-client f]
  (binding [*http-client* http-client]
    (f)))
