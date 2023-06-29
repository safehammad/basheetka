#!/usr/bin/env bb
(ns basheetka
  (:require [babashka.tasks :as tasks]
            [babashka.cli :as cli]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.data.csv :as csv]
            [clojure.pprint :refer [pprint]]))

;;; Consts

(def initial-bb-edn (array-map :paths ["."],
                               :tasks
                               {:requires '([basheetka :as bs]),
                                'import {:task '(bs/import)}}))

(def ^:dynamic *bb-edn-file* "bb.edn")

;;; Store spreadsheet cell values updated via :leave hook

(def state (atom {}))

;;; Utils

(defn kvs->array-map
  "Transform pairs of vectors to array map. For example, [[:a 1] [:b 2]] -> (array-map :a 1 :b 2)"
  [kvs]
  (apply array-map (sequence cat kvs)))

;;; Manipulate bb.edn

(defn dependent-cells [expr]
  (mapv symbol (re-seq (re-pattern "[A-Z]+\\d+") (str expr))))

(defn bb-edn-tasks [sheet-map]
  [[:requires '([basheetka :as bs])]
   [:leave '(bs/leave)]
   ['import {:task '(bs/import)}]
   ['export {:task '(bs/export) :depends (vec (keys sheet-map))}]])

(defn bb-edn-cells [sheet-map]
  (loop [kvs []
         [[cell value] & more-tasks] sheet-map]
    (if cell
      (let [depends (dependent-cells value)
            task    (cond-> {:task value}
                      (seq depends) (assoc :depends depends))]
        (recur (conj kvs [cell task]) more-tasks))
      kvs)))

(defn bb-edn-update [bb-edn sheet-map]
  (assoc bb-edn :tasks (kvs->array-map (concat (bb-edn-tasks sheet-map) (bb-edn-cells sheet-map)))))

(defn bb-edn-read []
  (edn/read-string (slurp *bb-edn-file*)))

(defn bb-edn-write [bb-edn]
  (pprint bb-edn (io/writer *bb-edn-file*)))

;;; Import / export csv file

;; (A B C ... Z AA AB AC ... AZ BA BB BC ... ZZ)
(def col-names (let [letters "ABCDEFGHIJKLMNOPQRSTUVWXYZ"]
                 (for [i (cons "" letters)
                       j letters]
                   (str i j))))

(defn form? [value]
  (when (and (str/starts-with? value "(") (str/ends-with? value ")"))
    (edn/read-string value)))

(def parse-value (some-fn parse-long parse-double form? identity))

(defn csv->map [filename]
  (with-open [r (io/reader filename)]
    (kvs->array-map (for [[row-num row] (map vector (iterate inc 1) (csv/read-csv r))
                          [col-name value] (map vector col-names row)]
                      [(symbol (str col-name row-num)) (parse-value value)]))))

(defn map->csv [filename sheet-map]
  (with-open [w (io/writer filename)]
    (csv/write-csv w (-> (group-by (comp second name first) (sort sheet-map))
                         (update-vals (partial map second))
                         (sort)
                         (vals)))))

;;; Task names exposed in bb.edn
;;; Change function names with care. Update generated bb.edn if you do.

(defn import []
  (-> (bb-edn-read)
      (bb-edn-update (csv->map (first *command-line-args*)))
      (bb-edn-write)))

(defn export []
  (map->csv (first *command-line-args*) @state))

(defn leave []
  (let [{:keys [name result]} (tasks/current-task)]
    (swap! state assoc name result)))

;;; Create initial bb.edn (added overwrite existing with --force option)

(defn init [{:keys [opts]}]
  (let [bb-exists? (.exists (io/file *bb-edn-file*))
        force? (:force opts)]
    (when (and (not force?) bb-exists?)
      (print "bb.edn file already exists. Do you want to overwrite? [y/n] ")
      (flush)
      (let [response (read-line)]
        (when (not= (clojure.string/lower-case response) "y")
          (println "Using existing bb.edn")        
          (System/exit 0))))
  
    (if bb-exists? (println "Overwriting bb.edn") (println "Initializing bb.edn"))
    (bb-edn-write initial-bb-edn)))

(defn help [_]
  (println  "Usage: ./basheetka.bb init [optional] --force"))

(def cmd-table [{:cmds ["init"] :fn init :args->opts [:force]}
                {:cmds [] :fn help}])

(defn -main [& args]
  (cli/dispatch cmd-table args))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
