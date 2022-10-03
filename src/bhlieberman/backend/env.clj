(ns backend.env
(:require [clojure.edn]))

(def envvars (clojure.edn/read-string (slurp "env.edn")))

(defn env [k]
  (or (k envvars) (System/getenv (name k))))