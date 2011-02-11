(ns clj-httpc.core
  "Core HTTP request/response implementation."
  (:use
    [clj-httpc.util])
  (:require
    [clj-httpc.content :as content]
    [clojure.contrib.logging :as log])
  (:import
    [clj_httpc EntityUtils LoggingRedirectStrategy]
    [java.io InterruptedIOException]
    [java.net SocketException UnknownHostException]
    [org.apache.http HttpEntityEnclosingRequest HttpResponse Header]
    [org.apache.http.client ClientProtocolException HttpClient]
    [org.apache.http.client.methods HttpUriRequest]
    [org.apache.http.entity ByteArrayEntity]
    [org.apache.http.impl.client DefaultHttpClient]
    [org.apache.http.protocol HttpContext BasicHttpContext]))

(def #^HttpClient *http-client* (DefaultHttpClient.))

(defn- parse-headers [#^HttpResponse http-resp]
  "Parse headers from a hash."
  (into {} (map (fn [#^Header h] [(.toLowerCase (.getName h)) (.getValue h)])
                (iterator-seq (.headerIterator http-resp)))))

(defn- parse-redirects
  "Gets the redirects from the LoggingRedirectStrategy."
  [redirect-strategy]
  (if (= (type redirect-strategy) LoggingRedirectStrategy)
    (into #{} (.getURIs redirect-strategy))
    nil))

(defn- abort-request?
  "Aborts the request if content types don't match or if the content length is
  too long."
  [request-method headers #^HttpResponse http-resp http-params]
  (let [length (get http-params content/limit)]
    (and (= request-method :get)
         (or (nil? (.getEntity http-resp))
             (not (content/matches-acceptable? headers http-resp http-params))
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
        redirect-strategy (.getRedirectStrategy *http-client*)
        resp (create-http-response http-url)]
    (try
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
        (.. *http-client*
          (getParams)
          (setParameter (first param) (last param))))
      ; Check for a request body
      (if body
        (let [http-body (ByteArrayEntity. body)]
          (.setEntity #^HttpEntityEnclosingRequest http-req http-body)))
      ; Execute the request
      (let [http-resp #^HttpResponse (.execute *http-client*
                                               http-req
                                               http-context)
            http-entity (.getEntity http-resp)
            limit (get http-params content/limit)
            abort? (abort-request? request-method headers http-resp http-params)
            body (if abort?
                   (.abort http-req)
                   (if http-entity (EntityUtils/toByteArray http-entity limit)))
            status (if abort? nil (.getStatusCode (.getStatusLine http-resp)))]
        (assoc (timestamp resp)
               :status status
               :headers (parse-headers http-resp)
               :redirects (parse-redirects redirect-strategy)
               :exception (if abort?
                            (InterruptedIOException. "Request aborted."))
               :body body))
      (catch UnknownHostException e
        (create-error-response resp (parse-redirects redirect-strategy) e))
      (catch SocketException e
        (create-error-response resp (parse-redirects redirect-strategy) e
                               :status 408))
      (catch InterruptedIOException e
        (create-error-response resp (parse-redirects redirect-strategy) e))
      (catch ClientProtocolException e
        ; ClientProtocolException wraps other exceptions.  The String version of
        ; the constructor is rarely used, so giving the user back the cause of
        ; the exception is usually more useful.
        (assoc (create-error-response resp (parse-redirects redirect-strategy) e)
               :exception (if (.getCause e) (.getCause e) e)))
      (catch Exception e
        (create-error-response resp (parse-redirects redirect-strategy) e))
      (finally
        ; It is harmless to abort a request that has completed, and in some
        ; cases will be required to release resources.  However, abort could
        ; stand to be placed right after those situations:
        ; See http://hc.apache.org/httpcomponents-client-ga/tutorial/html/fundamentals.html#d4e143
        (.abort http-req)))))

(defn with-http-client
  "Evaluates a function with *http-client* bound to http-client."
  [http-client f]
  (binding [*http-client* http-client]
    (f)))
