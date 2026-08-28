(ns golem.core
  "The Golem's Scroll — the entire game engine.

   The thesis in one sentence:
     A scroll is a vector of keywords. That vector IS the program,
     and every 'magical' mechanic — ×3, mirror, cursed rune — is an
     ordinary function from vector to vector.

   No parser. No AST classes. No interpreter framework.
   The reader gives us programs-as-data; the sequence library
   gives us program transformation.")

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

;; ─────────────────────────────────────────────────────────────
;; Levels — also just data.

(def levels
  [{:id 1 :name "First Steps"
    :desc "Write a scroll of tiles. Press Run — the golem reads it one tile at a time, doing exactly what it says."
    :start [0 2] :dir :east :gem [3 2]
    :palette [:walk :left :right]
    :capacity 6
    :solution [:walk :walk :walk]}

   {:id 2 :name "The Unfolding"
    :desc "The gem is six steps away — but the scroll only holds four tiles. The ×3 tile doesn't move the golem: it rewrites the scroll, tripling the tile after it. Watch the scroll when it's read."
    :start [0 2] :dir :east :gem [6 2]
    :palette [:walk :left :right :x3]
    :capacity 4
    :solution [:x3 :walk :x3 :walk]}

   {:id 3 :name "The Corner"
    :desc "Around the bend — and still not enough tiles to write every step by hand. Unfold wisely."
    :start [0 4] :dir :east :gem [4 1]
    :palette [:walk :left :right :x3]
    :capacity 6
    :solution [:x3 :walk :walk :left :x3 :walk]}

   {:id 4 :name "The Mirror"
    :desc "The scroll shop has run out of left-turn tiles. The mirror tile rewrites the scroll: every turn tile still on it is flipped. Turn left using only rights."
    :start [0 4] :dir :east :gem [3 0]
    :palette [:walk :right :mirror :x3]
    :capacity 9
    :solution [:walk :walk :walk :mirror :right :walk :walk :walk :walk]}

   {:id 5 :name "The Cursed Rune"
    :desc "A cursed rune lies on the floor at the marked square. The moment the golem steps on it, the rest of the scroll is REVERSED. Write the tail backwards — the curse will fix it."
    :start [0 4] :dir :east :gem [3 2]
    :rune {:at [2 4] :effect :reverse}
    :palette [:walk :left :right :x3 :mirror]
    :capacity 8
    :solution [:walk :walk :walk :walk :left :walk]}

   {:id 6 :name "The Echo"
    :desc "The gem sits at the top of a staircase far too long to write out. The echo tile rewrites the scroll: everything after it plays TWICE. Write the staircase once — let the scroll repeat itself."
    :start [0 5] :dir :east :gem [4 1]
    :palette [:walk :left :right :x3 :echo]
    :capacity 6
    :solution [:echo :echo :walk :left :walk :right]}])
