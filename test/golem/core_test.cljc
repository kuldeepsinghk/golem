(ns golem.core-test
  "Level validations. Figwheel auto-testing re-runs these on every save,
   so the engine can be refactored fearlessly right up to talk day."
  (:require [clojure.test :refer [deftest is testing]]
            [golem.core :as g]))

(deftest every-level-is-solvable
  (doseq [{:keys [id solution capacity] :as level} g/levels]
    (testing (str "level " id)
      (is (= :won (g/outcome level solution))
          (str "level " id " reference solution must win"))
      (is (<= (count solution) capacity)
          (str "level " id " solution must fit the scroll capacity")))))

(deftest puzzles-cannot-be-brute-forced
  (testing "level 2: gem unreachable without the unfold tile"
    (let [l2 (nth g/levels 1)]
      ;; capacity is 4, gem is 6 walks away — best naive attempt falls short
      (is (= :empty (g/outcome l2 [:walk :walk :walk :walk])))))
  (testing "level 4: a raw right turn sends the golem into the wall"
    (let [l4 (nth g/levels 3)]
      (is (= :crashed (g/outcome l4 [:walk :walk :walk :right
                                     :walk :walk :walk :walk])))))
  (testing "level 5: writing the path forwards fails — the rune reverses it"
    (let [l5 (nth g/levels 4)]
      (is (= :empty (g/outcome l5 [:walk :walk :walk :left :walk :walk])))))
  (testing "level 6: the best ×3 route needs 7 tiles — one over capacity"
    (let [l6 (nth g/levels 5)]
      (is (= :won (g/outcome l6 [:x3 :walk :walk :left :x3 :walk :walk])))
      (is (< (:capacity l6) 7)))))

(deftest scroll-rewriters
  (testing "unfold-3 triples the head tile"
    (is (= [:walk :walk :walk :left]
           (g/unfold-3 [:walk :left])))
    (is (= [] (g/unfold-3 []))))
  (testing "mirror flips turn tiles and leaves the rest alone"
    (is (= [:right :walk :left]
           (g/mirror-scroll [:left :walk :right]))))
  (testing "echo duplicates the remaining scroll — a loop in one line"
    (is (= [:walk :left :walk :left]
           (g/echo-scroll [:walk :left])))
    (is (= [] (g/echo-scroll []))))
  (testing "rune effects are plain scroll->scroll functions"
    (is (= [:c :b :a] ((g/rune-effects :reverse) [:a :b :c])))
    (is (= [:b :c]    ((g/rune-effects :vanish)  [:a :b :c])))))

(deftest non-terminating-scrolls-are-writable
  (testing "two tiles suffice to write a program that never halts"
    (is (= :exhausted (g/outcome (first g/levels) [:echo :echo])))
    (is (= :exhausted (g/outcome (first g/levels) [:x3 :x3 :walk])))))

(deftest the-homoiconic-moment
  (testing "the same rewrite works on ANY scroll, because a scroll is just data"
    (let [program [:x3 :walk :left]]
      ;; the program can be transformed before it ever runs —
      ;; this is a macro, played with game tiles
      (is (= [:x3 :walk :right] (g/mirror-scroll program))))))
