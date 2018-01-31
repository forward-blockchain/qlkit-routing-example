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
  (query [])
  (render [atts state]
          [:div {:max-width 300}
           [:p "Counter"]]))

(defcomponent Text
  (query [])
  (render [atts state]
          [:div {:max-width 300}
           [:p "Text"]]))

(defcomponent Root
  (query [[:tab/current]
          [:tab/todo    (ql/get-query TodoList)]
          [:tab/counter (ql/get-query Counter)]
          [:tab/text    (ql/get-query Text)]])
  (render [{:keys [:tab/current
                   :tab/todo
                   :tab/counter
                   :tab/text] :as atts}
           state]
          [:div {:max-width 300}
           ({:todo-list [TodoList todo]
             :counter   [Counter  counter]
             :text      [Text     text]}
            current)]))

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
