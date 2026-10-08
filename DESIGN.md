# Design Context

## Product character

Cube Solver is a focused speedcubing instrument: fast to scan during a session, precise when reviewing a solve, and quiet enough that times and move sequences remain the focus. UI changes should improve solve discovery and continuity without making the app feel like a general-purpose analytics dashboard.

## Visual system

- Keep the dark teal surfaces and the existing light theme. Source all colors from the CSS variables in `frontend/src/styles/foundation.css`.
- Use system sans for interface labels and Space Mono for times, move notation, and other compact numeric data.
- Keep dividers crisp, corners restrained, and cards flat. Avoid decorative gradients, oversized rounded panels, and gratuitous motion.
- Use blue for active navigation and chart interaction, green for best performance, amber for DNFs, and red only for destructive/error states.
- Do not add chart dependencies for the solve trend; use the project-owned responsive SVG.

## Solve-review signature

The solve chart is an index into the user's actual history, not a detached report. A chart point identifies one solve, opens its solution without closing the Statistics modal, and retains the metric, range, selected point, and keyboard focus when the solution closes. The solution context includes its solve number and timestamp.

## Layout and behavior

- History remains a compact, newest-first, cursor-paginated list. Its single search field accepts scramble text or displayed solve times and must not alter Timer sidebar data or Statistics-modal datasets.
- Keep the all-history summary visibly distinct from list filters.
- Statistics range (Last 50 / All solves) and metric (Time / Ao5 / Ao12) are independent controls. Summary values describe the chosen range; rolling points use WCA window semantics and align to the solve ending each window.
- At narrow widths, preserve readable scramble text, reachable actions, and chart controls without horizontal page overflow.
- Destructive and unsaved-work decisions use in-app accessible dialogs rather than browser-native confirmation prompts.

## Change discipline

Prefer small, testable interaction boundaries and existing design tokens. Add a new visual convention only when a repeated workflow requires it, then record it here and in `UX-CONTRACT.md`.
