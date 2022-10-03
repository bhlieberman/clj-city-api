(ns frontend.core
  (:require [reagent.core :as rc]
            [reagent.dom :as rd]
            [promesa.core :as p]))

(defn city-lookup []
  (let [city (rc/atom nil)
        _qs (when @city (str "&q=" @city))
        get-city (fn [_qs] (p/let [_resp (js/fetch "api/data/city/")
                                   data (.json (clj->js _resp))]
                             (println data)))]
    (fn []
      [:div.form-group.d-flex.m-2.p-2
       [:input.form-control {:type "text" :on-change (fn [e] (reset! city (.. e -target -value)))}]
       [:button.btn.btn-primary {:on-click #(get-city _qs)} "Submit"]
       [:span _qs]])))

(defn map-card [_city]
  (let [city (rc/atom nil)
        _get-map (fn [] (p/let [_resp (js/fetch "api/data/maps/")
                                data (.json (clj->js _resp))]
                          (println data)))]
    (fn [] [:div.card.border.rounded.m-2.p-2.text-center
            [:div.card-body
             [:div.card-title.fs-3 "Welcome to the city of: " @city]
             [:button.btn.btn-secondary {:on-click #(reset! city "New York")} "Get city map"]]])))

(defn weather-card []
  (let [weather (rc/atom nil)
        get-weather (fn [] (p/let [_resp (js/fetch "api/data/weather/")
                                   json (.json (clj->js _resp))
                                   data (js->clj json :keywordize-keys true)]
                             (reset! weather data)))]
    (fn [] [:div.card.border.rounded.m-2.p-2.text-center
            [:div.card-body
             [:div.card-title.fs-3 "Weather Forecast"]
             [:button.btn.btn-secondary {:on-click #(get-weather)} "Get weather"]
             (into [:div.container.m-2.p-2]
                   (map (fn [[min_temp max_temp desc]]
                          [:div.card.m-2
                           [:div.card-body
                            (into [:div.card-text
                                   (str "Low of " min_temp " and high of " max_temp " with " (.toLowerCase desc))])]])) @weather)]])))

(defn movies-card []
  (let [movies (rc/atom nil)
        get-movies (fn [] (p/let [_resp (js/fetch "api/data/movies/")
                                  json (.json (clj->js _resp))
                                  data (js->clj json :keywordize-keys true)]
                            (reset! movies data)))]
    (fn [] [:div.card.border.rounded.m-2.p-2.text-center
            [:div.card-body
             [:div.card-title.fs-3 "Movies Playing Soon"]
             [:button.btn.btn-secondary {:on-click #(get-movies)} "Get movies"]]
            (into [:div.container.m-2.p-2] (map (fn [movie] [:div.card.m-2
                                                             [:div.card-body
                                                              (into [:div.card-text] (first movie))]])) @movies)])))

(defn restaurants-card []
  (let [get-restaurants (fn [] (p/let [_resp (js/fetch "api/data/restaurants/")
                                       data (.json (clj->js _resp))]
                                 data))
        req (fn [] (get-restaurants))
        show-succ (rc/atom false)
        show-err (rc/atom false)
        succ (when (= (:status req) 200)
               (swap! show-succ not)
               (:body req))
        err (when (= (:status req) 500)
              (swap! show-err not)
              "Something went wrong")]
    (fn [] [:div.card.border.rounded.m-2.p-2.text-center
            [:div.card-body
             [:div.card-title.fs-3 "Restaurants"]
             (when @show-succ [:div.card-text succ])
             (when @show-err [:div.card-text err])
             [:button.btn.btn-secondary {:on-click #(req)} "Show restaurants"]]])))

(defn app []
  [:div.container
   [:div.text-center.h-2
    [:header.fs-1 "City Explorer"]]
   [city-lookup]
   [:div {:id "collapse-target"}
    [map-card ""]
    [weather-card]
    [movies-card]
    [restaurants-card]]])

(defn ^:dev/after-load render []
  (rd/render [app] (.getElementById js/document "app")))

(defn init []
  (render))
