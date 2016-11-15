(ns untangled.auth.access-token-handler
  (:require
    [ring.middleware.cookies :refer [wrap-cookies]]
    [ring.util.response :refer [get-header]]
    [clojure.string :refer [split]]
    [clojure.walk :as walk]
    [com.stuartsierra.component :as component]
    [clj-http.client :as http]
    [clojure.data.json :as json]
    [taoensso.timbre :as log]
    [untangled.auth.jwt-validation :refer :all]
    [bidi.bidi :as bidi]))

(defn- add-claims-to-request
  "Adds a :user to the request, which is a map of claims."
  [req token options]
  (assoc req :user ((:claims-transform options) (:claims token))))

(defn- default-claims-transform [claims]
  claims)

(defn- get-token-from-bearer [str]
  "Gets access token from the Bearer string."
  (let [[bearer token] (split str #" ")]
    (if (and token (= bearer "Bearer")) token nil)))

(defn- get-token [request]
  (when-let [token (or
                     (some-> request :headers (get "authorization") (get-token-from-bearer))
                     (get-in request [:params "openid/access-token"])
                     (get-in request [:form-params "access_token"])
                     (get-in request [:session :access-token])
                     ;; Run cookie last in case token is new.
                     (get-in request [:cookies "access_token" :value]))]
    (read-token token)))

(defn- missing-token? [token]
  (nil? token))

(defn- missing-sub? [token]
  (nil? (:sub (:claims token))))

(defn- missing-client-id? [token]
  (nil? (:client_id (:claims token))))

(defn- fail-with [token message]
  (log/trace "Token: " (:claims token) " Failed because: " message)
  false)

(defn- valid-token? [token {:keys [public-keys issuer audience grace-period-minutes]}]
  (cond
    (missing-token? token) false
    (not (valid-signature? token public-keys)) (fail-with token "Invalid signature.")
    (not (valid-issuer? token issuer)) (fail-with token "Invalid issuer.")
    (not (valid-expire? token grace-period-minutes)) (fail-with token "Expired token.")
    (not (valid-audience? token audience)) (fail-with token (str "Invalid audience. cfg-aud<" audience ">."))

    (and (missing-sub? token) (missing-client-id? token))
    (fail-with token (if (missing-sub? token) "Missing subject." "Missing client id."))

    :else true))

(def default-options
  {:authority            ""
   :audience             "api"
   :grace-period-minutes 1
   :claims-transform     default-claims-transform})

(defn wrap-access-token
  "Middleware that validates the request for a JWT access-token that are issued by
  an OpenID Connect server.  Validation rules include access-token signiture, issuer and
  audience.  The request wrapper also calls a specified function after validation to allow for
  claims transformation before associating the claims with the request.
  A :claims-principle map will be associated with the session after the claims
  claims transformation function is called.

  :issuer - a string that contains the issuer of the OpenID Connect server - required
  :public-key - a string that contains the public key used to validate the signiature - required
  :audience - a string that contains the Audience expected for this resource - required
  :grace-period-minutes - number of minutes token is allowed after token expiration time - optional (default 1)
  :claims-transform - function that is handle the token claims and must return
    transformed claims - optional"
  {:arglists '([options handler])}
  [options handler]
  (let [merged-options (merge default-options options)]
    (fn [{:as request :keys [uri]}]
      (let [token (get-token request)]
        (handler
          (cond-> request (valid-token? token merged-options)
            (add-claims-to-request token merged-options)))))))

(defrecord OpenIdConfig [config]
  component/Lifecycle
  (start [this]
    (log/info "Starting openid config download")
    (http/with-connection-pool {:timeout 5 :threads 2}
      (let [openid-config     (-> config :value :openid)
            authority         (:authority openid-config)
            discovery-doc-url (str authority "/.well-known/openid-configuration")]
        (if-let [discovery-doc (-> discovery-doc-url http/get :body json/read-str)]
          (let [issuer          (get discovery-doc "issuer")
                public-keys-url (get discovery-doc "jwks_uri")
                public-keys     (-> public-keys-url http/get :body json/read-str (get "keys"))
                public-keys'    (public-keys-from-jwks public-keys)
                openid-config'  (assoc openid-config :public-keys public-keys' :issuer issuer)]
            (assoc this :value openid-config'
              :middleware identity))
          (throw (ex-info "AccessTokenHandler: Error retrieving discovery document." {}))))))
  (stop [this] this))

(defrecord AccessTokenHandler [openid-config]
  component/Lifecycle
  (start [this]
    (log/info "Starting Access Token Handler")
    (assoc this :middleware
      (fn [h]
        (->> h
          (wrap-access-token (:value openid-config))
          ((:middleware openid-config))))))
  (stop [this] this))
