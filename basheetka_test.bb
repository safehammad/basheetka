#!/usr/bin/env bb
(ns basheetka-test
  (:require [babashka.classpath :refer [add-classpath]]
            [clojure.test :as test :refer [deftest testing is are]]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [babashka.process :refer [shell]]
            [babashka.fs :as fs]))

;; We don't use bb.edn to set the classpath as any bb.edn is for our "spreadsheet"
(add-classpath ".")
(require '[basheetka :as bs])

;;; Utils

(deftest kvs->array-map-test
  (testing "Construct array map. Used to maintain ordering when pprinting tasks to bb.edn."
    (is (= (array-map :a 1 :b 2 :c 3) (bs/kvs->array-map [[:a 1] [:b 2] [:c 3]])))
    (is (= (array-map :a 1 :c 3 :b 2) (bs/kvs->array-map [[:a 1] [:c 3] [:b 2]])))))

;;; Manipulate bb.edn

(deftest dependent-cells-test
  (testing "Find cells named [A-Z][0-9] in expression. Try all accepted types of expression."
    (are [result value] (= result (bs/dependent-cells value))
         '[A1 B1] '(+ A1 5 B1 6)
         '[B1 A1] '(+ B1 5 A1 6)
         []       5
         []       5.5
         []       "Foo")))

(deftest bb-edn-tasks-test
  (let [bb-edn (into {} (bs/bb-edn-tasks (apply array-map '[A1 5 B1 6 C1 (+ A1 B1 7)])))]
    (is (= [:requires :leave 'import 'export] (keys bb-edn)))
    (is (= '[A1 B1 C1] (get-in bb-edn ['export :depends])))))

(deftest bb-edn-cells-test
  (is (= '[[A1 {:task 5}] [B1 {:task 6}] [C1 {:task (+ A1 B1 7), :depends [A1 B1]}]]
         (bs/bb-edn-cells (apply array-map '[A1 5, B1 6, C1 (+ A1 B1 7)])))))

;;; Import / export csv file

(deftest col-names-test
  (testing "Column names are correct and in correct order."
    (let [col-names (vec bs/col-names)]
      (is (= 702 (count bs/col-names)))
      (is (= (map str  "ABCDEFGHIJKLMNOPQRSTUVWXYZ") (subvec col-names 0 26)))
      (is (= ["AA" "AB"] (subvec col-names 26 28)))
      (is (= ["AZ" "BA"] (subvec col-names 51 53)))
      (is (= ["ZY" "ZZ"] (subvec col-names 700 702))))))

(deftest parse-value-test
  (testing "Parse string to correct type."
    (are [parsed-value value] (= parsed-value (bs/parse-value value))
         5            "5"
         5.5          "5.5"
         '(+ A1 B1 5) "(+ A1 B1 5)"
         "Value"      "Value")))

(deftest csv->map-test
  (testing "Convert csv file to map of key-vals."
    (are [csv kvs] (= (bs/csv->map (char-array csv)) (apply array-map kvs))
         "5,6,(+ A1 B1 7)\n1,2,3" '[A1 5, B1 6, C1 (+ A1 B1 7), A2 1, B2 2, C2 3]
         "5,6\n1,2,3"             '[A1 5, B1 6, A2 1, B2 2, C2 3]
         "5,6,7\n1,2"             '[A1 5, B1 6, C1 7 A2 1, B2 2])))

(deftest map->csv-test
  (testing "Convert key-vals to csv."
    (are [sheet-map csv] (= csv (str/trim (str (doto (java.io.StringWriter.) (bs/map->csv sheet-map)))))
         '{A1 5, B2 6, B1 7, A2 8}       "5,7\n8,6"
         '{A1 5, B2 6, B1 7, A2 8, C2 9} "5,7\n8,6,9"
         '{A1 5, B2 6, B1 7, A2 8, C1 9} "5,7,9\n8,6")))

;;; Round trip test with basheetka.bb and bb tasks

(deftest round-trip-test
  (testing  "Ensure the bb (babashka) binary is in the PATH."
    (is (fs/which "bb")))
  (testing "Round trip basheetka operations."
    (fs/with-temp-dir [tmp-dir {}]
      (fs/copy "basheetka.bb" tmp-dir)
      (shell {:dir (str tmp-dir)} "bb" "basheetka.bb" "init")
      (is (= bs/initial-bb-edn (edn/read-string (slurp (str (fs/path tmp-dir bs/*bb-edn-file*))))))
      (spit (str (fs/path tmp-dir "in.csv")) "5,6,(+ A1 B1 7)\n1,2,3")
      (shell {:dir (str tmp-dir)} "bb" "import" "in.csv")
      (shell {:dir (str tmp-dir)} "bb" "export" "out.csv")
      (is (= "5,6,18\n1,2,3" (str/trim (slurp (str (fs/path tmp-dir "out.csv")))))))))

;;; Run tests

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (test/run-tests)]
    (when (pos? (+ fail error))
      (System/exit 1))))
