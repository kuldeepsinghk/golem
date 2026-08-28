(ns golem.core
  "The Golem's Scroll — the entire game engine.

   The thesis in one sentence:
     A scroll is a vector of keywords. That vector IS the program,
     and every 'magical' mechanic — ×3, mirror, cursed rune — is an
     ordinary function from vector to vector.

   No parser. No AST classes. No interpreter framework.
   The reader gives us programs-as-data; the sequence library
   gives us program transformation.

   The levels themselves live in golem.levels — this namespace
   knows how to read a scroll, not which puzzles exist.")

;; ─────────────────────────────────────────────────────────────
;; The world

(def cols 7)
(def rows 6)

(def delta      {:north [0 -1] :east [1 0] :south [0 1] :west [-1 0]})
(def turn-left  {:north :west  :west :south :south :east :east :north})
(def turn-right {:north :east  :east :south :south :west :west :north})

(defn in-bounds? [[x y]]
  (and (< -1 x cols) (< -1 y rows)))

;; ─────────────────────────────────────────────────────────────
;; Scroll rewriters — this is the homoiconic heart.
;; Each one is just a function: scroll -> scroll.

(def flip-turn {:left :right, :right :left})

(defn mirror-scroll
  "Flip every turn tile still on the scroll."
  [scroll]
  (mapv #(get flip-turn % %) scroll))

(defn unfold-3
  "Triple the first tile of the remaining scroll."
  [scroll]
  (if-let [t (first scroll)]
    (into [t t t] (rest scroll))
    (vec scroll)))

(defn echo-scroll
  "Duplicate the entire remaining scroll — everything after this point
   plays twice. A loop, in one line."
  [scroll]
  (into (vec scroll) scroll))

(def rune-effects
  "Floor runes, as data. A level says {:rune {:at [2 4] :effect :reverse}}
   and we look the function up here. Adding a new curse to the game
   is adding one entry to this map."
  {:reverse (comp vec reverse)
   :mirror  mirror-scroll
   :vanish  (comp vec rest)})

;; ─────────────────────────────────────────────────────────────
;; Reading one tile

(defn init-state [level scroll]
  {:level  level
   :pos    (:start level)
   :dir    (:dir level)
   :scroll (vec scroll)
   :status :running          ; :running | :won | :crashed | :empty | :exhausted
   :steps  0
   :rewrite nil})            ; set for one step when the scroll was rewritten

(defn step
  "Consume the head of the scroll, produce the next state.
   The entire game is (iterate step initial-state)."
  [{:keys [scroll pos dir level status] :as state}]
  (if (not= status :running)
    state
    (cond
      (empty? scroll)
      (assoc state :status :empty :rewrite nil)

      ;; echo and ×3 make non-terminating scrolls writable ([:echo :echo],
      ;; [:x3 :x3 :walk]) — the golem collapses instead of running forever.
      (>= (:steps state) 200)
      (assoc state :status :exhausted :rewrite nil)

      :else
      (let [tile   (first scroll)
            remain (vec (rest scroll))
            state  (-> state
                       (update :steps inc)
                       (assoc :rewrite nil :last-tile tile))]
        (case tile
          :walk
          (let [pos' (mapv + pos (delta dir))]
            (if-not (in-bounds? pos')
              (assoc state :status :crashed :scroll remain)
              (let [effect  (when (= pos' (get-in level [:rune :at]))
                              (get-in level [:rune :effect]))
                    scroll' (if effect ((rune-effects effect) remain) remain)
                    state'  (assoc state :pos pos' :scroll scroll'
                                         :rewrite (when effect {:type :rune :effect effect}))]
                (if (= pos' (:gem level))
                  (assoc state' :status :won)
                  state'))))

          :left   (assoc state :dir (turn-left dir)  :scroll remain)
          :right  (assoc state :dir (turn-right dir) :scroll remain)
          :x3     (assoc state :scroll (unfold-3 remain)
                               :rewrite {:type :unfold})
          :mirror (assoc state :scroll (mirror-scroll remain)
                               :rewrite {:type :mirror})
          :echo   (assoc state :scroll (echo-scroll remain)
                               :rewrite {:type :echo})
          ;; unknown tile: skip it
          (assoc state :scroll remain))))))

(defn trace
  "Run a state to completion; returns the vector of every state along the way."
  [state]
  (loop [s state, acc [state]]
    (if (or (not= (:status s) :running) (> (count acc) 300))
      acc
      (let [s' (step s)]
        (recur s' (conj acc s'))))))

(defn outcome
  "Convenience: what happens if this scroll runs on this level?"
  [level scroll]
  (:status (peek (trace (init-state level scroll)))))
