(ns godemo.core
  (:require [clojure.core.async :refer [>! >!! <! <!! alts! alts!! chan go timeout thread]])
  (:import [java.net URL]
           [java.io IOException]
           [java.util.concurrent Executors ExecutorCompletionService TimeUnit]))

(def urls {"Go" "http://golang.org/"
           "Python" "http://python.org/"
           "Perl" "http://www.perl.org/"
           "Scala" "http://www.scala-lang.org/"
           "Clojure" "http://clojure.org/"
           "Haskell" "http://www.haskell.org/haskellwiki/Haskell"
           "Ruby" "http://www.ruby-lang.org/en/"})

(defn format-time [t] (str (format "%.3f" t) "s"))

(defn fetch [lang urlstr]
  (try
    (let [start (System/nanoTime)
          size (count (slurp (.openStream (URL. urlstr))))
          t (format-time (/ (- (System/nanoTime) start) 1e9))]
      (str lang " " size " [" t "]"))
    (catch IOException e (println (.getMessage e)))))

(defn godemo-1 []
  (try
    (let [f (fn [[k,v]] #(println (fetch k v)))
          fns (map f urls)]
      (dorun (apply pcalls fns)))
    (finally (shutdown-agents))))

(defn godemo-2 []
  (let [pool (Executors/newFixedThreadPool 8)
        f (fn [[k,v]] #(println (fetch k v)))
        fns (map f urls)
        rets (.invokeAll pool fns)]
    (.shutdown pool))) ; TODO timeout

(defn godemo-3 []
  (let [pool (Executors/newFixedThreadPool 8)
        cs (ExecutorCompletionService. pool)
        f (fn [[k,v]] #(fetch k v))
        fns (map f urls)]
    (doseq [g fns] (.submit cs g))
    (doseq [_ fns] (println (.. cs take get)))
    (.shutdown pool)))

(defn godemo-4 []
  (let [pool (Executors/newFixedThreadPool 8)
        cs (ExecutorCompletionService. pool)
        f (fn [[k,v]] #(fetch k v))
        fns (map f urls)]
    (doseq [g fns] (.submit cs g))
    (let [end (+ (System/nanoTime) 3e9)] ; 1s
      (loop [n (count fns)]
        (if (and (> n 0) (< (System/nanoTime) end))
          (do (println (.. cs take get))
              (recur (dec n)))
          (println "Timed out"))))
          ; TODO timeout doesn't really work yet
          ; make threads interruptible
    (.shutdown pool)))

;; Using core.async with OS threads
(defn godemo-5 []
  (let [ch (chan)
        nurls (count urls)
        t (timeout 1000)]
    (doseq [[k v] urls]
      (thread (>!! ch (fetch k v))))
    (loop [n 0]
      (when (< n nurls)
        (let [[v p] (alts!! [ch t])]
          (if (= p ch)
            (do (println v) (recur (inc n)))
            (println "Timed out")))))))

;; Using core.async with go blocks
(defn godemo-6 []
  (let [ch (chan)
        nurls (count urls)
        t (timeout 1000)]
    (doseq [[k v] urls]
      (go (>! ch (fetch k v))))
    (<!!
      (go (loop [n 0]
            (when (< n nurls)
              (let [[v p] (alts! [ch t])]
                (if (= p ch)
                  (do (println v) (recur (inc n)))
                  (println "Timed out")))))))))

;; TODO clojure.org seems to have a redirect installed since the response size is 0

(defn -main [& args]
  (godemo-6))
