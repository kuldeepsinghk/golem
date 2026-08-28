(ns golem.scroll
  "Scroll rewriters — the homoiconic heart.

   A scroll is a vector of keywords. Every 'magical' mechanic — ×3,
   mirror, cursed rune — is an ordinary function from vector to vector,
   and that is all this namespace holds.

   Nothing here knows about the golem, the grid or the game state: these
   functions take a scroll and return a scroll, so they can be read and
   tested on their own (see golem.scroll-test). golem.core is the caller
   that decides WHEN a rewrite fires; this namespace decides WHAT it does.

   Adding a mechanic to the game starts by adding one function here."
  (:refer-clojure :exclude [reverse]))

;; ─────────────────────────────────────────────────────────────
;; Tile rewriters — what a rewrite tile does to the rest of the scroll

(def flip-turn
  "The turn tiles, each mapped to its opposite. Used by `mirror`."
  {:left :right, :right :left})

(defn mirror
  "Flip every turn tile still on the scroll."
  [scroll]
  (mapv #(get flip-turn % %) scroll))

(defn unfold-3
  "Triple the first tile of the remaining scroll."
  [scroll]
  (if-let [t (first scroll)]
    (into [t t t] (rest scroll))
    (vec scroll)))

(defn echo
  "Duplicate the entire remaining scroll — everything after this point
   plays twice. A loop, in one line."
  [scroll]
  (into (vec scroll) scroll))

;; ─────────────────────────────────────────────────────────────
;; Rune rewriters — what a floor rune does to the rest of the scroll

(defn reverse
  "Read the rest of the scroll backwards. Shadows clojure.core/reverse
   inside this namespace (see the ns :exclude) because from the game's
   side the operation has no better name — and unlike core's, it returns
   a vector, so the result is still a scroll."
  [scroll]
  (vec (clojure.core/reverse scroll)))

(def rune-effects
  "Floor runes, as data. A level says {:rune {:at [2 4] :effect :reverse}}
   and we look the function up here. Adding a new curse to the game
   is adding one entry to this map."
  {:reverse reverse})
