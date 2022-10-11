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

(defn get-map [{{:keys [lat lon]} :body-params}]
  (let [url "https://maps.locationiq.com/v3/staticmap"
        res (client/get url {:query-params {:key (env :CITY-KEY)
                                            :lat lat
                                            :lon lon
                                            :zoom 17}})]
    (r/response res)))

(defn get-city-data
  "Gets geographical info from LocationIQ API."
  [{{:keys [q]} :body-params}]
  (let [url "https://us1.locationiq.com/v1/search.php"
        res (client/get url {:query-params {"key" (env :CITY-KEY) "q" q :format "json"}})]
    (r/response (->> (select-keys (-> res
                                      :body
                                      (read-str :key-fn keyword)
                                      first) [:lat :lon :display_name])
                     (into [])))))

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
       ["city/" {:handler (fn [req] (get-city-data req))}]
       ["maps/" {:handler (fn [req] (get-map req))}]
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
  (get-city-data {:body-params {:q "Seattle"}})
  )