(ns github
  {:clj-kondo/config '{:lint-as {promesa.core/let clojure.core/let}}}
  (:require ["ink" :refer [render Text]]
            ["ink-spinner" :refer [default] :rename {default Spinner}]
            ["ink-quicksearch-input" :refer [QuickSearch]]
            ["node-fetch" :as fetch]
            ["swr" :refer [default] :rename {default useSWR}]
            ["open" :as open]
            [promesa.core :as p]
            [reagent.core :as r]
            [goog.object :as g]))

(defonce repo-data
  {:user (first *command-line-args*)
   :repo (second *command-line-args*)
   :branch (or (-> (next *command-line-args*) (next)) "master")})

(defonce term-size (atom nil))
(defonce selected (r/atom nil))

(defn get-repo [user repo branch]
  (let [url (str "https://api.github.com/repos/" user
                 "/" repo
                 "/git/trees/" branch
                 "?recursive=1")]
    (-> (fetch url)
        (.then #(.json %)))))

(defn search [{:keys [items on-select]}]
  [:> QuickSearch
   {:items items
    :onSelect #(on-select (js->clj (g/get % "value") :keywordize-keys true))

    ;; TODO: somehow set `limit` causes a nonsense error,
    ;; might as well rewrite this component
    ;; :limit (- (:rows @term-size) 8)

    :indicatorComponent
    (fn [props]
      (r/as-element [:> Text {:color "#00FF00"} (if (g/get props "isSelected") "> " "  ")]))
    :highlightComponent
    (fn [props]
      (r/as-element [:> Text {:color "#6C71C4"} (g/get props "children")]))
    :itemComponent
    (fn [props]
      (let [selected? (g/get props "isSelected")
            children (g/get props "children")]
        (r/as-element [:> Text {:color (if selected? "#00FF00" "") } children])))
    :statusComponent
    (fn [props]
      (let [match? (g/get props "hasMatch")
            children (g/get props "children")]
        (r/as-element [:> Text {:color (if match? "#00FF00" "#FF0000") } children])))}])

(defn use-repo-files [{:keys [user repo branch]}]
  (let [q (useSWR "files" #(get-repo user repo branch))
        data (g/get q "data")
        error (g/get q "error")
        loading? (and (not data) (not error))]
    {:data (js->clj data :keywordize-keys true)
     :error error
     :loading? loading?}))

(defn files [{:keys [repo]}]
  (let [{:keys [loading? data]} (use-repo-files repo)
        items (->> (:tree data)
                   (filter #(= (:type %) "blob"))
                   (map #(identity {:value % :label (:path %)})))]
    [:<>
     (when loading?
       [:> Text
        [:> Text {:color "green"} [:> Spinner {:type "dots"}]]
        " Loading"])
     (when data
       [search {:items items
                :on-select #(open (str "https://github.com/" (:user repo) "/" (:repo repo)
                                       "/blob/" (:branch repo)
                                       "/" (:path %)))}])]))

(defn app []
  [:<>
   [:f> files {:repo repo-data}]])

(p/let [get-size (-> (js/import "term-size")
                     (.then #(g/get % "default")))]
  (reset! term-size (js->clj (get-size) :keywordize-keys true))
  (render (r/as-element [app])))
