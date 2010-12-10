# `clj-httpc`

A Clojure HTTP library thinly wrapping the [Apache HttpComponents](http://hc.apache.org/) client.  This repository is forked from [clj-http](http://hc.apache.org); it will try to allow you to access the wrapped HttpComponents library if you need to.

Thanks go to the [clj-sys](https://github.com/clj-sys) guys for writing the original implementation.

## Features

These features are added on top of (or will be added on top of) `clj-http`:

- Allows user to specify HttpParams to underlying HttpClient
- Logs all redirects
- Aborts downloading of response body if content types don't match
- Aborts downloading of response body if content length is too long (even if length isn't known beforehand)

## Usage

The main HTTP client functionality is provided by the `clj-httpc.client` namespace:

    (require '[clj-httpc.client :as client])

The client supports simple `get`, `head`, `put`, `post`, and `delete` requests. Responses are returned as Ring-style response maps:

    (client/get "http://google.com")
    => {:status 200
        :headers {"date" "Sun, 01 Aug 2010 07:03:49 GMT"
                  "cache-control" "private, max-age=0"
                  "content-type" "text/html; charset=ISO-8859-1"
                  ...}
        :body "<!doctype html>..."}

More example requests:

    (client/get "http://site.com/resources/id")

    (client/get "http://site.com/resources/3" {:accept :json})

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

A more general `response` function is also available, which is useful as a primitive for building higher-level interfaces:

    (defn api-action [method path & [opts]]
      (client/request
        (merge {:method method :url (str "http://site.com/" path)} opts)))

The client will throw exceptions on, well, exceptional status codes:

    (client/get "http://site.com/broken")
    => Exception: 500

The client will also follow redirects on the appropriate `30*` status codes.

The client transparently accepts and decompresses the `gzip` and `deflate` content encodings.

## Installation

`clj-httpc` is available as a Maven artifact from [Clojars](http://clojars.org/clj-httpc):

    :dependencies
      [[clj-httpc "0.1.1"] ...]

## Design

The design of `clj-httpc` is inspired by the [Ring](http://github.com/mmcgrana/ring) protocol for Clojure HTTP server applications.

The client in `clj-httpc.core` makes HTTP requests according to a given Ring request map and returns Ring response maps corresponding to the resulting HTTP response. The function `clj-httpc.client/request` uses Ring-style middleware to layer functionality over the core HTTP request/response implementation. Methods like `clj-httpc.client/get` are sugar over this `clj-httpc.client/request` function.

## Development

To run the tests:

    $ lein deps
    $ lein run -m clj-httpc.server
    $ lein test

## License

Released under the MIT License: <http://www.opensource.org/licenses/mit-license.php>
