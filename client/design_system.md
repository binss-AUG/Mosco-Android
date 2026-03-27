# ANDROID PROJECT RULES: THE GALACTIC INTERFACE

You are an expert Android Native Developer and UI/UX Architect. 
Whenever you generate or modify Android XML layouts or Kotlin/Java code for this project, you MUST strictly follow the rules below. Do not deviate under any circumstances.

## 1. COLOR SYSTEM (STRICT ENFORCEMENT)
- **NEVER** use hardcoded HEX colors (e.g., #FFFFFF, #000000, #FF0000).
- **NEVER** use default Android colors (e.g., @android:color/black).
- You MUST use our custom `mosco_` color palette defined in `colors.xml`:
  - Backgrounds/Surfaces: `@color/mosco_surface` (Base), `@color/mosco_surface_container` (Cards/Tabs), `@color/mosco_surface_container_high` (Dialogs/Popups).
  - Primary Brand / Highlights: `@color/mosco_primary` (Neon Purple), `@color/mosco_primary_dim`.
  - Secondary/Success: `@color/mosco_tertiary` (Neon Green).
  - Text: `@color/mosco_on_surface` (Primary Text - White), `@color/mosco_on_surface_variant` (Secondary Text - Gray).
  - Disabled States: `@color/mosco_btn_disabled` (Background), `@color/mosco_text_disabled` (Text).
  - Borders/Lines: `@color/mosco_outline_variant` (Ghost Borders).

## 2. COMPONENT STYLES
- **Buttons:** Do NOT style buttons from scratch. Use defined styles in `styles.xml`:
  - Primary CTA: `style="@style/MoscoButton.Primary"`
  - Secondary/Cancel: `style="@style/MoscoButton.Outlined"`
  - Text Only: `style="@style/MoscoButton.TextOnly"`
  - Small Icon Buttons: `style="@style/MoscoButton.IconGlass"`
- **Chips/Filters:** Always use `style="@style/FilterChipStyle"`.

## 3. SIZING & SPACING
- **No Pixels:** Never use `px`. Use `dp` for dimensions and `sp` for text sizes.
- **Touch Targets:** Any clickable icon or button MUST have a minimum size or padding to reach at least `48dp x 48dp` (Material standard), even if the visual icon is `24dp`.
- **Corner Radius:** Standard corner radius is `8dp` for buttons and cards, `12dp` or `16dp` for larger dialogs.

## 4. IMAGES & ICONS
- Images scaling: When displaying product/item images, always maintain aspect ratio. Use `android:adjustViewBounds="true"` and `android:scaleType="fitCenter"` or `centerInside`. Do NOT stretch images.
- Icons: Assume all icons are VectorDrawables. Set their tint using `app:tint="@color/mosco_on_surface"` or `@color/mosco_primary` to match the theme.

## 5. UI/UX PHILOSOPHY
- **Depth through Tones:** We do not use standard `android:elevation` drop shadows. We create depth by layering lighter surfaces over darker surfaces (e.g., `mosco_surface_container` over `mosco_surface`).
- **Ghost Borders:** Use 1dp strokes of `@color/mosco_outline_variant` to separate elements instead of heavy shadows.