(ns clj-httpc.content-test
  (:use [clj-httpc.content]
        [clojure.test])
  (:require [clj-httpc.content :as content])
  (:import [com.google.gdata.util ContentType]
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
  [type content]
  (doto (BasicHttpEntity.)
    (.setContent content)
    (.setContentType type)))

(deftest gets-content-type
  (let [entity (create-entity "text/html" nil)
        resp (create-response 200 entity)
        content-type (get-type resp)
        accepted (ContentType/TEXT_HTML)]
    (is (.match content-type accepted))))

(deftest parses-accept-headers
  (do
    (let [headers {}]
      (is (parse-accept headers) [(ContentType. "*/*")]))
    (let [headers {"Accept" (str "multipart/mixed; boundary=\"frontier\","
                                 "text/*;q=0.3,"
                                 "text/html;q=0.7,"
                                 "text/html;level=1,"
                                 "text/html;level=2;q=0.4,"
                                 "*/*;q=0.5")}]
      (is (parse-accept headers)
          [(ContentType. "multipart/mixed; boundary=\"frontier\"")
           (ContentType. "text/*;q=0.3,")
           (ContentType. "text/html;q=0.7,")
           (ContentType. "text/html;level=1,")
           (ContentType. "text/html;level=2;q=0.4,")
           (ContentType. "*/*;q=0.5")])
    (let [headers {"Accept" "text/*"}]
      (is (parse-accept headers) [(ContentType. "text/*")])))))

(deftest matches-acceptable-content-types
  (do
    (let [acceptable (map #(ContentType. %)
                          ["text/html" "application/json" "text/plain"])
          content-type (ContentType. "application/json")]
      (is (matches? acceptable content-type)))
    (let [acceptable (map #(ContentType. %) ["text/*"])
          content-type (ContentType. "text/xml")]
      (is (matches? acceptable content-type)))
    (let [acceptable []
          content-type (ContentType. "application/json")]
      (is (not (matches? acceptable content-type))))))
