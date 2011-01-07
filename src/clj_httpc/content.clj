(ns clj-httpc.content
  "Utilities for content type handling."
  (:require
    [clojure.contrib.str-utils2 :as su])
  (:import
    [org.apache.http HttpResponse]
    [com.google.gdata.util ContentType]))

(def limit "clj-httpc.content-length-limit")

(defn get-type
  "Return the ContentType of the HTTP response body."
  [#^HttpResponse resp]
  (let [content-type (.. resp (getEntity) (getContentType))]
    (if (nil? content-type)
      nil
      (ContentType. (.getValue content-type)))))

(defn parse-accept
  "Returns a list of ContentTypes parsed from the Accept header."
  [headers]
  (if (contains? headers "Accept")
    (let [accept-value (get headers "Accept")
          content-types (su/split accept-value #",")]
      (map #(ContentType. (su/trim %)) content-types))
    (list (ContentType. "*/*"))))

(defn matches?
  "Returns true if the supplied ContentType matches one of the acceptable
  ContentTypes, or if the supplied ContentType is nil."
  [acceptable-types #^ContentType content-type]
  (if (nil? content-type)
    true
    (some #(.match content-type %) acceptable-types)))

(defn matches-acceptable?
  "Returns true if the response's Content-Type matches any of the Accept
  headers."
  [headers resp]
  (let [acceptable-types (parse-accept headers)
        content-type (get-type resp)]
    (matches? acceptable-types content-type)))

(defn over-limit?
  "Returns true if response's Content-Length is too long."
  [#^HttpResponse resp limit]
  (let [entity (.getEntity resp)]
    (and entity
         limit
         (> (.getContentLength entity) limit))))
