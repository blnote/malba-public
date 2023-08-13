(ns malba.gephi
  (:require [clojure.string :as string])
  (:import [java.awt Color]
           [org.gephi.preview.types EdgeColor]))

(import  (org.openide.util Lookup)
         (org.gephi.project.api ProjectController)
         (org.gephi.graph.api GraphController)
         (org.gephi.preview.api  PreviewController PreviewProperty RenderTarget)
         (org.gephi.filters.api FilterController)
         (org.gephi.filters.plugin.attribute AttributeEqualBuilder$EqualBooleanFilter$Node)
         (org.gephi.filters.plugin.graph EgoBuilder$EgoFilter)
         (org.gephi.filters.plugin.operator INTERSECTIONBuilder$IntersectionOperator)
         (org.gephi.io.importer.api Container$Factory ImportController )
         (org.gephi.io.processor.plugin DefaultProcessor)
         (org.gephi.io.exporter.api ExportController) 
         (org.gephi.layout.plugin.forceAtlas ForceAtlasLayout)
         (org.gephi.layout.plugin.fruchterman FruchtermanReingold)
         (org.gephi.layout.plugin.noverlap NoverlapLayout)
         (org.gephi.layout.plugin.force StepDisplacement)
         (org.gephi.layout.plugin.force.yifanHu YifanHuLayout))


(def preview-state
  (atom {:ego nil ;contains node id if in neighbor-view
         :surrounding false ;surrounding nodes are shown when true
         :focused nil} ;contains the node close to mouse pointer when hovering
        ))

;max number of pixels a node is allowed to be away 
;from mouse pointer when focusing
(def max-dist 18)

(defn- get-class
  "class loader from netbeans used by gephi"
  [c]
  (.. (Lookup/getDefault) (lookup c)))

(defn- get-graph-model []
  (.getGraphModel (get-class GraphController)
                  (.getCurrentWorkspace (get-class ProjectController))))
(defn init
  "inits gephi project, graph model and returns rendering target"
  []
  (let [projectController (doto (get-class ProjectController)
                            (.newProject))
        workspace (.getCurrentWorkspace projectController)
        graphModel (.getGraphModel (get-class GraphController) workspace)
        pc (get-class PreviewController)
        props  (-> pc (.getModel) (.getProperties))
        target (.getRenderTarget pc RenderTarget/G2D_TARGET)]

    (doto (.getNodeTable graphModel) ;add information columns to graph
      (.addColumn "step" Integer)
      (.addColumn "surrounding" Boolean)
      (.addColumn "title" String)
      (.addColumn "pubyear" String)
      (.addColumn "authors" String)
      (.addColumn "source_title" String))

    (doto props ;layout properties for preview
      (.putValue PreviewProperty/SHOW_NODE_LABELS true)
      (.putValue PreviewProperty/NODE_LABEL_PROPORTIONAL_SIZE false)
      (.putValue PreviewProperty/NODE_OPACITY 70)
      (.putValue PreviewProperty/NODE_BORDER_WIDTH 1)
      (.putValue PreviewProperty/ARROW_SIZE 11.0)
      (.putValue PreviewProperty/EDGE_RESCALE_WEIGHT false)
      (.putValue PreviewProperty/EDGE_THICKNESS 0.4)
      (.putValue PreviewProperty/EDGE_CURVED false)
      (.putValue PreviewProperty/EDGE_COLOR (EdgeColor. Color/GRAY))
      (.putValue PreviewProperty/DIRECTED true)
      (.putValue PreviewProperty/NODE_LABEL_FONT
                   (-> (.getFontValue props PreviewProperty/NODE_LABEL_FONT)
                       (.deriveFont (int 5) (float 14)))))
    (.render pc target)
    target))

(defn- create-node [container id detail]
  (let [nodeDraft (.. container (factory) (newNodeDraft id))]
    (when detail
      (doseq [k (keys detail)]
        (.setValue nodeDraft (name k) (detail k)))
      (when-not (detail :label)
        (.setLabel nodeDraft id)))
    nodeDraft))

(defn layout-graph [algo]
  (let [steps 100
        algo (condp = algo
               "overlap" (NoverlapLayout. nil)
               "frucht" (FruchtermanReingold. nil)
               "force" (ForceAtlasLayout. nil)
               "yifan" (YifanHuLayout. nil (StepDisplacement. 0.5))
               (throw (IllegalArgumentException. (format "Layout %s not supported!" algo))))]
    (doto algo
      (.setGraphModel (get-graph-model))
      (.resetPropertiesValues)
      (.initAlgo))
    (loop [i 0]
      (if (and (< i steps) (.canAlgo algo))
        (do (.goAlgo algo)
            (recur (inc i)))
        (.endAlgo algo))))
  (.refreshPreview (get-class PreviewController)))

(defn- render-preview
  "sets up the gephi preview dependent on settings set through 
   view-surrounding and view-neighbours"
  []
  (let [pc (get-class PreviewController)
        fc (get-class FilterController)
        gm (get-graph-model)
        intersectionQ (.createQuery fc (INTERSECTIONBuilder$IntersectionOperator.))]

    (when-let [node-id (@preview-state :ego)] ;view only node and its neighbors 
      (let [ego-filter (EgoBuilder$EgoFilter.)]
        (doto ego-filter
          (.setPattern node-id)
          (.setDepth (int 1)))
        (.setSubQuery fc intersectionQ (.createQuery fc ego-filter))))
    
    (when-not (@preview-state :surrounding) ;filter out surrounding nodes
      (when-let [cl (.. gm (getNodeTable) (getColumn "surrounding"))]
        (let [sf (AttributeEqualBuilder$EqualBooleanFilter$Node. cl)]
          (.setMatch sf false)
          (.setSubQuery fc intersectionQ (.createQuery fc sf)))))
    
    (.setVisibleView gm (.filter fc intersectionQ)) ;apply filters
    
    (reduce (fn [_ n]
              (let [sur (.getAttribute n "surrounding")]
                (when (= false sur) 
                  (.setColor n (Color/RED)))
                )) nil (-> gm (.getGraph) (.getNodes)))
    
    
    (.. pc (getModel) (getProperties) (putValue PreviewProperty/NODE_OPACITY 60))
    (.refreshPreview pc)))



(defn view-reset []
  (swap! preview-state assoc :ego nil)
  (render-preview))


(defn clear-graph []
  (-> (get-graph-model) (.getGraph) (.clear))
  (view-reset))

(defn view-surrounding [b]
  (swap! preview-state assoc :surrounding b)
  (render-preview))


(defn- dist "eucledian distance" [[a b] [c d]]
  (Math/sqrt (+ (Math/pow (- c a) 2) (Math/pow (- d b) 2))))

(defn- find-nearest-neighbor [coords]
  (when (and coords (number? (first coords)) (number? (last coords)))
    (first  (reduce (fn [[nmin d] n]
                      (let [nd (dist coords [(.x n) (.y n)])]
                        (if (< nd d) [n nd] [nmin d])))
                    [nil max-dist] (-> (get-graph-model) (.getGraphVisible) (.getNodes))))))

(defn view-neighbors
  "find nearest neighoring node for given coordinates 
   and focuses preview on it and its neighbors."
  [coords]
  (when-let [node (find-nearest-neighbor coords)]
    (swap! preview-state assoc :ego (.getId node))
    (render-preview)))


(defn- defocus-node []
  (when-let [node-id (@preview-state :focused)]
    (try
      (when-let [node (.. (get-graph-model) (getDirectedGraph) (getNode node-id))]
        (.setColor node (-> (.getColor node) (.brighter) (.brighter)))
        (.setSize node  (/ (.size node) 2.0))
        (when-not (@preview-state :ego) ;if not in neighbor view, focus also neighbors 
          (reduce (fn [r n]
                    (.setSize n (/ (.size n) 2.0))
                    r)
                  nil (.. (get-graph-model) (getDirectedGraph) (getNeighbors node))))
        node)
      (catch IllegalArgumentException _) 
      (finally (swap! preview-state assoc :focused nil)))))

(defn- focus-node [coords]
  (defocus-node)
  (when-not (= coords :end)
    (when-let [node (find-nearest-neighbor coords)]
      (try
        (swap! preview-state assoc :focused (.getId node))
        (.setColor node (doto (.getColor node) (.darker) (.darker)))
        (.setSize node  (* 2.0 (.size node)))
        (when-not (@preview-state :ego) ;if not in neighbor view, focus also neighbors 
          (reduce (fn [r n]
                    (.setSize n (* 2.0 (.size n)))
                    r) 
                  nil (.. (get-graph-model) (getDirectedGraph) (getNeighbors node))
                  ))
        node
        (catch IllegalArgumentException _)))))


(defn- get-node-detail [node]
  (when node
    (->> (.getAttributeKeys node)
         (map (fn [s] {(keyword s) (.getAttribute node s)}))
         (apply merge)) ))

(defn hovered
  "called when hovered over preview with model coordinates or, 
   if mouse exits preview with :end as parameter
   returns node detail of hovered-over node"
  [coords]
  (let [node (focus-node coords)]
    (.refreshPreview (get-class PreviewController))
    (get-node-detail node)))

(defn update-graph
  "adds missing nodes/edges from subgraph and surrounding and corresponding edges"
  [ids]
  (let [ic (get-class ImportController)
        ws (.getCurrentWorkspace (get-class ProjectController))
        graph (.getDirectedGraph (get-graph-model))
        c (.newContainer (get-class Container$Factory))
        container (.getLoader c)] 
    (doseq [id (keys ids)]
      (if-let [n (.getNode graph id)]
        (do ;if node already in graph, only update surrounding and step attributes 
          (when (not= (.getAttribute n "surrounding")
                      (get-in ids [id :surrounding]))
            (.setAttribute n "step" (Integer. (get-in ids [id :step])))) 
          (.setAttribute n "surrounding" (get-in ids [id :surrounding])))
        (let [nodeDraft (create-node container id (get-in ids [id :details]))]
          (.setValue nodeDraft "surrounding" (get-in ids [id :surrounding]))
          (.setValue nodeDraft "step" (Integer. (get-in ids [id :step])))
          (.addNode container nodeDraft))))
    ;add edges 
    (doseq [id (keys ids)
            edge (get-in ids [id :edges])]
      (let [end (edge :end)
            edge-id (string/join [id "-" end])
            edgeDraft (.. container (factory) (newEdgeDraft edge-id))]
        (when-not (.hasEdge graph edge-id)
          (doto edgeDraft
            (.setSource (.getNode container id))
            (.setTarget (.getNode container end))
            (.setWeight (if (edge :surrounding) 0.5 1.5)))
          (.addEdge container edgeDraft))))
    (.process ic c (DefaultProcessor.) ws) 
    (render-preview)))

(defn export
  "exports file to csv pdf or gexf"
  [[file mode]]
  (if (= mode "csv")
    (let [ks [:id :title :pubyear :authors :source_title :step :surrounding]
          sep "\t"
          header (map name ks) 
          node-details 
          (->> (reduce (fn [out n] ;have to use reduce because of gephi thread locking ... 
                         (conj out (get-node-detail n))) ()
                       (-> (get-graph-model) (.getGraphVisible) (.getNodes)))
               (sort-by :step)
               (map #(map (fn [k] (% k)) ks)))] 
      (->> (cons header node-details)
           (map #(interpose sep %))
           (interpose "\n")
           flatten
           string/join
           (spit file)))
    (let [ec (get-class ExportController)
          ws (.getCurrentWorkspace (get-class ProjectController))
          exporter (.getExporter ec mode)]
      (.setWorkspace exporter ws)
      (.exportFile ec file exporter))))
