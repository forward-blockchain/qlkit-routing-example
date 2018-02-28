(ns qlkit-todo.parsers
  (:require [qlkit.core :refer [parse-children parse-children-remote parse-children-sync]]))

(defn dispatch [query-term & _] (first query-term))

(defmulti read   dispatch)
(defmulti remote dispatch)
(defmulti sync   dispatch)
(defmulti mutate dispatch)

(defn remote?
  "Ensure that invisible tabs don't query the server."
  [[dispatch-key params :as query-term] {:keys [tab/current] :as state}]
  (when (or (not current)
            (= dispatch-key current))
    query-term))

(defmethod read :tab/current
  [_ _ {:keys [:tab/current] :as state}]
  (or current 0))

(defmethod mutate :tab/current!
  [[_ params :as query-term] env state-atom]
  (swap! state-atom assoc :tab/current (:tab/current params)))

;; ------------------------------ TodoList component ------------------------------
(defmethod read :tab/todo
  [query-term env state]
  (parse-children query-term env))

(defmethod remote :tab/todo
  [& args]
  (apply remote? args))

(defmethod sync :tab/todo
  [[_ params :as query-term] result env state-atom]
  (parse-children-sync query-term result env))

(defmethod read :qlkit-todo/todos
  [[dispatch-key params :as query-term] env {:keys [:todo/by-id] :as state}]
  (let [{:keys [todo-id]} params]
    (if todo-id
      [(parse-children query-term (assoc env :todo-id todo-id))]
      (for [id (keys by-id)]
        (parse-children query-term (assoc env :todo-id id))))))

(defmethod remote :qlkit-todo/todos
  [query-term state]
  (parse-children-remote query-term))

(defmethod sync :qlkit-todo/todos
  [[_ params :as query-term] result env state-atom]
  (for [{:keys [db/id] :as todo} result]
    (parse-children-sync query-term todo (assoc env :db/id id))))

(defmethod read :db/id
  [query-term {:keys [todo-id] :as env} state]
  (when (get-in state [:todo/by-id todo-id])
    todo-id))

(defmethod remote :db/id
  [query-term state]
  query-term)

(defmethod sync :db/id
  [query-term result {:keys [:db/id] :as env} state-atom]
  (when id
    (swap! state-atom assoc-in [:todo/by-id id :db/id] result)))

(defmethod mutate :todo/new!
  [[dispatch-key params :as query-term] env state-atom]
  (let [{:keys [:db/id]} params]
    (swap! state-atom assoc-in [:todo/by-id id] params)))

(defmethod remote :todo/new!
  [query-term state]
  query-term)

(defmethod mutate :todo/delete!
  [query-term {:keys [todo-id] :as env} state-atom]
  (swap! state-atom update :todo/by-id dissoc todo-id))

(defmethod remote :todo/delete!
  [query-term state]
  query-term)


(defmethod read :todo/text
  [query-term {:keys [todo-id] :as env} state]
  (get-in state [:todo/by-id todo-id :todo/text]))

(defmethod remote :todo/text
  [query-term state]
  query-term)

(defmethod sync :todo/text
  [query-term result {:keys [:db/id] :as env} state-atom]
  (when id
    (swap! state-atom assoc-in [:todo/by-id id :todo/text] result)))



(defmethod sync :todo/new!
  [query-term result env state-atom]
  (let [[temp-id permanent-id] result]
    (swap! state-atom
           update
           :todo/by-id
           (fn [by-id]
             (-> by-id
                 (dissoc temp-id)
                 (assoc permanent-id (assoc (by-id temp-id) :db/id permanent-id)))))))

;; ------------------------------ Text component ----------------------------------
(defmethod read :tab/text
  [query-term env state]
  (parse-children query-term env))

(defmethod remote :tab/text
  [& args]
  (apply remote? args))

(defmethod sync :tab/text
  [query-term result env state-atom]
  (parse-children-sync query-term result env))

(defmethod read :text/text
  [query-term env {:keys [text/text] :as state}]
  text)

(defmethod remote :text/text
  [query-term state]
  query-term)

(defmethod sync :text/text
  [query-term result env state-atom]
  (swap! state-atom assoc :text/text result))

(defmethod remote :text/save!
  [query-term state]
  query-term)

(defmethod remote :text/delete!
  [query-term state]
  query-term)

;; ------------------------------ Counter component -------------------------------
(defmethod read :tab/counter
  [query-term env state]
  (parse-children query-term env))

(defmethod remote :tab/counter
  [& args]
  (apply remote? args))

(defmethod sync :tab/counter
  [query-term result env state-atom]
  (parse-children-sync query-term result env))

(defmethod read :counter/counter
  [query-term env {:keys [counter/counter] :as state}]
  counter)

(defmethod remote :counter/counter
  [query-term state]
  query-term)

(defmethod sync :counter/counter
  [query-term result env state-atom]
  (swap! state-atom assoc :counter/counter result))

(defmethod remote :counter/inc!
  [query-term state]
  query-term)

(defmethod remote :counter/dec!
  [query-term state]
  query-term)
