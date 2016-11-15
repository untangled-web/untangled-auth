(ns untangled.auth.core
  (:require
    [com.stuartsierra.component :as cmp]
    [clojure.data.json :as json]
    [untangled.auth.access-token-handler :as ath]
    [untangled.auth.openid-mock-server :as oms]))

(defn openid-location [{:keys [config] :as env} match]
  "A helper endpoint that can be injected via untangled server's :extra-routes.
   This allows untangled clients to access the configuration they require to begin the OpenID auth process."
  (let [openid-config (-> config :value :openid)
        url (str (:authority openid-config) "/connect/authorize")]
    {:status  200
     :headers {"Content-Type" "application/json"}
     :body    (json/write-str
                {:authUrl  url
                 :scope    (:scope openid-config)
                 :clientId (:client-id openid-config)})}))

(defn build-access-token-handler [& [openid-config]]
  (cmp/using
    (ath/map->AccessTokenHandler {})
    {:openid-config (or openid-config :openid-config)}))

(defrecord VirtualOpenIdConfig [config]
  cmp/Lifecycle
  (start [this]
    (cmp/start
      (if (-> config :value :openid-mock)
        (oms/map->MockOpenIdConfig this)
        (ath/map->OpenIdConfig this))))
  (stop [this]
    (cmp/stop this)))

(defn build-openid-config []
  (cmp/using
    (map->VirtualOpenIdConfig {})
    [:config]))
