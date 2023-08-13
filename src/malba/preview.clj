;; Copyright 2023 blnote.
;; This file is part of MALBA.

(ns malba.preview)

(import  (org.gephi.preview.api Vector)
         (javax.swing JPanel JTextArea BorderFactory)
         (java.awt Color BorderLayout  AlphaComposite)
         (java.awt.event  InputEvent MouseListener MouseMotionListener MouseWheelListener)
         (java.util Timer TimerTask))


;;timer to buffer resize/scrolling events and refresh preview
(def timer (atom nil))
;;checked within timer to determine if repaint is necessary
(def do-refresh (atom false))
(def interval 100) ;refresh interval (minimum)
(def counter (atom 10)) ;number of times to check for refresh before timer stops


(defn screen-pos-to-model-pos
  "transform coordinates on screen to preview model coords"
  [target screen-pos height width]
  (let [scaled-trans (doto (Vector. width height)
                       (.mult 0.5)
                       (.mult (- 1 (.getScaling target))))]
    (doto screen-pos
      (.sub scaled-trans)
      (.div (.getScaling target))
      (.sub (.getTranslate target)))))

(defn- refresh [preview target]
  (reset! do-refresh true)
  (when-not @timer ;start refresh loop 
    (reset! timer (Timer. "Refresh Loop" true))
    (.schedule @timer (proxy [TimerTask] []
                        (run []
                          (if (compare-and-set! do-refresh true false)
                            (when target
                              #_(SwingUtilities/invokeLater #((.refresh target) (.repaint preview)))
                              (.refresh target)
                              (.repaint preview))
                            (if (= 0 @counter)
                              (do (.cancel @timer)
                                  (reset! timer nil)
                                  (reset! counter 10))
                              (swap! counter dec)))))
               0 interval)))

;;timer to show detail information when hovered
(def hover-time 400) ;delay
(def hover-timer (atom nil))

(defn end-hover-timer []
  (when @hover-timer
    (.cancel @hover-timer)
    (reset! hover-timer nil)))

(defn start-hover-timer [evt-fn]
  (when @hover-timer (.cancel @hover-timer))
  (reset! hover-timer (Timer. "Hover Timer" true))
  (.schedule @hover-timer (proxy [TimerTask] []
                            (run []
                              (evt-fn))) hover-time))

(defn- detail-panel
  "construct panel with publication detail information"
  []
  (let [detail-panel (proxy [JTextArea] [] ;;semi-transparent textarea with border
                       (paintComponent [g]
                         (let [composite (.getComposite g)]
                           (doto g
                             (.setComposite (AlphaComposite/getInstance AlphaComposite/SRC_OVER 0.75))
                             (.setColor (.getBackground this))
                             (.fillRect 0 0 (.getWidth this) (.getHeight this))
                             (.setComposite composite)))
                         (let [shade 0
                               pixels 2]
                           (doseq [i (range pixels)]
                             (doto g
                               (.setColor (Color. shade shade shade (* i (/ 90 pixels))))
                               (.drawRect i i
                                          (- (.getWidth this) (+ 1 (* 2 i)))
                                          (- (.getHeight this) (+ 1 (* 2 i))))))
                           #_(.paintChildren g)
                           (proxy-super paintComponent g))))]
    (doto detail-panel
      (.setOpaque false)
      (.setEditable false)
      (.setVisible false)
      (.setLayout (BorderLayout.))
      (.setBorder (BorderFactory/createCompoundBorder (.getBorder detail-panel)
                                                      (BorderFactory/createEmptyBorder 3 3 3 3))))))

(def ref-move (atom (Vector. 0 0)))
(def last-move (atom (Vector. 0 0)))

(defn- preview-panel
  "construct preview panel and connect paintComponent to render target from preview controller."
  [target evt-dispatch]
  (let [panel
        (proxy [JPanel MouseListener MouseMotionListener MouseWheelListener] []
          (paintComponent [g]
            (proxy-super paintComponent g)
            (when target
              (let [width (.getWidth this)
                    height (.getHeight this)]
                (when (or (not= (.getWidth target) width)
                          (not= (.getHeight target) height))
                  (.resize target width height))
                (.drawImage g (.getImage target) 0 0 width height this))))

          (mousePressed [e]
            (.set @ref-move (.getX e) (.getY e))
            (.set @last-move (.getTranslate target))
            (refresh this target))
          (mouseReleased [e]
            (refresh this target))
          (mouseDragged [e]
            (doto (.getTranslate target)
              (.set (.getX e) (.getY e))
              (.sub @ref-move)
              (.div (.getScaling target)) ;ensure const. moving speed whatever the zoom is 
              (.add @last-move))
            (refresh this target))
          (mouseWheelMoved [e]
            (let [scroll (.getUnitsToScroll e)]
              (when (not= 0 scroll)
                (.setScaling target (* (.getScaling target)
                                       (if (< scroll 0) 12/10 10/12)))
                (refresh this target))))
          (mouseEntered [e])
          (mouseClicked [e]
            (evt-dispatch "hovered" {:event :end
                                     :time (System/currentTimeMillis)})
            (when target
              (let [model-pos (screen-pos-to-model-pos target
                                                       (Vector. (.getX e) (.getY e))
                                                       (.getHeight this) (.getWidth this))]
                (if (not= 0 (bit-and (.getModifiers e) InputEvent/BUTTON3_MASK))
                  (evt-dispatch "copy-to-clipboard" [(.x model-pos) (- (.y model-pos))])
                  (evt-dispatch "view-neighbors" [(.x model-pos) (- (.y model-pos))])))))
          (mouseExited [e]
            (end-hover-timer)
            (evt-dispatch "hovered" {:event :end
                                     :time (System/currentTimeMillis)}))
          (mouseMoved [e]
            (let [model-pos (screen-pos-to-model-pos target
                                                     (Vector. (.getX e) (.getY e))
                                                     (.getHeight this) (.getWidth this))]
              (start-hover-timer #(evt-dispatch "hovered" {:event [(.x model-pos) (- (.y model-pos))]
                                                           :time (System/currentTimeMillis)})))))]
    (doto panel
      (.addMouseListener panel)
      (.addMouseMotionListener panel)
      (.addMouseWheelListener panel))))

(defn- show-details
  "called after hover event to show publication details"
  [dp ^String s]
  (if s
    (doto dp
      (.setText s)
      (.setVisible true))
    (.setVisible dp false)))

(defn init-preview
  "creates preview panel, 
   returns a refresh function to be called after graph changes,
   mouse clicks..."
  [target evt-dispatch]
  (let [preview (preview-panel target evt-dispatch)
        dp (detail-panel)]
    (.add preview dp)
    {:preview preview
     :refresh-fn #(refresh preview target)
     :reset-fn #(when target (.reset target) (refresh preview target))
     :show-details-fn (partial show-details dp)}))

