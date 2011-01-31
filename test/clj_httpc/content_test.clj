(ns clj-httpc.content-test
  (:use
    [clojure.test])
  (:require
    [clj-httpc.content :as content])
  (:import
    [com.google.gdata.util ContentType]
    [org.apache.http ProtocolVersion]
    [org.apache.http.entity BasicHttpEntity]
    [org.apache.http.impl DefaultHttpResponseFactory]
    [org.apache.http.protocol BasicHttpContext]))

(defn- create-response
  [code entity]
  (let [factory (DefaultHttpResponseFactory.)
        protocol (ProtocolVersion. "HTTP" 1 1)
        context (BasicHttpContext.)]
    (doto (.newHttpResponse factory protocol code context)
      (.setEntity entity))))

(defn- create-entity
  [type content length]
  (doto (BasicHttpEntity.)
    (.setContent content)
    (.setContentType type)
    (.setContentLength length)))

(deftest gets-content-type
  (let [entity (create-entity "text/html" nil 0)
        resp (create-response 200 entity)
        content-type (content/get-type resp)
        accepted (ContentType/TEXT_HTML)]
    (is (.match content-type accepted))))

(deftest parses-accept-headers
  (do
    (let [headers {}]
      (is (content/parse-accept headers) [(ContentType. "*/*")]))
    (let [headers {"Accept" (str "multipart/mixed; boundary=\"frontier\","
                                 "text/*;q=0.3,"
                                 "text/html;q=0.7,"
                                 "text/html;level=1,"
                                 "text/html;level=2;q=0.4,"
                                 "*/*;q=0.5")}]
      (is (content/parse-accept headers)
          [(ContentType. "multipart/mixed; boundary=\"frontier\"")
           (ContentType. "text/*;q=0.3,")
           (ContentType. "text/html;q=0.7,")
           (ContentType. "text/html;level=1,")
           (ContentType. "text/html;level=2;q=0.4,")
           (ContentType. "*/*;q=0.5")])
    (let [headers {"Accept" "text/*"}]
      (is (content/parse-accept headers) [(ContentType. "text/*")])))))

(deftest matches-acceptable-content-types
  (do
    (let [acceptable (map #(ContentType. %)
                          ["text/html" "application/json" "text/plain"])
          content-type (ContentType. "application/json")]
      (is (content/matches? acceptable content-type)))
    (let [acceptable (map #(ContentType. %) ["text/*"])
          content-type (ContentType. "text/xml")]
      (is (content/matches? acceptable content-type)))
   (let [acceptable (map #(ContentType. %) ["text/*;q=0.7"])
          content-type (ContentType. "text/html")]
      (is (content/matches? acceptable content-type)))
    (let [acceptable []
          content-type (ContentType. "application/json")]
      (is (not (content/matches? acceptable content-type))))))

(comment

  (matches-acceptable-content-types)

  )

(deftest reports-over-limit?
  (do
    (let [entity (create-entity "text/html" nil 1000)
          resp (create-response 200 entity)
          limit 1000]
      (is (not (content/over-limit? resp limit))))
    (let [entity (create-entity "text/html" nil 1000)
          resp (create-response 200 entity)
          limit 100]
      (is (content/over-limit? resp limit)))))
