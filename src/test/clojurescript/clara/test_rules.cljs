(ns clara.test-rules
  (:require-macros [cemerick.cljs.test :refer (is deftest run-tests testing)]
                  [clara.macros :refer [defrule defsession defquery]] )
  (:require [cemerick.cljs.test :as t]
            [clara.rules.engine :as eng]
            [clara.rules.accumulators :as acc]
            [clara.rules :refer (assemble-session insert fire-rules query insert!)]
            [clara.rules.testfacts :refer [->Temperature Temperature
                                           ->WindSpeed WindSpeed
                                           ->ColdAndWindy ColdAndWindy]]))

(comment
;; Launch browser repl.
 (cemerick.piggieback/cljs-repl :repl-env (cemerick.austin/exec-env))
)

(defn- has-fact? [token fact]
  (some #{fact} (map first (:matches token))))

(def simple-defrule-side-effect (atom nil))
(def other-defrule-side-effect (atom nil))


(defrule test-rule 
  [Temperature (< temperature 20)]
  =>
  (reset! simple-defrule-side-effect ?__token__))

(defrule test-other-rule 
  [Temperature (< temperature 20)]
  =>
  (reset! other-defrule-side-effect ?__token__))

(defquery cold-query
  []
  [Temperature (< temperature 20) (== ?t temperature)])

;; Accumulator for getting the lowest temperature.
(def lowest-temp (acc/min :temperature))

(defquery coldest-query
  []
  [?t <- lowest-temp :from [Temperature]])


(defrule is-cold-and-windy
  "Rule to determine whether it is indeed cold and windy."

  (Temperature (< temperature 20) (== ?t temperature))
  (WindSpeed (> windspeed 30) (== ?w windspeed))
  =>
  (insert! (->ColdAndWindy ?t ?w)))

(defquery find-cold-and-windy
    []
    [?fact <- ColdAndWindy])

(defquery wind-without-temperature
  []
  [WindSpeed (== ?w windspeed)]
  [:not [Temperature]])

(defquery wind-with-temperature
  []
  [WindSpeed (== ?w windspeed) (== ?loc location)]
  [Temperature (== ?t temperature) (== ?loc location)])

(defsession my-session 'clara.test-rules)  

(deftest test-simple-defrule
  (let [session (insert my-session (->Temperature 10 "MCI"))]
    
    (fire-rules session)

    (is (has-fact? @simple-defrule-side-effect (->Temperature 10 "MCI")))))

(deftest test-simple-query
  (let [session (-> my-session
                    (insert (->Temperature 15 "MCI"))
                    (insert (->Temperature 10 "MCI"))
                    (insert (->Temperature 80 "MCI")))]

    ;; The query should identify all items that wer einserted and matchd the
    ;; expected criteria.
    (is (= #{{:?t 15} {:?t 10}}
           (set (query session cold-query))))))

(deftest test-simple-accumulator
  (let [session (-> my-session
                    (insert (->Temperature 15 "MCI"))
                    (insert (->Temperature 10 "MCI"))
                    (insert (->Temperature 80 "MCI")))]

    ;; Accumulator returns the lowest value.
    (is (= #{{:?t 10}}
           (set (query session coldest-query))))))

(deftest test-simple-insert

  (let [session (-> my-session
                    (insert (->Temperature 15 "MCI"))
                    (insert (->WindSpeed 45 "MCI"))
                    (fire-rules))]

    (is (= #{{:?fact (->ColdAndWindy 15 45)}}  
           (set 
            (query session find-cold-and-windy))))))

(deftest test-no-temperature

  ;; Test that a temperature cancels the match.
  (let [session (-> my-session                 
                    (insert (->Temperature 15 "MCI"))
                    (insert (->WindSpeed 45 "MCI"))
                    (fire-rules))]

    (is (= #{}  
           (set 
            (query session wind-without-temperature)))))

  ;; Now test the no temperature scenario.
  (let [session (-> my-session                 
                    (insert (->WindSpeed 45 "MCI"))
                    (fire-rules))]

    (is (= #{{:?w 45}}  
           (set 
            (query session wind-without-temperature))))))


(deftest test-simple-join

  (let [session (-> my-session
                    (insert (->Temperature 15 "MCI"))
                    (insert (->WindSpeed 45 "MCI"))
                    (fire-rules))]

    (is (= #{{:?w 45 :?t 15 :?loc "MCI"}}  
           (set 
            (query session wind-with-temperature))))))
