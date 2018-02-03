# Qlkit Routing Example

This is an example of [qlkit](https://qlkit.org) routing.
It's an app with three tabs; each tab has different data needs and presentation.

The app takes advantage of [qlkit](https://qlkit.org)'s design for efficient rendering and minimal server queries.
Nothing is rendered that isn't part of the selected tab, and no data is fetched that isn't needed for that tab.

To try out the app in your browser, run `lein figwheel` then open your browser at [localhost:3449](http://localhost:3449/).

Please see the [introductory article](https://medium.com/p/79b7b118ddac) for the qlkit design philosophy.

## An app in three parts
The sample app has tabs named *Todo*, *Counter*, and *Text*.

* Todo - a list of things to do
* Counter - just a number with increment and decrement buttons
* Text - a text area that can be saved or deleted on the server

The selected tab contains a child React component with the same name.

Tabs that are not selected don't have a child component.

When a tab is selected, the parser asks the server for the child component's data then the selected tab is rendered.

_Note that changing tabs changes the URL hash to match the tab, and refreshing the URL with a hash correctly navigates to the appropriate tab._

### Parsing

> Thunder is good, thunder is impressive; but it is lightning that does the work.
> -- Mark Twain

Qlkit components are lightning.  They say what data they need in a `query` function and use that data in a `render` function.
That's really all they do.  The logic's in the parsers. (The `query` function returns a vector of `query-terms` the parsers can act on.)

Parsing functions `read` or `mutate` data and optionally create a `remote` query to send off to the server.
Server responses are merged into the app's local state via `sync` functions.

* read - return data for a `query-term`
* mutate - modify app state according to a `query-term` (which usually contains parameters)
* remote - optionally send a `query-term` off to the server
* sync - synchronize with the server's response (by merging them into app state)

When a query is parsed, each `query-term` is passed to the appropriate `read` or `mutate` parser
Then, if there's `remote` parser for the `query-term` it returns the `query-term` that's sent to the server.
The `remote` may return the same `query-term`, a different `query-term`, or nothing at all.

You probably see where this is heading.  The `remote` controls what is sent to the server.  In particular, the `remote` can say "don't ask the server for this".

If there's a `remote` there has to be a `sync`.  If nothing's sent to the server, `sync` won't be called.

After all the `remote` parsing's done, qlkit calls all the `read` parsers again, to make sure the components have the updated data from the server.

#### Turn it off then on again

The sample app has a `read` parser for `:tab/current`, corresponding to the currently-selected tab.

At the top level, there's a `read` (and a `remote`) parser for each tab.
The `remote` parsers have access to the same information as `read`s do, so they can return a remote query or not, based on which tab is selected.  So, if the *Todo* tab is current, the `remote` parsers for the *Counter* and *Text* tabs just return nil and nothing goes to the server.

So the query that's sent to the server always contains only the `query-term`s needed for the selected tab.


### Rendering

Rendering is simple -- as it should be.  There's a top-level component named *Root* that's a container for the tabs.

When the *Root* component's `render` function is called, it uses `:tab/current` to decide what to render.
The current tab is given a child component and the other tabs are empty.  So if the `:tab/current` is `:tab/todo`, the *Todo* component is added to the right tab, and the *Counter* and *Text* tabs are invisible and have no children.

### Summary
Qlkit lets you write simple logic with your app fetching all the data from the server, but you can also write parsers that filter the data by need, and only fetching from the server when you must.



---
_Copyright (c) Conrad Barski. All rights reserved._
_The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php), the same license used by Clojure._




