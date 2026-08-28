(ns golem.ui
  "The Golem's Scroll — Reagent UI.

   One state atom. While idle you edit :scroll; pressing Run hands it
   to the engine, and a timer walks the engine one `step` at a time.
   The scroll strip always shows the LIVE scroll — so every rewrite
   (unfold, mirror, rune) happens before the player's eyes."
  (:require [reagent.core :as r]
            [reagent.dom.client :as rdomc]
            [golem.core :as g]))

;; ─────────────────────────────────────────────────────────────
;; State

(defonce state
         (r/atom {:level-ix 0
                  :scroll   []       ; the scroll being edited
                  :game     nil      ; engine state while running / finished
                  :timer    nil}))

(defn current-level [] (nth g/levels (:level-ix @state)))

;; ─────────────────────────────────────────────────────────────
;; Actions

(defn stop-timer! []
  (when-let [t (:timer @state)]
    (js/clearInterval t))
  (swap! state assoc :timer nil))

(defn tick! []
  (swap! state update :game g/step)
  (when (not= :running (get-in @state [:game :status]))
    (stop-timer!)))

(defn start! []
  (stop-timer!)
  (swap! state assoc :game (g/init-state (current-level) (:scroll @state)))
  (swap! state assoc :timer (js/setInterval tick! 600)))

(defn step-once! []
  (stop-timer!)
  (if (:game @state)
    (when (= :running (get-in @state [:game :status]))
      (swap! state update :game g/step))
    (swap! state assoc :game (g/init-state (current-level) (:scroll @state)))))

(defn reset-run! []
  (stop-timer!)
  (swap! state assoc :game nil))

(defn clear-scroll! []
  (reset-run!)
  (swap! state assoc :scroll []))

(defn add-tile! [t]
  (when (and (nil? (:game @state))
             (< (count (:scroll @state)) (:capacity (current-level))))
    (swap! state update :scroll conj t)))

(defn remove-tile! [i]
  (when (nil? (:game @state))
    (swap! state update :scroll
           #(vec (concat (subvec % 0 i) (subvec % (inc i)))))))

(defn select-level! [ix]
  (stop-timer!)
  (swap! state assoc :level-ix ix :scroll [] :game nil))

;; ─────────────────────────────────────────────────────────────
;; Look & feel

(def tile-label
  {:walk "walk" :left "⟲ left" :right "⟳ right"
   :x3 "×3" :mirror "⇋ mirror" :echo "𝄇 echo"})

(def tile-color
  {:walk   "#e7d8b1"
   :left   "#d9c79a"
   :right  "#d9c79a"
   :x3     "#c9a86a"      ; the rewriters get a deeper, magical tone
   :mirror "#c9a86a"
   :echo   "#c9a86a"})

(def golem-glyph {:north "▲" :east "▶" :south "▼" :west "◀"})

(def ink "#3a2e1f")

;; ─────────────────────────────────────────────────────────────
;; Components

(defn tile-chip [t {:keys [head? on-click ghost?]}]
  [:div {:on-click on-click
         :style {:min-width 58 :padding "8px 10px" :text-align "center"
                 :font-family "ui-monospace, monospace" :font-size 14
                 :font-weight (if (#{:x3 :mirror :echo} t) 700 500)
                 :color ink
                 :background (if ghost? "transparent" (tile-color t "#e7d8b1"))
                 :border (cond
                           head? "2px solid #b45309"
                           ghost? "2px dashed rgba(58,46,31,0.35)"
                           :else "1px solid rgba(58,46,31,0.4)")
                 :border-radius 6
                 :box-shadow (when head? "0 0 12px rgba(180,83,9,0.7)")
                 :cursor (when on-click "pointer")
                 :user-select "none"}}
   (if ghost? "·" (tile-label t (name t)))])

(defn scroll-strip []
  (let [{:keys [scroll game]} @state
        live?    (some? game)
        tiles    (if live? (:scroll game) scroll)
        cap      (:capacity (current-level))
        rewrite  (:rewrite game)]
    [:div {:style {:background "linear-gradient(#f0e4c3, #e4d3a8)"
                   :border (if rewrite "2px solid #b45309" "2px solid #a8946c")
                   :box-shadow (if rewrite
                                 "0 0 24px rgba(180,83,9,0.8)"
                                 "0 2px 8px rgba(0,0,0,0.35)")
                   :border-radius 10 :padding 14 :margin "14px 0"
                   :transition "box-shadow 0.15s, border 0.15s"}}
     [:div {:style {:display "flex" :justify-content "space-between"
                    :font-family "Georgia, serif" :color ink :font-size 13
                    :margin-bottom 8}}
      [:span (if live? "The scroll — being read" "The scroll — write your program")]
      [:span (if live?
               (case (:type rewrite)
                 :unfold "✨ the scroll rewrites itself — ×3 unfolds"
                 :mirror "✨ the scroll rewrites itself — turns flipped"
                 :rune   "✨ the rune rewrites the scroll"
                 :echo   "✨ the scroll rewrites itself — the rest plays twice"
                 (str (count tiles) " tiles remain"))
               (str (count tiles) " / " cap " tiles"))]]
     [:div {:style {:display "flex" :gap 8 :flex-wrap "wrap" :min-height 38}}
      (concat
        (for [[i t] (map-indexed vector tiles)]
          ^{:key (str "t" i)}
          [tile-chip t {:head? (and live? (zero? i)
                                    (= :running (:status game)))
                        :on-click (when-not live? #(remove-tile! i))}])
        (when-not live?
          (for [i (range (- cap (count tiles)))]
            ^{:key (str "g" i)}
            [tile-chip :walk {:ghost? true}])))]]))

(defn board []
  (let [{:keys [game]} @state
        level (current-level)
        pos   (if game (:pos game) (:start level))
        dir   (if game (:dir game) (:dir level))
        gem   (:gem level)
        rune  (get-in level [:rune :at])
        won?  (= :won (:status game))]
    [:div {:style {:display "grid"
                   :grid-template-columns (str "repeat(" g/cols ", 52px)")
                   :gap 3 :padding 10 :background "#141420"
                   :border-radius 10 :width "fit-content"}}
     (for [y (range g/rows) x (range g/cols)]
       ^{:key (str x "-" y)}
       [:div {:style {:width 52 :height 52 :border-radius 5
                      :background (if (odd? (+ x y)) "#232336" "#1c1c2d")
                      :display "flex" :align-items "center"
                      :justify-content "center" :font-size 24
                      :position "relative"}}
        (when (= [x y] rune)
          [:span {:style {:position "absolute" :font-size 26
                          :color "#7c3aed" :opacity 0.9}} "◉"])
        (when (and (= [x y] gem) (not won?))
          [:span {:style {:filter "drop-shadow(0 0 6px #22d3ee)"}} "💎"])
        (when (= [x y] pos)
          [:span {:style {:color "#4ade80" :font-size 28
                          :filter "drop-shadow(0 0 6px #4ade80)"
                          :transition "all 0.2s"}}
           (if won? "🗿" (golem-glyph dir))])])]))

(defn status-banner []
  (when-let [game (:game @state)]
    (let [[msg color]
          (case (:status game)
            :won     ["💎 The golem reaches the gem. The scroll ran true." "#4ade80"]
            :crashed ["The golem walks into the wall. Rewrite the scroll." "#f87171"]
            :empty   ["The scroll runs out before the gem. Too few words." "#fbbf24"]
            :exhausted ["The golem collapses. The scroll never ends." "#a78bfa"]
            :running [(str "Reading the scroll… step " (:steps game)) "#94a3b8"]
            nil)]
      [:div {:style {:margin "10px 0" :font-family "Georgia, serif"
                     :font-size 16 :color color :min-height 22}}
       msg])))

(defn palette []
  (let [{:keys [game]} @state
        level (current-level)]
    [:div {:style {:display "flex" :gap 8 :align-items "center"
                   :opacity (if game 0.35 1)
                   :pointer-events (if game "none" "auto")}}
     [:span {:style {:color "#94a3b8" :font-size 13
                     :font-family "Georgia, serif"}} "Tiles:"]
     (for [t (:palette level)]
       ^{:key t} [tile-chip t {:on-click #(add-tile! t)}])]))

(defn button [label on-click primary?]
  [:button {:on-click on-click
            :style {:padding "8px 18px" :border-radius 6 :font-size 14
                    :font-family "Georgia, serif" :cursor "pointer"
                    :background (if primary? "#b45309" "transparent")
                    :color (if primary? "#fff" "#cbd5e1")
                    :border (if primary? "none" "1px solid #475569")}}
   label])

(defn controls []
  (let [{:keys [game scroll]} @state]
    [:div {:style {:display "flex" :gap 10 :margin "14px 0"}}
     [button "▶ Run" start! (and (seq scroll) (nil? game))]
     [button "Step" step-once! false]
     [button "Reset golem" reset-run! false]
     [button "Clear scroll" clear-scroll! false]]))

(defn level-tabs []
  [:div {:style {:display "flex" :gap 6 :margin-bottom 12}}
   (for [[ix {:keys [id name]}] (map-indexed vector g/levels)]
     ^{:key id}
     [:div {:on-click #(select-level! ix)
            :style {:padding "6px 12px" :border-radius 6 :cursor "pointer"
                    :font-family "Georgia, serif" :font-size 13
                    :background (if (= ix (:level-ix @state)) "#b45309" "#1e293b")
                    :color (if (= ix (:level-ix @state)) "#fff" "#94a3b8")}}
      (str id ". " name)])])

(defn game-root []
  (let [level (current-level)]
    [:div {:style {:min-height "100vh" :background "#0b0b14"
                   :padding "28px 32px" :box-sizing "border-box"}}
     [:div {:style {:max-width 720}}
      [:h1 {:style {:font-family "Georgia, serif" :color "#e7d8b1"
                    :font-weight 400 :font-size 30 :margin "0 0 4px"}}
       "The Golem's Scroll"]
      [:p {:style {:color "#64748b" :font-family "Georgia, serif"
                   :font-style "italic" :margin "0 0 18px" :font-size 14}}
       "The golem does exactly what the scroll says. Some tiles rewrite the scroll."]
      [level-tabs]
      [:p {:style {:color "#94a3b8" :font-family "Georgia, serif"
                   :font-size 15 :line-height 1.5 :margin "0 0 6px"}}
       (:desc level)]
      [scroll-strip]
      [palette]
      [controls]
      [status-banner]
      [board]]]))

;; ─────────────────────────────────────────────────────────────
;; Mount
;;
;; The React 18 root is created once and kept across hot reloads —
;; createRoot on an already-rooted container would warn and remount.

(defonce root
         (delay (rdomc/create-root (js/document.getElementById "app"))))

(defn mount! []
  (rdomc/render @root [game-root]))

;; figwheel calls this after any namespace is reloaded, so a change
;; to the engine in core.cljc re-renders the board too.
(defn ^:after-load re-render! [] (mount!))

(mount!)
