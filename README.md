DESCRIPTION
===========

A Clojure HTTP library thinly wrapping the [Apache HttpComponents](http://hc.apache.org/) client.  This repository is forked from the old `clj-http` library (which no longer exists).  One main differences is that this library gives you access to the underlying HttpComponents library if you wanted it, and it is easier to set up persistent connections.  The new (as of version `0.2.6`) [clj-http](https://github.com/dakrone/clj-http) has persistent connections; please consider using it.

This library uses Clojure 1.2.0, and I do not expect to update it for 1.3.0.

Thanks go to the [clj-sys](https://github.com/clj-sys) guys for writing the original implementation.

NOTE: ALL APIs ARE SUBJECT TO CHANGE WITHOUT NOTICE.

FEATURES
========

These features are added on top of `clj-http`:

- Allows user to specify HttpParams to underlying HttpClient
- Logs all redirects
- Aborts downloading of response body if content types don't match
- Aborts downloading of response body if content length is too long
- Allows thread safe re-use a single instance of HttpClient
- Correctly handles non-UTF-8 charsets in response Content-Type

USAGE
=====

The main HTTP client functionality is provided by the `clj-httpc.client` namespace:

    (require '[clj-httpc.client :as client])

If you want to create your own Commons HttpClient to use (e.g. to use a `ThreadSafeClientConnManager`) or if you want to abort response downloading based on content length:

    (require '[clj-httpc.content :as content])
    (use '[clj-httpc.core :only (with-http-client)])

The client supports simple `get`, `head`, `put`, `post`, and `delete` requests. Responses are returned as Ring-style response maps:

    (client/get "http://www.duck.com")
    => {:status 200
        :start-time 1295562743800
        :end-time 1295562743953
        :time 153
        :exception nil
        :redirects [[#<URI http://www.google.com/> 302]]
        :headers {"date" "Sun, 01 Aug 2010 07:03:49 GMT"
                  "cache-control" "private, max-age=0"
                  "content-type" "text/html; charset=ISO-8859-1"
                  ...}
        :body "<!doctype html>..."}

    (client/get "http://www.site.com/zhongwen")
    => {:status 200
        ...
        :headers {"content-type" "text/plain; charset=big5" ...}
        :body "中文"}

More example requests:

    (client/get "http://site.com/resources/id")

    (client/post "http://site.com/resources" {:body byte-array})

    (client/post "http://site.com/resources" {:body "string"})

    (client/get "http://site.com/protected" {:basic-auth ["user" "pass"]})

    (client/get "http://site.com/search" {:query-params {"q" "foo, bar"}})

    (client/get "http://site.com/favicon.ico" {:as :byte-array})

    (client/post "http://site.com/api"
      {:basic-auth ["user" "pass"]
       :body "{\"json\": \"input\"}"
       :headers {"X-Api-Version" "2"}
       :content-type :json
       :accept :json})

Aborts the request if the response body is not really JSON (aborted requests have status nil and body nil):

    (client/get "http://site.com/resources/3"
                {:http-params {content/force-match? true}
                 :accept :json})

Aborts the request if the really-big-file is too large (aborted requests have status nil and body nil):

    (client/get "http://site.com/really-big-file.mpg" {:http-params {content/limit 100000}})

Use your own Commons HttpClient instance if you want to.  For example, in case you wanted to share a single instance amongst multiple threads:

    ; The following code is in util/create-http-client, but you can modify it to
    ; suit your needs.
    (import clj_httpc.CustomRedirectStrategy)

    (def http-client
      (let [http-params (util/create-http-params)
            scheme-registry (util/create-scheme-registry)
            manager (ThreadSafeClientConnManager. http-params scheme-registry)
            client (DefaultHttpClient. manager http-params)]
        (.setRedirectStrategy client (CustomRedirectStrategy.))))

    (with-http-client http-client
      (fn [_]
        (client/get "http://www.duck.com")))

A more general `response` function is also available, which is useful as a primitive for building higher-level interfaces:

    (defn api-action [method path & [opts]]
      (client/request
        (merge {:method method :url (str "http://site.com/" path)} opts)))

The client will not throw exceptions on exceptional status codes:

    (:exception (client/get "http://site.com/broken"))
    => Exception: 500

The client will also follow redirects on the appropriate `30*` status codes.

    (client/get "http://www.duck.com")
    => {:redirects [[#<URI http://www.google.com/> 302]]
        ...}

The client transparently accepts and decompresses the `gzip` and `deflate` content encodings.

DEVELOPMENT
===========

To run the tests:

    $ lein deps
    $ lein run -m clj-httpc.run-server
    $ lein test

LICENSE
=======

Released under the MIT License: <http://www.opensource.org/licenses/mit-license.php>
