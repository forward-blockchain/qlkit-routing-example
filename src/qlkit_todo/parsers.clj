(ns qlkit-todo.parsers
  (:refer-clojure :rename {read core-read})
  (:require [qlkit.core :refer [parse-children]]))

(defn dispatch [query-term & _] (first query-term))

(defmulti read   dispatch)
(defmulti remote dispatch)
(defmulti sync   dispatch)
(defmulti mutate dispatch)

;; ------------------------------ TodoList component ------------------------------
(def todos (atom {0 {:db/id 0 :todo/text "walk the dog"}
                  1 {:db/id 1 :todo/text "pay the bills"}
                  2 {:db/id 2 :todo/text "iron the curtains"}}))

(defmethod read :tab/todo
  [query-term env _]
  (parse-children query-term env))

(defmethod read :qlkit-todo/todos
  [[_ params :as query-term] env _]
  (let [{:keys [todo-id]} params]
    (if todo-id
      [(parse-children query-term (assoc env :todo-id todo-id))]
      (for [id (keys @todos)]
        (parse-children query-term (assoc env :todo-id id))))))

(defmethod read :todo/text
  [query-term {:keys [todo-id] :as env} _]
  (get-in @todos [todo-id :todo/text]))

(defmethod read :db/id
  [query-term {:keys [todo-id] :as env} _]
  (when (@todos todo-id)
    todo-id))

(def sequencer (atom 2))

(defmethod mutate :todo/new!
  [[dispatch-key params :as query-term] env _]
  (let [{:keys [:db/id :todo/text]} params
        permanent-id                (swap! sequencer inc)]
    (swap! todos
           assoc
           permanent-id
           {:db/id     permanent-id
            :todo/text text})
    [id permanent-id]))

(defmethod mutate :todo/delete!
  [query-term {:keys [todo-id] :as env} _]
  (swap! todos dissoc todo-id))

;; ------------------------------ Text component ----------------------------------
(def text (atom ""))

(defmethod read :tab/text
  [query-term env _]
  (parse-children query-term env))

(defmethod read :text/text
  [query-term env _]
  @text)

(defmethod mutate :text/save!
  [[dispatch-key params :as query-term] env _]
  (reset! text (:text/text params)))

(defmethod mutate :text/delete!
  [query-term env _]
  (reset! text ""))

;; ------------------------------ Counter component -------------------------------
(def counter (atom 0))

(defmethod read :tab/counter
  [query-term env _]
  (parse-children query-term env))

(defmethod read :counter/counter
  [query-term env _]
  @counter)

(defmethod mutate :counter/inc!
  [query-term env _]
  (swap! counter inc))

(defmethod mutate :counter/dec!
  [query-term env _]
  (swap! counter dec))


