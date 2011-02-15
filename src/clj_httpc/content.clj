(ns clj-httpc.content
  "Utilities for content type handling."
  (:require
    [clojure.contrib.logging :as log]
    [clojure.contrib.str-utils2 :as su])
  (:import
    [java.nio.charset Charset UnsupportedCharsetException]
    [org.apache.http HttpResponse]
    [com.google.gdata.util ContentType]))

(def #^{:doc
  "Associate this key in the HttpParams with a number indicating the
  number of bytes to download before aborting the request."}
  limit "clj-httpc.content.limit")

(def #^{:doc
  "Associate this key in the HttpParams with a boolean indicating whether or
  not to abort the request if the response content type does not match the
  list of acceptable content types provided in ther request."}
  force-match? "clj-httpc.content.force-match?")

(def #^{:doc
  "Associate this key in the HttpParams with a String or Charset indicating
  the charset to use for output coercion.  This exists so the old client
  behavior can be preserved, but you probably really don't want this; without
  a Content-Type header in the HttpResponse, the charset will be the default
  for that particular type (which is probably what you want)."
  :deprecated "1.6.0"}
  default-charset "clj-httpc.content.default-charset")

(defn create-content-type
  "Create a ContentType object from the Content-Type header; we'll allow
  some kludge to default to sensible types."
  [text]
  (try
    (cond
      (nil? text) nil
      (= text "text") (ContentType. "text/plain")
      :default (ContentType. text))
    (catch IllegalArgumentException e
      (log/debug "Could not parse invalid media type: " text)
      nil)))

(defn get-type
  "Return the ContentType of the HTTP response body."
  [#^HttpResponse resp]
  (let [content-type (.. resp (getEntity) (getContentType))]
    (if (nil? content-type)
      nil
      (create-content-type (.getValue content-type)))))

(defn get-charset
  "Return the charset for this ContentType.  The default Content-Type for
  HTTP application/octet-stream;charset=iso-8859-1 as per RFC2616, but for
  backwards compatibility purposes."
  [#^ContentType content-type default]
  (cond
    (and (nil? content-type) (not (nil? default)))
      default
    (nil? content-type)
      "iso-8859-1"
    :default
      (.getCharset content-type)))

(defn parse-accept
  "Returns a list of ContentTypes parsed from the Accept header."
  [headers]
  (if (contains? headers "Accept")
    (let [accept-value (get headers "Accept")
          content-types (su/split accept-value #",")]
      (map #(create-content-type (su/trim %)) content-types))
    (list (ContentType. "*/*"))))

(defn matches?
  "Returns true if the supplied ContentType matches one of the acceptable
  ContentTypes."
  [acceptable-types #^ContentType content-type]
  (if (nil? content-type)
    false
    (some #(.match content-type %) acceptable-types)))

(defn matches-acceptable?
  "Returns true if the response's Content-Type matches any of the Accept
  headers."
  [headers resp params]
  (if (get params force-match?)
    (let [acceptable-types (parse-accept headers)
          content-type (get-type resp)]
      (matches? acceptable-types content-type))
    true))

(defn over-limit?
  "Returns true if response's Content-Length is too long."
  [#^HttpResponse resp limit]
  (let [entity (.getEntity resp)]
    (and entity
         limit
         (> (.getContentLength entity) limit))))
