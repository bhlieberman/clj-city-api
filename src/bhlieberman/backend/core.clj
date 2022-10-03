(ns backend.core
  (:require [ring.adapter.jetty :as jetty]
            [reitit.ring :as ring]
            [ring.util.response :as r]
            [clojure.java.io :as io]
            [muuntaja.core :as m]
            [ring.middleware.params :as p]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [backend.env :refer [env]]
            [clojure.repl :refer [doc]]
            [clojure.data.json :refer [write-str read-str]]
            [clojure.pprint :as pprint]
            [clj-http.client :as client]))

(defonce server (atom nil))

(defn index []
  (slurp (io/resource "public/index.html")))

(def api-data {:city {:req {:uri "https://us1.locationiq.com/v1/search.php"
                            :method "GET"}
                      :handler (fn [_req] {:body "city" :status 200})}
               :maps {:req {:uri "https://maps.locationiq.com/v3/staticmap"
                            :method "GET"}
                      :handler (fn [_req] {:body "map" :status 200})}
               :weather {:req {:uri "http://api.weatherbit.io/v2.0/forecast/daily"
                               :method "GET"}
                         :handler (fn [_req] {:body "weather" :status 200})}
               :movies {:req {:uri "https://api.themoviedb.org/3/search/movie"
                              :method "GET"}
                        :handler (fn [_req] {:body "movies" :status 200})}})

(defn city-endpoint []
  (let [uri (get-in api-data [:city :req :uri])]
    (client/get uri {:accept :json :query-params {"key" (env :CITY-KEY) "q" "Seattle"}})))

(defn get-weather
  "Gets the weather forecast from the WeatherBit API." 
  []
  (let [url "http://api.weatherbit.io/v2.0/forecast/daily"
        res (client/get url {:query-params {"lat" "39.2904" "lon" "76.6122" "key" (env :WEATHER-KEY)}})]
    (r/response (map (juxt :min_temp :max_temp #(get-in % [:weather :description]))
          (-> res
              :body
              (read-str :key-fn keyword)
              :data)))))

(defn get-movies
  "Gets movie info based on query value."
  []
  (let [url "https://api.themoviedb.org/3/search/movie"
        res (client/get url {:query-params {:query "Seattle" :api_key (env :MOVIES-KEY)}})]
    (r/response (map (juxt :title :release_date :popularity)
                     (-> res
                      :body
                      (read-str :key-fn keyword)
                      :results)))))

(def app
  (ring/ring-handler
   (ring/router
    ["/"
     ["ping" {:handler (fn [req] {:status 200
                                  :body (with-out-str (pprint/pprint req))
                                  :headers {"Content-Type" "text/plain"}})}]
     ["api/"
      ["data/"
       ["city/" {:handler (fn [_req] (client/get "https://us1.locationiq.com/v1/search.php" {:query-params {"key" (env :CITY-KEY) "q" "Seattle"}}))}]
       ["maps/" {:handler (fn [_req] (client/get (get-in api-data [:maps :req :uri])))}]
       ["weather/" {:handler (fn [_req] (get-weather))}]
       ["movies/" {:handler (fn [_req] (get-movies))}]
       ["restaurants/" (fn [_req] {:body (write-str {:restaurants ["chipotle" "mcdonalds"]}) :status 200})]]]
     ["assets/*" (ring/create-resource-handler {:root "public/assets"})]
     ["" {:handler (fn [_req] {:body (index) :status 200})}]]
    {:data {:muuntaja m/instance
            :middleware [muuntaja/format-middleware wrap-keyword-params p/wrap-params]}})))

(defn start-server []
  (swap! server
         assoc
         :jetty
         (jetty/run-jetty #'app
                          {:port 3002
                           :join? false})))

(defn stop-server []
  (when-some [s @server]
    (.stop (:jetty s))
    (reset! server nil)))

(comment
  (stop-server)
  (start-server)
  (client/get "http://localhost:3002/api/data/movies/")
  (client/get "http://localhost:3002/api/data/weather/")
  (client/get "https://us1.locationiq.com/v1/search.php" {:query-params {"key" (env :CITY-KEY) "q" "Seattle"}})
  (client/get "http://localhost:3002/api/data/city/")

  )