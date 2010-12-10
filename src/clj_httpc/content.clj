(ns clj-httpc.content
  "Utilities for content type handling."
  (:require [clojure.contrib.str-utils2 :as su])
  (:import [com.google.gdata.util ContentType]))

(defn get-type
  "Return the ContentType of the HTTP response body."
  [resp]
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
  [acceptable-types content-type]
  (if (nil? content-type)
    true
    (some #(.match content-type %) acceptable-types)))
