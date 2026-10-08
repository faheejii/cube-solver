# UX Contract

## History search and filters

- History uses one search field for literal, case-insensitive scramble substrings and displayed official solve times. Exact times use `S.CC` or `M:SS.CC`; `DNF` finds DNF solves; `*` is a simple wildcard over the rendered time (for example `*.21`, `*:21.*`, and `9.*`). A trailing `+` restricts a numeric match to +2 solves. Malformed time-shaped expressions show format guidance instead of becoming scramble searches. The help icon explains this syntax on hover, keyboard focus, and touch.
- Typing is debounced by 300 ms; clearing applies immediately. While IME composition is active, do not issue intermediate queries.
- The committed search query is represented in the URL and restored on refresh/back-forward navigation. The History UI does not expose a penalty filter.
- Changing the query resets the result cursor. Stale responses must not replace the current query's rows. Explicit Load more appends the next cursor page without duplicates.
- Search results affect only the History list. The all-history summary, Timer sidebar, and Statistics modal retain their own unfiltered data scope.
- Each solve row opens through a native button; deletion is a separate action.

## Statistics chart

- Last 50 is the initial range; All solves loads the complete cursor history. Range and Time/Ao5/Ao12 metric selection remain independent while the solution dialog is open.
- Ao5 and Ao12 values are keyboard-accessible buttons in Timer, History, and Statistics summaries. They open a breakdown of the latest 5 or 12 unfiltered solves, including official +2 times and deterministic fastest/slowest exclusions; one DNF is the dropped slowest result and multiple DNFs produce a DNF average. Partial windows explain how many solves remain. Each solve opens its solution above the breakdown, and closing overlays restores focus to the prior row or metric.
- The Best metric in each statistics summary opens a solve that attains the displayed best. In the Statistics modal, this is scoped to the selected range; outside it, it uses all history. If several solves tie, opening any tied solve is valid.
- The summary reports the selected range. Time uses official times; rolling metrics align each point to the solve ending its WCA-style window. A single DNF is removed as the high result; windows with multiple DNFs are DNF; incomplete windows have no plotted point.
- Chart points are keyboard-operable and expose solve number, metric value/status, and timestamp. Activating a point opens that exact solve above the chart. Closing the solution returns focus to the selected chart point without resetting the chart.

## Dialogs and destructive actions

- A solution dialog opened from History or Statistics shows solve number and local date/time context.
- Delete confirmation names the saved time and states that saved Fast and Optimized solutions are also deleted and that the action cannot be undone. Keep the dialog open during the request; on failure, show the error and allow retry or cancel.
- When the Timer displays a saved solve, show its penalty selector and a matching delete button together. The delete button uses the same confirmation flow; after deletion, the timer displays the next most recent saved solve if one exists.
- Unsaved solution previews are never discarded silently when changing solver mode, cross face, or closing the solution. Use an in-app confirmation; Escape cancels that confirmation first.
- Dialogs use `role="alertdialog"` for confirmation, trap focus, provide clear initial focus, and support Escape/cancel where no operation is pending.
