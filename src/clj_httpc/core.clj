(ns clj-httpc.core
  "Core HTTP request/response implementation."
  (:refer-clojure :exclude (time))
  (:use
    [clj-httpc.util])
  (:require
    [clj-httpc.content :as content]
    [clojure.contrib.logging :as log])
  (:import
    [clj_httpc EntityUtils]
    [clj_httpc LoggingRedirectHandler]
    [clj_httpc TrustEveryoneSSLSocketFactory]
    [java.io InterruptedIOException]
    [java.net SocketException]
    [java.net UnknownHostException]
    [org.apache.http HttpEntityEnclosingRequest]
    [org.apache.http HttpResponse]
    [org.apache.http HttpVersion]
    [org.apache.http Header]
    [org.apache.http.client HttpClient ClientProtocolException]
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
                    (str content-type "; charset=" character-encoding))) ;; this is causing problems
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
            abort? (abort-request? request-method headers http-resp http-params)
            body (if abort?
                   (.abort http-req)
                   (if http-entity (EntityUtils/toByteArray http-entity limit)))
            status (if abort? 0 (.getStatusCode (.getStatusLine http-resp)))]
        (assoc resp
               :status status
               :headers (parse-headers http-resp)
               :redirects (into #{} (.getURIs redirect-handler))
               :body body))
      (catch UnknownHostException e
        (create-error-response http-req resp {:exception e}))
      (catch SocketException e
        (create-error-response http-req resp {:exception e :status 408}))
      (catch InterruptedIOException e
        (create-error-response http-req resp {:exception e}))
      (catch ClientProtocolException e
        (assoc (create-error-response http-req resp {:exception e})
               :redirects (into #{} (.getURIs redirect-handler))))
      (catch Exception e
        (create-error-response http-req resp {:exception e}))
      (finally
        ; It is harmless to abort a request that has completed, and in some cases will
        ; be required to release resources.  However, abort could stand to be placed
        ; right after those situations:
        ; See http://hc.apache.org/httpcomponents-client-ga/tutorial/html/fundamentals.html#d4e143
        (.abort http-req)))))

(defn with-http-client
  "Evaluates a function with *http-client* bound to http-client."
  [http-client f]
  (binding [*http-client* http-client]
    (f)))
