(ns clj-httpc.content
  "Utilities for content type handling."
  (:require
    [clojure.contrib.str-utils2 :as su])
  (:import
    [org.apache.http HttpResponse]
    [com.google.gdata.util ContentType]))

(def #^{:doc
  "Associate this key in the HttpParams with a number indicating the
  number of bytes to download before aborting the request."}
  limit "clj-httpc.content-length-limit")

(def #^{:doc
  "Associate this key in the HttpParams with a boolean indicating whether or
  not to abort the request if the response content type does not match the
  list of acceptable content types provided in ther request."}
  match-acceptable-content "clj-httpc.match-acceptable-content")

(defn- create-content-type
  "This is a big kludge and I'm not sure where else to put this, or if it
  should be included at all.  Sometimes web servers will return invalid
  Content-Type headers.  For example, instead of 'text/plain', they might
  return 'text'.  The ContentType constructor will not allow these and
  matches-acceptable? will fail.

  TODO: Figure out a better place to put this or don't handle it at all."
  [text]
  (ContentType. (if (= text "text") "text/plain" text)))

(defn get-type
  "Return the ContentType of the HTTP response body."
  [#^HttpResponse resp]
  (let [content-type (.. resp (getEntity) (getContentType))]
    (if (nil? content-type)
      nil
      (create-content-type (.getValue content-type)))))

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
  ContentTypes, or if the supplied ContentType is nil."
  [acceptable-types #^ContentType content-type]
  (if (nil? content-type)
    true
    (some #(.match content-type %) acceptable-types)))

(defn matches-acceptable?
  "Returns true if the response's Content-Type matches any of the Accept
  headers."
  [headers resp params]
  (if (get params match-acceptable-content)
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
