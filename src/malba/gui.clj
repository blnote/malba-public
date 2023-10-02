;; Copyright 2023 blnote.
;; This file is part of MALBA.

(ns malba.gui
  "graphical user interface
   initialization via init sets an atom UI containing the GUI interface functions
   which can be called via (gui/invoke function-name)"
  (:require [clojure.string :as string]
            [malba.preview]))

(import (com.formdev.flatlaf FlatLightLaf)
        (java.io File)
        (java.awt Color Dimension)
        (java.awt Insets BorderLayout GridLayout GridBagLayout GridBagConstraints)
        (java.awt.event KeyEvent InputEvent WindowAdapter ActionListener FocusAdapter FocusEvent ComponentAdapter)

        (javax.swing JCheckBox JButton JFileChooser ButtonGroup JPanel JLabel JRadioButton JTextField JPasswordField JTextField JScrollPane JFrame JToggleButton JTextArea JMenuItem JTabbedPane JProgressBar JMenu JMenuBar UIManager BorderFactory BoxLayout SwingConstants) 
        (javax.swing SwingUtilities KeyStroke)
        (javax.swing.border CompoundBorder EmptyBorder))

(def current-dir (atom (File. (System/getProperty "user.home"))))

(def ^{:private true} UI (atom {})) ;contains all relevant gui update functions

(defn invoke
  "interface to UI update/access functions, see UI structure generated at initialization"
  [fname & args]
  (when-let [f (@UI fname)]
    (if (or (= fname :get-db-info) (= fname :get-params))
      (apply f args)
      (SwingUtilities/invokeLater #(apply f args)))))

(defn- file-dialog
  "opens file dialog for loading (load=true) or saving. 
   optional parameter fname is default filename."
  ([load] (file-dialog load nil))
  ([load ^String fname]
   (let [filechooser (JFileChooser.)]
     (.setCurrentDirectory filechooser @current-dir)
     (when fname (.setSelectedFile filechooser (File. fname)))
     (when (= (if load
                (.showOpenDialog filechooser nil)
                (.showSaveDialog filechooser nil))
              (JFileChooser/APPROVE_OPTION))
    ;user chose file successfully
       (reset! current-dir (.getCurrentDirectory filechooser))
       (.getSelectedFile filechooser)))))


(defn- action-btn-seed [event-dispatch]
  (when-let [selected-file (file-dialog true)]
    (event-dispatch "load-seed" selected-file)))

(defn- action-btn-file-network [event-dispatch]
  (when-let [selected-file (file-dialog true)]
    (event-dispatch "load-network" selected-file)))

(defn- action-btn-cache [event-dispatch]
  (event-dispatch "clear-cache"))

(defn- action-btn-connect [event-dispatch]
  (event-dispatch "db-connect"))
(defn- action-btn-load [event-dispatch]
  (when-let [selected-file (file-dialog true)]
    (event-dispatch "load-session" selected-file)))

(defn- action-btn-save [event-dispatch]
  (when-let [selected-file (file-dialog false "malba.session")]
    (event-dispatch "save-session" selected-file)))

(defn- action-export [event-dispatch ^String mode]
  (when-let [selected-file (file-dialog false (string/join ["malba." mode]))]
    (event-dispatch "export" [selected-file mode])))

(defn- action-btn-reset [event-dispatch]
  (event-dispatch "algo-reset"))
(defn- action-btn-stop [event-dispatch]
  (event-dispatch "algo-stop"))
(defn- action-btn-run [event-dispatch]
  (event-dispatch "algo-run"))
(defn- action-btn-search [event-dispatch]
  (event-dispatch "algo-search"))
(defn- action-btn-step [event-dispatch]
  (event-dispatch "algo-step"))

(defn- action-btn-reset-view [event-dispatch]
  (event-dispatch "view-reset"))
(defn- action-btn-surrounding [event-dispatch btn]
  (event-dispatch "view-surrounding" (.isSelected ^JToggleButton btn)))
(defn- action-btn-layout-frucht [event-dispatch]
  (event-dispatch "layout" "frucht"))
(defn- action-btn-layout-yifan [event-dispatch]
  (event-dispatch "layout" "yifan"))
(defn- action-btn-layout-overlap [event-dispatch]
  (event-dispatch "layout" "overlap"))

(defn- log-text [^JProgressBar progressBar ^JTextArea areaLog msg]
  (when (some? msg)
    (.setBackground progressBar (Color. 209 209 209))

    (doto areaLog
      (.setCaretPosition (.. areaLog (getDocument) (getLength)))
      (.replaceSelection (string/join ["\n" msg]))
      (.setCaretPosition (.. areaLog (getDocument) (getLength))))))

(defn- log-error [^JProgressBar progressBar ^JTextArea areaLog msg]
  (.setBackground progressBar (Color/RED))
  (doto areaLog
    (.setCaretPosition (.. areaLog (getDocument) (getLength)))
    (.replaceSelection (string/join ["\n>>> Error: " msg]))
    (.setCaretPosition (.. areaLog (getDocument) (getLength)))))


(defn- initComponents [{:keys [event-dispatch version exit_on_close preview]}]
  (try
    (UIManager/setLookAndFeel (FlatLightLaf.))
    (UIManager/put "MenuItem.minimumIconSize" (Dimension. 0 0))
    (catch Exception e  (binding [*out* *err*]
                          (println (.getMessage e)) )))
  (let [buttonGroupNetwork (new ButtonGroup)
        panelControl (new JPanel)
        panelSeed (new JPanel)
        lblSeed (new JLabel)
        btnSeed (new JButton)
        panelNetwork (new JPanel)
        panelFileNetwork (new JPanel)
        btnFileNetwork (new JButton)
        radioFile (new JRadioButton)
        radioDB (new JRadioButton)
        panelURL (new JPanel)
        lblUrl (new JLabel)
        txtDBadr (new JTextField)
        panelUser (new JPanel)
        jPanel3 (new JPanel)
        lblUser (new JLabel)
        txtUser (new JTextField)
        jPanel2 (new JPanel)
        lblPassword (new JLabel)
        txtPwd (new JPasswordField)
        panelPwd (new JPanel)
        btnCache (new JButton)
        btnConnect (new JButton)
        panelParams (new JTabbedPane)
        panelParamsMalba (new JPanel)
        chkDCout (new JCheckBox)
        chkBC (new JCheckBox)
        chkDCin (new JCheckBox)
        txtDCout (new JTextField 3)
        txtBC (new JTextField 3)
        txtDCin (new JTextField 3)
        lblSubSize (new JLabel)
        txtSubSize (new JTextField 4)
        lblMaxParents (new JLabel)
        txtMaxParents (new JTextField 4)
        panelAlgo (new JPanel)
        panelAlgoButtons (new JPanel)
        btnSearch (new JButton)
        btnRun (new JButton)
        btnStep (new JButton)
        btnStop (new JButton)
        btnReset (new JButton)
        progressBar (new JProgressBar)
        jScrollPanel (new JScrollPane)
        areaLog (new JTextArea)
        panelStatus (new JPanel)
        lblStatusDB (new JLabel)
        lblStatusCache (new JLabel)
        panelResults (new JPanel)
        canvas (new JPanel)
        panelGraphButtons (new JPanel)
        btnLayoutYifan (new JButton)
        btnLayoutFrucht (new JButton)
        btnLayoutOverlap (new JButton)
        btnSurrounding (new JToggleButton)
        btnResetView (new JButton)
        panelGraphStatus (new JPanel)
        lblGraphStatus (new JLabel)
        lblParams (new JLabel)
        jMenuBar2 (new JMenuBar)
        menuSession (new JMenu)
        btnLoad (new JMenuItem)
        btnSave (new JMenuItem)
        menuExport (new JMenu)
        btnExportResults (new JMenuItem)
        btnExportFile (new JMenuItem)
        btnExportPdf (new JMenuItem)

        frame (new JFrame)

        control-width 350

        action-fn (fn ;helper to generate click events
                    ([f] (proxy [ActionListener] []
                           (actionPerformed [_]
                             (f event-dispatch))))
                    ([f param] (proxy [ActionListener] []
                                 (actionPerformed [_]
                                   (f event-dispatch param)))))
        ;select everything when focusing in text boxes:
        focus-listener (proxy [FocusAdapter] []
                         (focusGained [^FocusEvent evt]
                           (let [tf ^JTextField (.getComponent evt)]
                             (.selectAll tf))))
        set-db-mode (fn [^Boolean bool]
                      (->> [btnCache btnConnect txtDBadr txtUser txtPwd lblUrl lblUser lblPassword]
                           (map #(.setEnabled ^java.awt.Component % bool))
                           (doall))
                      (.setEnabled btnFileNetwork (not bool))
                      (.setSelected radioFile (not bool))
                      (.setSelected radioDB bool)
                      (when bool (.setText radioFile "from file:")))
        {:keys [preview-panel ;generate preview panel
                refresh-fn reset-fn show-details-fn]} (malba.preview/init-preview preview event-dispatch)
        ]

    ;;seed panel
    (doto lblSeed
      (.setText "no file chosen"))

    (doto btnSeed
      (.setText "Choose File")
      (.setFocusPainted false)
      (.addActionListener (action-fn action-btn-seed)))

    (doto panelSeed
      (.setBorder (BorderFactory/createTitledBorder "Seed"))
      (.setMaximumSize (Dimension. control-width 50))
      (.setMinimumSize (Dimension. control-width 50))
      (.setPreferredSize (Dimension. control-width 50))
      (.setLayout (BorderLayout.))
      (.add lblSeed BorderLayout/CENTER)
      (.add btnSeed BorderLayout/EAST))

    ;;network panel
    (doto radioFile
      (.setSelected true)
      (.setText "from file:")
      (.addActionListener (proxy [ActionListener] []
                            (actionPerformed [_]
                              (set-db-mode false)))))
    (doto btnFileNetwork
      (.setText "Choose File")
      (.setFocusPainted false)
      (.addActionListener (action-fn action-btn-file-network)))
    (doto panelFileNetwork
      (.setLayout (BorderLayout.))
      (.add radioFile BorderLayout/CENTER)
      (.add btnFileNetwork BorderLayout/EAST))

    (doto radioDB
      (.setText "from database:")
      (.addActionListener (proxy [ActionListener] []
                            (actionPerformed [_]
                              (set-db-mode true)))))

    (doto buttonGroupNetwork
      (.add radioFile)
      (.add radioDB))
    (doto lblUrl
      (.setText "url:")
      (.setMaximumSize (Dimension. 25 16))
      (.setMinimumSize (Dimension. 25 16))
      (.setPreferredSize (Dimension. 35 16)))
    (doto txtDBadr
      (.addFocusListener focus-listener))
    (doto panelURL
      (.setLayout (new BorderLayout 3 0))
      (.add lblUrl BorderLayout/WEST)
      (.add txtDBadr BorderLayout/CENTER))
    (doto lblUser
      (.setText "user:")
      (.setPreferredSize (Dimension. 35 16)))
    (doto txtUser
      (.addFocusListener focus-listener))
    (doto jPanel3
      (.setLayout (BorderLayout. 3 0))
      (.add lblUser BorderLayout/WEST)
      (.add txtUser BorderLayout/CENTER))
    (doto lblPassword
      (.setText "password:"))
    (doto txtPwd
      (.addFocusListener focus-listener)
      (.addActionListener (action-fn action-btn-connect)))
    (doto jPanel2
      (.setLayout (BorderLayout. 3 0))
      (.add lblPassword BorderLayout/WEST)
      (.add txtPwd BorderLayout/CENTER))
    (doto panelUser
      (.setLayout (GridLayout. 1 2 3 0))
      (.add jPanel3)
      (.add jPanel2))
    (doto btnCache
      (.setText "Clear Cache")
      (.addActionListener (action-fn action-btn-cache)))
    (doto btnConnect
      (.setText "Connect")
      (.addActionListener (action-fn action-btn-connect)))
    (doto panelPwd
      (.setLayout (GridLayout. 1 0 3 0))
      (.add btnCache)
      (.add btnConnect))
    (doto panelNetwork
      (.setBorder (BorderFactory/createTitledBorder "Network"))
      (.setMaximumSize (Dimension. control-width 150))
      (.setMinimumSize (Dimension. control-width 150))
      (.setLayout (GridLayout. 5 0 0 3))
      (.add panelFileNetwork)
      (.add radioDB)
      (.add panelURL)
      (.add panelUser)
      (.add panelPwd))

    ;;panel params (tabbed pane)
    (doto chkDCout
      (.setText "DCout:")
      (.setSelected true)
      (.setToolTipText "a reference cited by subgraph x times")
      (.addActionListener (proxy [ActionListener] []
                            (actionPerformed [_]
                              (.setEnabled txtDCout (.isSelected chkDCout))))))
    (doto txtDCout
      (.setHorizontalAlignment JTextField/RIGHT)
      (.setToolTipText "a reference cited by subgraph x times"))
    (doto chkBC
      (.setText "BC:")
      (.setSelected true)
      (.setToolTipText "share of references of a citing publication which overlap with subgraph's references")
      (.addActionListener (proxy [ActionListener] []
                            (actionPerformed [_]
                              (.setEnabled txtBC (.isSelected chkBC))))))
    (doto txtBC
      (.setHorizontalAlignment JTextField/RIGHT)
      (.setToolTipText "share of references of a citing publication which overlap with subgraph's references"))
    (doto chkDCin
      (.setText "DCin:")
      (.setSelected true)
      (.setToolTipText "share of references of a citing publication that are in subgraph")
      (.addActionListener (proxy [ActionListener] []
                            (actionPerformed [_]
                              (.setEnabled txtDCin (.isSelected chkDCin))))))
    (doto txtDCin
      (.setHorizontalAlignment JTextField/RIGHT)
      (.setToolTipText "share of references of a citing publication that are in subgraph"))
    (doto lblSubSize
      (.setText "max. graph size:"))
    (doto txtSubSize
      (.setHorizontalAlignment JTextField/RIGHT)
      (.setToolTipText "size of subgraph at which algorithm terminates"))
    (doto lblMaxParents
      (.setText "max. parents:"))
    (doto txtMaxParents
      (.setHorizontalAlignment JTextField/RIGHT)
      (.setToolTipText "parents of nodes having more parents will be ignored in candidate search
                            to avoid large candidate sets"))


    (let [grid-bag-constr (fn
                            [x y w]
                            (GridBagConstraints. x y w 1 1 1 GridBagConstraints/WEST GridBagConstraints/NONE (Insets. 0 0 0 6) 0 0))]
      (doto panelParamsMalba
        (.setLayout (GridBagLayout.))
        (.setBorder (BorderFactory/createEmptyBorder 8 4 4 8))
        (.add chkDCout (grid-bag-constr 0 0 2))
        (.add txtDCout (grid-bag-constr 2 0 1))
        (.add chkDCin (grid-bag-constr 0 1 2))
        (.add txtDCin (grid-bag-constr 2 1 1))
        (.add chkBC (grid-bag-constr 0 2 2))
        (.add txtBC (grid-bag-constr 2 2 1))
        (.add lblSubSize (grid-bag-constr 3 0 2))
        (.add txtSubSize (grid-bag-constr 5 0 1))
        (.add lblMaxParents (grid-bag-constr 3 1 2))
        (.add txtMaxParents (grid-bag-constr 5 1 1))))

    (doto panelParams
      (.setFocusable false)
      (.setMaximumSize (Dimension. control-width 120))
      (.setPreferredSize (Dimension. control-width 120))
      (.addTab "MALBA" panelParamsMalba)
      #_(.addTab "Future Algo" panelParamsScore))

    ;;panel algo: 
    (doto btnSearch
      (.setText "Search")
      (.setMargin (Insets. 2 1 1 2))
      (.setVerticalTextPosition SwingConstants/BOTTOM)
      (.addActionListener (action-fn action-btn-search))
      (.setToolTipText "search for parameters that generate largest subgraph (starting from current subgraph)"))
    (doto btnRun
      (.setText "Run")
      (.setMargin (Insets. 2 1 1 2))
      (.setVerticalTextPosition SwingConstants/BOTTOM)
      (.addActionListener (action-fn action-btn-run))
      (.setToolTipText "run algorithm with given parameters (starting from current subgraph)"))
    (doto btnStep
      (.setText "Step")
      (.setMargin (Insets. 2 1 1 2))
      (.setVerticalTextPosition SwingConstants/BOTTOM)
      (.addActionListener (action-fn action-btn-step))
      (.setToolTipText "execute one cylce of algorithm"))
    (doto btnStop
      (.setText "Stop")
      (.setMargin (Insets. 2 1 1 2))
      (.setVerticalTextPosition SwingConstants/BOTTOM)
      (.addActionListener (action-fn action-btn-stop))
      (.setToolTipText "interrupt algorithm"))
    (doto btnReset
      (.setText "Reset")
      (.setMargin (Insets. 2 1 1 2))
      (.setVerticalTextPosition SwingConstants/BOTTOM)
      (.addActionListener (action-fn action-btn-reset))
      (.setToolTipText "reset algorithm state and subgraph to initial seed"))

    (doto panelAlgoButtons
      (.setLayout (GridLayout. 1 0 2 0))
      (.setBorder (BorderFactory/createEmptyBorder 0 0 5 0))
      (.add btnSearch)
      (.add btnRun)
      (.add btnStep)
      (.add btnStop)
      (.add btnReset))

    (doto progressBar
      (.setForeground (Color. 153 204 255))
      (.setOpaque true))
    (UIManager/put "ProgressBar.cycleTime" 5000)

    (doto panelAlgo
      (.setLayout (BorderLayout.))
      (.setMinimumSize (Dimension. control-width 50))
      (.setMaximumSize (Dimension. control-width 50))
      (.setBorder (BorderFactory/createEmptyBorder 4 4 4 4))
      (.add panelAlgoButtons BorderLayout/CENTER)
      (.add progressBar BorderLayout/SOUTH))

    ;;panel for logging
    (doto areaLog
      (.setBackground Color/WHITE)
      (.setColumns 20)
      (.setRows 5)
      (.setBorder (BorderFactory/createLineBorder (Color. 153 153 153)))
      (.setWrapStyleWord true)
      (.setLineWrap true)
      (.setEditable false)
      (.append "Welcome. Please choose a seed and network source or load a session file."))

    (doto jScrollPanel
      (.setBorder (BorderFactory/createEmptyBorder 3 3 3 3))
      (.setHorizontalScrollBarPolicy javax.swing.ScrollPaneConstants/HORIZONTAL_SCROLLBAR_NEVER)
      (.setViewportView areaLog))

    ;;status panel
    (doto lblStatusDB
      (.setHorizontalAlignment SwingConstants/LEFT)
      (.setText "not initialized")
      (.setBorder (EmptyBorder. 0 0 0 5)))
    (doto lblStatusCache
      (.setHorizontalAlignment SwingConstants/RIGHT)
      (.setText "")
      (.setToolTipText "Cached citation info: (cited/cited-by(/details)).")
      (.setBorder (EmptyBorder. 0 0 0 5)))

    (doto panelStatus
      (.setLayout (BorderLayout.))
      (.setMinimumSize (Dimension. control-width 50))
      (.setMaximumSize (Dimension. control-width 50))
      (.setBorder (CompoundBorder. (EmptyBorder. 0 3 0 3) (BorderFactory/createEtchedBorder)))
      (.add lblStatusDB BorderLayout/WEST)
      (.add lblStatusCache BorderLayout/EAST))

    ;;add everything to control panel (the left part of the window)
    (doto panelControl
      (.setBorder (BorderFactory/createEmptyBorder 0 3 0 0))
      (.setMaximumSize (Dimension. control-width 32767))
      (.setMinimumSize (Dimension. control-width 135))
      (.setPreferredSize (Dimension. control-width 600))
      (.setLayout (BoxLayout. panelControl BoxLayout/Y_AXIS))
      (.add panelSeed)
      (.add panelNetwork)
      (.add panelParams)
      (.add panelAlgo)
      (.add jScrollPanel)
      (.add panelStatus))

    ;;panel Results
    
    (doto panelGraphStatus
      (.setLayout (BorderLayout.))
      (.setBorder (BorderFactory/createEmptyBorder 1 5 1 5))
      (.setMinimumSize (Dimension. 100 25))
      (.setPreferredSize (Dimension. 710 25))
      (.add lblGraphStatus BorderLayout/WEST)
      (.add lblParams BorderLayout/EAST)
      #_(.add (Box.Filler. (Dimension. 0 0) (Dimension. 0 0) (Dimension. 32767 32767)) BorderLayout/CENTER))
    
    ;;panel Preview
    (doto canvas
      (.setBackground Color/WHITE)
      (.setBorder (BorderFactory/createEmptyBorder 5 5 5 5))
      (.setRequestFocusEnabled false)
      (.setLayout (BorderLayout.))
      (.addComponentListener (proxy [ComponentAdapter] [] ;add preview panel
                               (componentShown [_] (refresh-fn))
                               (componentResized [_] (refresh-fn))))
      (.add ^JPanel preview-panel BorderLayout/CENTER))

    (doto btnLayoutYifan
      (.setText "Yifan Hu Layout")
      (.setMargin (Insets. 2 5 2 5))
      (.addActionListener (action-fn action-btn-layout-yifan)))
    (doto btnLayoutFrucht
      (.setText "Frucht.-Rein. Layout")
      (.setMargin (Insets. 2 5 2 5))
      (.addActionListener (action-fn action-btn-layout-frucht)))
    (doto btnLayoutOverlap
      (.setText "No Overlap")
      (.setMargin (Insets. 2 5 2 5))
      (.addActionListener (action-fn action-btn-layout-overlap)))
    (doto btnSurrounding
      (.setText "Show Surroundings")
      (.setMargin (Insets. 2 5 2 5))
      (.addActionListener (action-fn action-btn-surrounding btnSurrounding)))
    (doto btnResetView
      (.setText "Reset View")
      (.setMargin (Insets. 2 5 2 5))
      (.addActionListener (action-fn action-btn-reset-view)))

    (doto panelGraphButtons
      (.setLayout (GridLayout. 1 0))
      (.add btnLayoutYifan)
      (.add btnLayoutOverlap)
      (.add btnLayoutFrucht)
      (.add btnSurrounding)
      (.add btnResetView))

    (doto panelResults
      (.setLayout (BorderLayout.))
      (.setBorder (BorderFactory/createTitledBorder "Results"))
      (.add panelGraphStatus BorderLayout/NORTH)
      (.add canvas BorderLayout/CENTER)
      (.add panelGraphButtons BorderLayout/SOUTH))

    ;;MENU
    (doto btnLoad
      (.setText "Load")
      (.addActionListener (action-fn action-btn-load))
      (.setAccelerator (KeyStroke/getKeyStroke KeyEvent/VK_L InputEvent/CTRL_DOWN_MASK)))
    (doto btnSave
      (.setText "Save")
      (.addActionListener (action-fn action-btn-save))
      (.setAccelerator (KeyStroke/getKeyStroke KeyEvent/VK_S InputEvent/CTRL_DOWN_MASK)))

    (doto menuSession
      (.setText "Session")
      (.add btnLoad)
      (.add btnSave))

    (doto btnExportResults
      (.setText "Results to CSV")
      (.addActionListener (action-fn action-export "csv")))

    (doto btnExportPdf
      (.setText "Graph to PDF")
      (.addActionListener (action-fn action-export "pdf")))

    (doto btnExportFile
      (.setText "Graph to GEXF (Gephi)")
      (.addActionListener (action-fn action-export "gexf")))
    (doto menuExport
      (.setText "Export")
      (.add btnExportResults)
      (.add btnExportFile)
      (.add btnExportPdf))


    (doto jMenuBar2
      (.add menuSession)
      (.add menuExport))

    ;;putting it all together
    (doto (.getContentPane frame)
      (.add panelControl BorderLayout/WEST)
      (.add panelResults BorderLayout/CENTER))

    (doto frame
      (.setTitle (string/join ["MALBA Version " version]))

      (.setDefaultCloseOperation  (if exit_on_close
                                    javax.swing.WindowConstants/EXIT_ON_CLOSE
                                    javax.swing.WindowConstants/DISPOSE_ON_CLOSE))
      (.addWindowListener (proxy [WindowAdapter] []
                            (windowClosing [evt] (event-dispatch "window-close"))))
      (.setJMenuBar jMenuBar2)
      (.pack)
      (.setVisible true))

    ;;reset UI atom with a set of functions to update UI
    (let [enable-algo-btns (fn [^Boolean bool]
                             (->> (.getComponents panelAlgoButtons)
                                  (map #(.setEnabled ^java.awt.Component % bool))
                                  (dorun))
                             (.setEnabled btnStop (not bool)))
          enable-result-btns (fn [^Boolean bool]
                               (->> (.getComponents panelGraphButtons)
                                    (map #(.setEnabled ^java.awt.Component % bool))
                                    (dorun)))

          set-initialized (fn [^Boolean bool]
                            (.setEnabled btnSave bool)
                            (.setEnabled menuExport bool)
                            (enable-algo-btns bool)
                            (enable-result-btns bool)
                            (.setEnabled btnStop false))]
      (set-initialized false)
      (reset! UI {:refresh-preview refresh-fn ;preview event functions
                  :reset-preview reset-fn
                  :show-details show-details-fn
                  :task-started #(do (.setBackground progressBar (Color. 209 209 209))
                                     (.setIndeterminate progressBar true)
                                     (when % (.setText lblStatusDB %)))
                  :task-stopped #(do (.setIndeterminate progressBar false)
                                     (when % (.setText lblStatusDB %)))
                  :set-params (fn [params]
                                (condp = (get params :name)
                                  "MALBA" (let [{:keys [dc-in bc dc-out
                                                        max-subgraph-size
                                                        max-parents-of-shared-refs]} params]
                                            (.setSelected chkBC (some? bc))
                                            (.setEnabled txtBC (some? bc))
                                            (.setSelected chkDCin (some? dc-in))
                                            (.setEnabled txtDCin (some? dc-in))
                                            (.setSelected chkDCout (some? dc-out))
                                            (.setEnabled txtDCout (some? dc-out))
                                            (when bc (.setText txtBC (str bc)))
                                            (when dc-in (.setText txtDCin (str dc-in)))
                                            (when dc-out (.setText txtDCout (str dc-out)))
                                            (.setText txtSubSize (str max-subgraph-size))
                                            (.setText txtMaxParents (str max-parents-of-shared-refs))
                                            (.setSelectedIndex panelParams 0))
                                  "SCORE" (.setSelectedIndex panelParams 1)))
                  :get-params #(condp = (.getSelectedIndex panelParams)
                                 0 {:name "MALBA"
                                    :dc-in (when (.isSelected chkDCin)
                                             (-> txtDCin .getText (string/replace "," ".") parse-double))
                                    :dc-out (when (.isSelected chkDCout)
                                              (-> txtDCout .getText parse-long))
                                    :bc (when (.isSelected chkBC)
                                          (-> txtBC .getText (string/replace "," ".") parse-double))
                                    :max-subgraph-size (-> txtSubSize .getText parse-long)
                                    :max-parents-of-shared-refs (-> txtMaxParents .getText parse-long)}
                                 1 {:name "FUTURE"})
                  :set-seed #(.setText lblSeed %)
                  :set-network-file #(do (.setText radioFile %) (set-db-mode false))
                  :set-db-info (fn [{:keys [url user password]}]
                                 (.setText txtDBadr url) (.setText txtUser user) (.setText txtPwd password)
                                 (set-db-mode true))
                  :get-db-info (fn [] {:url (.getText txtDBadr) :user (.getText txtUser) :password (.getText txtPwd)})
                  :set-initialized set-initialized
                  :enable-algo-btns enable-algo-btns
                  :enable-result-btns enable-result-btns
                  :log-cache #(when % (.setText lblStatusCache %))
                  :log-status #(when % (.setText lblStatusDB %))
                  :log-graph-size #(when % (.setText lblGraphStatus %))
                  :log-parameters #(when % (.setText lblParams %))
                  :log-text (partial log-text progressBar areaLog)
                  :log-error (partial log-error progressBar areaLog)}))))

(defn init [params]
  (SwingUtilities/invokeAndWait #(initComponents params)))
