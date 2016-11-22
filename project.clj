(defproject navis/untangled-auth "0.1.0"
  :description "Support for session auth. Currently targeting openid, but meant to be expanded over time."
  :url "https://github.com/untangled-web/untangled-auth"
  :license {:name "MIT"
            :url  "https://opensource.org/licenses/MIT"}

  :dependencies [[bidi "2.0.12"]
                 [clj-http "2.1.0"]
                 [clj-jwt "0.1.1"]
                 [com.stuartsierra/component "0.3.1"]
                 [com.taoensso/timbre "4.3.1"]
                 [commons-codec "1.10"]
                 [crypto-equality "1.0.0"]
                 [hickory "0.6.0" :scope "test"]
                 [navis/untangled-spec "0.3.9" :scope "test" :exclusions [org.clojure/google-closure-library-third-party org.clojure/google-closure-library io.aviso/pretty org.clojure/clojurescript]]
                 [org.clojure/clojure "1.9.0-alpha13" :scope "provided"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/java.classpath "0.2.2"]
                 [org.clojure/tools.namespace "0.2.10"]
                 [ring/ring-defaults "0.1.5"]
                 [ring/ring-mock "0.3.0" :scope "test"]]

  :plugins [[com.jakemccrary/lein-test-refresh "0.17.0"]]

  :source-paths ["src"]
  :test-paths ["specs" "specs/config"]
  :resource-paths ["src" "resources"]

  :jvm-opts ["-server" "-Xmx1024m" "-Xms512m" "-XX:-OmitStackTraceInFastThrow"]

  :test-refresh {:report       untangled-spec.reporters.terminal/untangled-report
                 :with-repl    true
                 :changes-only true}

  :test-selectors {:focused :focused}

  :profiles {:dev {:source-paths ["dev"]
                   :repl-options {:init-ns user}}})
