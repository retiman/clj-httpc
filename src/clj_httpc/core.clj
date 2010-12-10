(ns clj-httpc.core
  "Core HTTP request/response implementation."
  (:require [clj-httpc.content :as content])
  (:import (clj_http LoggingRedirectHandler))
  (:import (java.net SocketException))
  (:import (org.apache.http HttpRequest HttpEntityEnclosingRequest HttpResponse Header))
  (:import (org.apache.http.util EntityUtils))
  (:import (org.apache.http.entity ByteArrayEntity))
  (:import (org.apache.http.client.methods HttpGet HttpHead HttpPut HttpPost HttpDelete))
  (:import (org.apache.http.impl.client DefaultHttpClient)))

(defn- parse-headers [#^HttpResponse http-resp]
  (into {} (map (fn [#^Header h] [(.toLowerCase (.getName h)) (.getValue h)])
                (iterator-seq (.headerIterator http-resp)))))

(defn- acceptable-content?
  "Returns true if the response's Content-Type matches any of the Accept
  headers."
  [headers resp]
  (let [acceptable-types (content/parse-accept headers)
        content-type (content/get-type resp)]
    (content/matches? acceptable-types content-type)))

(defn request
  "Executes the HTTP request corresponding to the given Ring request map and
   returns the Ring response map corresponding to the resulting HTTP response.

   Note that where Ring uses InputStreams for the request and response bodies,
   the clj-httpc uses ByteArrays for the bodies."
  [{:keys [request-method scheme server-name server-port uri query-string
           headers content-type character-encoding http-params body]}]
  (let [http-client (DefaultHttpClient.)
        redirect-handler (LoggingRedirectHandler.)]
    (try
      (.setRedirectHandler http-client redirect-handler)
      (doseq [param http-params]
        (.. http-client (getParams) (setParameter (first param) (last param))))
      (let [http-url (str scheme "://" server-name
                          (if server-port (str ":" server-port))
                          uri
                          (if query-string (str "?" query-string)))
            #^HttpRequest
              http-req (case request-method
                         :get    (HttpGet. http-url)
                         :head   (HttpHead. http-url)
                         :put    (HttpPut. http-url)
                         :post   (HttpPost. http-url)
                         :delete (HttpDelete. http-url))]
        (if (and content-type character-encoding)
          (.addHeader http-req "Content-Type"
                      (str content-type "; charset=" character-encoding)))
        (if (and content-type (not character-encoding))
          (.addHeader http-req "Content-Type" content-type))
        (.addHeader http-req "Connection" "close")
        (doseq [[header-n header-v] headers]
          (.addHeader http-req header-n header-v))
        (if body
          (let [http-body (ByteArrayEntity. body)]
            (.setEntity #^HttpEntityEnclosingRequest http-req http-body)))
        (let [http-resp (.execute http-client http-req)
              http-entity (.getEntity http-resp)
              body (if (and http-entity (acceptable-content? headers http-resp))
                     (EntityUtils/toByteArray http-entity)
                     (try
                       (.abort http-req)
                       (catch SocketException e nil)))
              resp {:status (.getStatusCode (.getStatusLine http-resp))
                    :headers (parse-headers http-resp)
                    :body body
                    :redirect-uris (into #{} (.getURIs redirect-handler))}]
          (.shutdown (.getConnectionManager http-client))
          resp)))))
