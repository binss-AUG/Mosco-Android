# Design System Document: The Galactic Interface

## 1. Overview & Creative North Star
**Creative North Star: The Cosmic Curator**

This design system is engineered to move beyond the utilitarian "app-grid" and into the realm of high-end digital collectibles and tech-forward editorial. Inspired by the depth of deep space and the precision of modern aerospace tech, the system relies on **The Cosmic Curator** philosophy: every element should feel like a rare object suspended in a vacuum.

Instead of rigid, flat boxes, we utilize intentional layering and glassmorphism to create a "UI Nebula" effect. We break the standard template look by utilizing high-contrast typography and shifting tonal depths that guide the user's eye through light and blur rather than lines and borders.

---

## 2. Colors & Surface Architecture

The palette is anchored in deep neutrals to allow the neon accents to "throb" with energy.

### Color Tokens
*   **Background:** `#0e0e0e` (The Void)
*   **Surface:** `#0e0e0e` (Base)
*   **Primary (Neon):** `#6c29fd` (The Pulse)
*   **Secondary:** `#ffffff` (Starlight Neutral)
*   **Tertiary:** `#08ff00` (Cosmic Flare)

### The "No-Line" Rule
Traditional 1px solid borders are strictly prohibited for defining sections. Content grouping is achieved through **Surface Tiering**. Use `surface_container_low` (`#131313`) to define a section against the `surface` background. 

### Surface Hierarchy & Nesting
Treat the interface as a series of physical plates. 
1.  **Level 0 (Base):** `surface` (`#0e0e0e`)
2.  **Level 1 (Sections):` `surface_container_low` (`#131313`)
3.  **Level 2 (Active Cards):** `surface_container` (`#1a1a1a`)
4.  **Level 3 (Interactive Elements):** `surface_container_high` (`#20201f`)

### The Glass & Gradient Rule
For the Bottom Navigation and Modal Overlays (like filter sheets), implement **Glassmorphism**.
*   **Background:** `surface_variant` (`#262626`) at 60% opacity.
*   **Backdrop Blur:** 20px to 30px.
*   **Signature Texture:** Primary buttons should use a linear gradient from `primary` (`#6c29fd`) to `primary_dim` (`#9547f7`) at a 135-degree angle to create a sense of metallic sheen.

---

## 3. Typography
We utilize **Be Vietnam Pro** for headlines and **Public Sans** for body and labels to maintain a clean, high-tech aesthetic that remains legible even against glowing backgrounds.

*   **Display (lg/md):** Reserved for hero moments or "Rare" item titles. Use `3.5rem` with `-0.02em` tracking to feel editorial and authoritative.
*   **Headline (sm/md):** Used for primary section titles. Always `on_surface` (`#ffffff`) for maximum contrast.
*   **Title (md):** Used for card titles. These are the "labels" of your collection.
*   **Body (md/sm):** Use `on_surface_variant` (`#adaaaa`) for descriptions to create a secondary visual hierarchy that doesn't compete with the neon accents.
*   **Label (sm):** Used for metadata (e.g., "Season Event Compensation"). All-caps or slightly tracked out to feel like technical data.

---

## 4. Elevation & Depth

### The Layering Principle
Depth is achieved through tonal shifts. A "Rewards Box" card should not have a shadow; it should be a `surface_container` block sitting on a `surface` background.

### Ambient Shadows
When an element must "float" (like a Floating Action Button or a Tooltip), use an ambient glow instead of a drop shadow:
*   **Color:** `surface_tint` (`#6c29fd`) at 8% opacity.
*   **Blur:** 24px.
*   **Spread:** 2px.

### The "Ghost Border"
For cards containing rich media (like the aircraft cards), a **Ghost Border** is permitted. 
*   **Value:** `outline_variant` (`#484847`) at 20% opacity.
*   **Inner Glow:** Add a 1px inner box-shadow with `on_surface` at 5% opacity to simulate a light-catching edge on a glass pane.

---

## 5. Components

### Buttons
*   **Primary:** Gradient fill (`primary` to `primary_dim`). **Moderate roundedness (`2`)**. No border. High-contrast `on_primary` text.
*   **Secondary/Ghost:** `outline` token at 20% opacity. Text in `primary`. For "Filter" or "Clear" actions.

### Cards & Lists
*   **Collection Cards:** Forbid divider lines. Separate items using the **Spacious Spacing Scale**.
*   **List Items:** Use `surface_container_low` for the background. On hover/press, transition to `surface_container_highest`.

### Glass Navigation
*   The Bottom Navigation bar must use `surface_container` at 70% opacity with a `backdrop-blur`. The active state is indicated by a `primary` icon and a subtle `primary` glow strip (2px) at the top of the icon.

### Filter Sheets (Modals)
*   Top-rounded corners at **moderate (`2`)**. 
*   Background: `surface_container_high` with a subtle gradient overlay to simulate "Cosmic Dust."

---

## 6. Do's and Don'ts

### Do
*   **Do** use asymmetrical spacing to make the layout feel like a curated gallery rather than a database.
*   **Do** use `primary` (blue) sparingly as a "heartbeat" for the UI—only for interactive or high-value status elements.
*   **Do** ensure all Glassmorphic elements have enough contrast against the content scrolling behind them.

### Don't
*   **Don't** use pure black (`#000000`) for surfaces; it flattens the UI and kills the "Cosmic" depth. Use `surface` (`#0e0e0e`).
*   **Don't** use 100% opaque, high-contrast borders. They break the immersion of the "Glass" metaphor.
*   **Don't** use standard "Drop Shadows." If an element needs lift, use background color shifts or ambient glows.
*   **Don't** clutter the screen. If in doubt, increase vertical white space using the `spacing-8` or `spacing-10` tokens.