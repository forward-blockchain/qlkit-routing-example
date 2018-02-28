(ns qlkit-todo.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [qlkit.core :as ql]
            [qlkit-renderer.core :refer [transact! update-state!] :refer-macros [defcomponent]]
            [goog.dom :refer [getElement]]
            [qlkit-todo.parsers :refer [read mutate remote sync]]
            [qlkit-material-ui.core :refer [enable-material-ui!]]
            [cljs-http.client :as http :refer [post]]
            [clojure.string :refer [lower-case]]
            [cljs.reader :refer [read-string]]))

(enable-console-print!)
(enable-material-ui!)

(defcomponent TodoItem
  (query [[:todo/text] [:db/id]])
  (render [{:keys [:todo/text] :as atts} state]
          [:list-item
           [:list-item-avatar [:avatar [:icon.check {:color "green"}]]]
           [:list-item-text {:primary text}]
           [:list-item-secondary-action {:on-click #(transact! [:todo/delete!])}
            [:icon-button [:icon.cancel {:color "black"}]]]]))

(defcomponent TodoList
  (query [[:qlkit-todo/todos (ql/get-query TodoItem)]])
  (render [{:keys [:qlkit-todo/todos] :as atts} {:keys [new-todo] :as state}]
          [:div {:max-width 300
                 :margin    "1rem"}
           [:input {:id          :new-todo
                    :auto-focus  true
                    :full-width  true
                    :value       (or new-todo "")
                    :placeholder "What needs to be done?"
                    :on-key-down (fn [e]
                                   (when (= (.-keyCode e) 13)
                                     (transact! [:todo/new! {:db/id     (random-uuid)
                                                             :todo/text new-todo}])
                                     (update-state! dissoc :new-todo)))
                    :on-change   (fn [e]
                                   (update-state! assoc :new-todo (.-value (.-target e))))}]
           (when (seq todos)
                          [:card [:list (for [todo (sort-by :todo/text todos)]
                             [TodoItem todo])]])]))

(defcomponent Counter
  (query [[:counter/counter]])
  (render [{:keys [:counter/counter] :as atts} state]
          [:card
           [:card-content
            [:typography {:variant :headline} counter]]
           [:card-actions
             [:button {:size :small :variant :raised :on-click #(transact! [:counter/inc!])} "larger"  [:icon.arrow-upward]]
             [:button {:size :small :variant :raised :on-click #(transact! [:counter/dec!])} "smaller" [:icon.arrow-downward]]]]))

(defcomponent Text
  (query [[:text/text]])
  (render [{:keys [:text/text] :as atts} {:keys [value] :as state}]
          (let [display    (cond (not (empty? value)) value
                                 (not (empty? text))  text
                                 :else                "")
                save!      (fn [e]
                             (transact! [:text/save! {:text/text value}])
                             (update-state! dissoc :value))
                delete!    (fn [e]
                             (transact! [:text/delete!])
                             (update-state! dissoc :value))
                no-save?   (or (empty? value) (= text value))
                no-delete? (empty? text)]
            [:card {:margin "1rem"
                    :padding "1rem"}
             [:text-field {:value       display
                           :placeholder (apply str (repeat 10 "All work and no play makes jack a dull boy. "))
                           :full-width  true
                           :multiline   true
                           :on-change   (fn [e] (js/console.log "display" display ) (update-state! assoc :value (.-value (.-target e))))}]
             [:card-actions
              [:button {:size :small :variant :raised :on-click save!   :disabled no-save?}   "save"   [:icon.save]]
              [:button {:size :small :variant :raised :on-click delete! :disabled no-delete?} "delete" [:icon.delete]]]])))

(def tabs {0         "#todo"
           1         "#counter"
           2         "#text"
           "todo"    0
           "counter" 1
           "text"    2})

(defn on-tab-change! [_ tab-index]
  (transact! [:tab/current! {:tab/current tab-index}])
  (set! js/window.location.hash (tabs tab-index)))
  
(defn on-hash-change! [& _]
  (let [[_ _ uri-hash] (re-matches #"^([^#]+)#([^?]*).*$" js/window.location.href)
        uri-hash (if uri-hash (lower-case uri-hash) "todo")]
    (transact! [:tab/current! {:tab/current (tabs uri-hash)}])))

(defcomponent Root
  (query [[:tab/current]
          [:tab/todo    (ql/get-query TodoList)]
          [:tab/counter (ql/get-query Counter)]
          [:tab/text    (ql/get-query Text)]])
  (component-did-mount []
                       (set! js/window.onhashchange on-hash-change!)
                       (on-hash-change!))
  (render [{:keys [:tab/current
                   :tab/todo
                   :tab/counter
                   :tab/text] :as atts}
           state]
          [:div
           [:app-bar {:position "static"}
            [:tabs {:value current :on-change on-tab-change!}
             [:tab {:label "todo"}]
             [:tab {:label "counter"}]
             [:tab {:label "text"}]]]
           (case current
             0 [TodoList todo]
             1 [Counter counter]
             2 [Text text])]))

(defn remote-handler [query callback]
  (go (let [{:keys [status body] :as result} (<! (post "endpoint" {:edn-params query}))]
        (if (not= status 200)
          (print "server error: " body)
          (callback (read-string body))))))

(ql/mount {:component      Root
           :dom-element    (getElement "app")
           :state          (atom {})
           :remote-handler remote-handler
           :parsers        {:read   read
                            :mutate mutate
                            :remote remote
                            :sync   sync}})
