(ns qlkit-todo.parsers
  (:refer-clojure :rename {read core-read})
  (:require [qlkit.core :as ql]))

(def sequencer (atom 2))

(def todos (atom {0 {:db/id 0 :todo/text "walk the dog"}
                  1 {:db/id 1 :todo/text "pay the bills"}
                  2 {:db/id 2 :todo/text "iron the curtains"}}))

(defmulti read (fn [qterm & _] (first qterm)))

(defmethod read :qlkit-todo/todos
  [[_ params :as query-term] env]
  (let [{:keys [todo-id]} params]
    (if todo-id
      [(ql/parse-children query-term (assoc env :todo-id todo-id))]
      (for [id (keys @todos)]
        (ql/parse-children query-term (assoc env :todo-id id)))))) 

(defmethod read :todo/text
  [query-term {:keys [todo-id] :as env}]
  (get-in @todos [todo-id :todo/text]))

(defmethod read :db/id
  [query-term {:keys [todo-id] :as env}]
  (when (@todos todo-id)
    todo-id))

(defmulti mutate (fn [& args] (ffirst args)))

(defmethod mutate :todo/new!
  [[dispatch-key params :as query-term] env]
  (let [{:keys [:db/id :todo/text]} params
        permanent-id                (swap! sequencer inc)]
    (swap! todos
           assoc
           permanent-id
           {:db/id     permanent-id
            :todo/text text})
    [id permanent-id]))

(defmethod mutate :todo/delete!
  [query-term {:keys [todo-id] :as env}]
  (swap! todos dissoc todo-id))
