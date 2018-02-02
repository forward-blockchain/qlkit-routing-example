(ns qlkit-todo.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [qlkit.core :as ql]
            [qlkit-renderer.core :refer [transact! update-state!] :refer-macros [defcomponent]]
            [goog.dom :refer [getElement]]
            [qlkit-todo.parsers :refer [read mutate remote sync]]
            [qlkit-material-ui.core :refer [enable-material-ui!]]
            [cljs-http.client :as http :refer [post]]
            [cljs.reader :refer [read-string]]))

(enable-console-print!)
(enable-material-ui!)

(defonce app-state (atom {}))

(defcomponent TodoItem
  (query [[:todo/text] [:db/id]])
  (render [{:keys [:todo/text] :as atts} state]
          [:li {:primary-text text
                :right-icon [:span {:on-click (fn []
                                                (transact! [:todo/delete!]))}
                             [:navigation-cancel]]}]))

(defcomponent TodoList
  (query [[:qlkit-todo/todos (ql/get-query TodoItem)]])
  (render [{:keys [:qlkit-todo/todos] :as atts} {:keys [new-todo] :as state}]
          [:div {:max-width 300}
           [:input {:id          :new-todo
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
             [:card [:ol (for [todo todos]
                           [TodoItem todo])]])]))
(defcomponent Counter
  (query [[:counter/counter]])
  (render [{:keys [:counter/counter] :as atts} state]
          (let [inc! (fn [e] (transact! [:counter/inc!]))
                dec! (fn [e] (transact! [:counter/dec!]))]
            [:card
             [:chip (str "Number: " counter)]
             [:icon-button {:on-click inc!} [:navigation-arrow-drop-up]]
             [:icon-button {:on-click dec!} [:navigation-arrow-drop-down]]])))

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
            [:card
             [:text-field {:floating-label-text "Compose"
                           :hint-text           (apply str (repeat 10 "Passersby were amazed at the unusually large amounts of blood. "))
                           :value               display
                           :full-width          true
                           :multi-line          true
                           :on-change           (fn [e] (update-state! assoc :value (.-value (.-target e))))}]
             [:icon-button {:on-click save!   :disabled no-save?}   [:content-save]]
             [:icon-button {:on-click delete! :disabled no-delete?} [:action-delete]]])))

(defn on-hash-change [& _]
  (let [[_ _ uri-hash] (re-matches #"^([^#]+)#([^?]*).*$" js/window.location.href)
        current   (or ({"todo"    :tab/todo
                        "counter" :tab/counter
                        "text"    :tab/text}
                       uri-hash)
                      :tab/todo)]
    (transact! [:tab/current! {:tab/current current}])))

(defcomponent Root
  (query [[:tab/current]
          [:tab/todo    (ql/get-query TodoList)]
          [:tab/counter (ql/get-query Counter)]
          [:tab/text    (ql/get-query Text)]])
  (component-did-mount []
                       (set! js/window.onhashchange on-hash-change)
                       (on-hash-change))
  (render [{:keys [:tab/current
                   :tab/todo
                   :tab/counter
                   :tab/text] :as atts}
           state]
          (let [tab!  (fn [tab] (fn [] (transact! [:tab/current! {:tab/current tab}])))]
            [:div
             [:tabs {:value current}
              [:tab {:value :tab/todo    :label "Todo"    :on-active (tab! :tab/todo)}    (when todo    [TodoList todo])]
              [:tab {:value :tab/counter :label "Counter" :on-active (tab! :tab/counter)} (when counter [Counter counter])]
              [:tab {:value :tab/text    :label "Text"    :on-active (tab! :tab/text)}    (when text    [Text text])]]])))

(defn remote-handler [query callback]
  (go (let [{:keys [status body] :as result} (<! (post "endpoint" {:edn-params query}))]
        (if (not= status 200)
          (print "server error: " body)
          (callback (read-string body))))))

(ql/mount {:component      Root
           :dom-element    (getElement "app")
           :state          app-state
           :remote-handler remote-handler
           :parsers        {:read   read
                            :mutate mutate
                            :remote remote
                            :sync   sync}})
