(ns golem.levels-test
  "Level content — is every puzzle well-formed, playable and beatable?

   These run against the real engine, so a level is only 'solvable' if
   golem.core/step actually walks its reference solution onto the gem.
   Adding a level to golem.levels/all adds it to every test here."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [golem.core :as g]
            [golem.levels :as levels]))

(defn level-by-id
  "Look a level up by :id, never by position — so inserting or reordering
   levels in golem.levels/all cannot silently retarget these tests."
  [id]
  (or (some #(when (= id (:id %)) %) levels/all)
      (throw (ex-info (str "no level with :id " id) {:id id}))))

(deftest there-is-at-least-one-level
  ;; every other test here loops over levels/all, and a loop over an
  ;; empty vector passes vacuously — so assert the vector isn't empty.
  (is (seq levels/all) "golem.levels/all must not be empty"))

(deftest level-ids-are-unique
  (is (= (count levels/all)
         (count (distinct (map :id levels/all))))
      (str "duplicate :id in " (sort (map :id levels/all)))))

(deftest every-level-is-solvable
  (doseq [{:keys [id solution capacity] :as level} levels/all]
    (testing (str "level " id)
      (is (= :won (g/outcome level solution))
          (str "level " id " reference solution must win"))
      (is (<= (count solution) capacity)
          (str "level " id " solution must fit the scroll capacity")))))

(deftest level-data-is-well-formed
  (doseq [{:keys [id start dir gem palette capacity] :as level} levels/all]
    (testing (str "level " id)
      (is (g/in-bounds? start)
          (str "level " id " :start " start " is off the board"))
      (is (g/in-bounds? gem)
          (str "level " id " :gem " gem " is off the board"))
      (is (not= start gem)
          (str "level " id " starts the golem on top of the gem"))
      (is (contains? g/delta dir)
          (str "level " id " :dir " dir " is not one of " (sort (keys g/delta))))
      (is (pos? capacity)
          (str "level " id " needs a positive :capacity"))
      (is (seq palette)
          (str "level " id " has an empty :palette"))
      (is (not (str/blank? (:name level)))
          (str "level " id " needs a :name — the level tab shows it"))
      (is (not (str/blank? (:desc level)))
          (str "level " id " needs a :desc — the level blurb shows it")))))

(deftest rune-levels-name-a-real-effect
  ;; core/step does ((rune-effects effect) remain) — an unknown :effect is
  ;; not a wrong answer, it is a nil call that throws the moment the golem
  ;; steps on the rune. Catch the typo here instead.
  (doseq [{:keys [id rune]} (filter :rune levels/all)]
    (testing (str "level " id)
      (is (g/in-bounds? (:at rune))
          (str "level " id " :rune :at " (:at rune) " is off the board"))
      (is (contains? g/rune-effects (:effect rune))
          (str "level " id " :rune :effect " (:effect rune)
               " is not one of " (sort (keys g/rune-effects)))))))

(deftest solutions-only-use-tiles-from-the-palette
  ;; a solution that needs a tile the player has no button for is
  ;; unsolvable in the UI even though it wins here.
  (doseq [{:keys [id palette solution]} levels/all]
    (testing (str "level " id)
      (let [missing (distinct (remove (set palette) solution))]
        (is (empty? missing)
            (str "level " id " solution uses tiles absent from its :palette: "
                 (vec missing)))))))

(deftest puzzles-cannot-be-brute-forced
  (testing "level 2: gem unreachable without the unfold tile"
    (let [l2 (level-by-id 2)]
      ;; capacity is 4, gem is 6 walks away — best naive attempt falls short
      (is (= :empty (g/outcome l2 [:walk :walk :walk :walk])))))
  (testing "level 4: a raw right turn sends the golem into the wall"
    (let [l4 (level-by-id 4)]
      (is (= :crashed (g/outcome l4 [:walk :walk :walk :right
                                     :walk :walk :walk :walk])))))
  (testing "level 5: writing the path forwards fails — the rune reverses it"
    (let [l5 (level-by-id 5)]
      (is (= :empty (g/outcome l5 [:walk :walk :walk :left :walk :walk])))))
  (testing "level 6: the best ×3 route needs 7 tiles — one over capacity"
    (let [l6 (level-by-id 6)]
      (is (= :won (g/outcome l6 [:x3 :walk :walk :left :x3 :walk :walk])))
      (is (< (:capacity l6) 7)))))
