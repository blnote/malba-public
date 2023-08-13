(ns build
  (:require [clojure.tools.build.api :as b]))

;build command: clojure -T:build jar

(def lib 'tu-berlin/malba)
(def version "0.5")
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn" :aliases []}))
(def uber-file (format "target/%s-%s.jar" (name lib) version))

(defn clean [_]
  (b/delete {:path "target"}))


(defn uber [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src"]
               :target-dir class-dir})
  (b/copy-file {:src "malba-algo.edn" :target "target/malba-algo.edn"})
  (b/copy-file {:src "database.edn" :target "target/database.edn"})
  (b/copy-file {:src "README.md" :target "target/README.md"})
  
  (b/compile-clj {:basis basis
                  :src-dirs ["src"]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis basis
           :main 'malba.core 
           :conflict-handlers {"(?i)^(META-INF/)?(COPYRIGHT|NOTICE|LICENSE)(\\.(txt|md))?$" :append-dedupe}})
  (b/delete {:path "target/classes"})
  (b/zip {:src-dirs ["target"] :zip-file (format "%s-%s.zip" (name lib) version)}))