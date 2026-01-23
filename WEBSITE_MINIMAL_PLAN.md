# Hyvexa Website - Homepage Design Spec

A single-page design for Hyvexa, a Hytale parkour server. Premium, warm fantasy aesthetic with gold accents.

**Framework:** Next.js with Tailwind CSS

---

## Brand Identity

**Mood:** Warm, earthy, prestigious, adventure-themed
**Feel:** Like a medieval guild hall meets modern gaming — grounded yet exciting

---

## Color Palette

**Brand**
| Name | Hex | Usage |
|------|-----|-------|
| Gold | `#C9A24D` | Primary buttons, #1 rank, highlights, logo |
| Bronze | `#8C6A2F` | Secondary buttons, #2-3 ranks, borders |
| Stone | `#6F5A3A` | Dividers, inactive states, subtle borders |

**Accent**
| Name | Hex | Usage |
|------|-----|-------|
| Emerald | `#2FAF8F` | Online status, success, new records, progress |
| Emerald Dark | `#1E7F68` | Hover states on emerald elements |

**Background**
| Name | Hex | Usage |
|------|-----|-------|
| Dark Sand | `#2A241C` | Main page background |
| Stone Shadow | `#1E1A14` | Navbar, footer (darkest) |
| Warm Clay | `#3A3126` | Cards, sections, elevated surfaces |

**Text**
| Name | Hex | Usage |
|------|-----|-------|
| Primary | `#F2E6C8` | Headings, important text |
| Secondary | `#CFC2A3` | Body text, descriptions |
| Muted | `#9A907A` | Labels, timestamps, hints |

---

## Typography

| Use | Font | Weight | Style |
|-----|------|--------|-------|
| Logo / Hero title | Cinzel | 700 | Uppercase, wide letter-spacing |
| Section headings | Cinzel | 700 | Uppercase, tracking-wide |
| Body / UI | Nunito | 400, 600, 700 | Normal |
| Numbers / Times | JetBrains Mono | 500 | Tabular figures |

---

## Page Structure

```
┌─────────────────────────────────────────────────────────────┐
│ 1. NAVBAR                                                   │
├─────────────────────────────────────────────────────────────┤
│ 2. HERO (full viewport)                                     │
├─────────────────────────────────────────────────────────────┤
│ 3. STATS BAR                                                │
├─────────────────────────────────────────────────────────────┤
│ 4. LEADERBOARD                                              │
├─────────────────────────────────────────────────────────────┤
│ 5. HOW TO JOIN                                              │
├─────────────────────────────────────────────────────────────┤
│ 6. FOOTER                                                   │
└─────────────────────────────────────────────────────────────┘
```

---

## Section Designs

### 1. Navbar

**Position:** Fixed top
**Background:** Stone Shadow (`#1E1A14`)
**Border:** 1px bottom border in Stone (`#6F5A3A`)
**Height:** ~64px
**Padding:** Horizontal page padding (max-width container)

```
┌─────────────────────────────────────────────────────────────┐
│  [LOGO]  HYVEXA                              [Join Server]  │
└─────────────────────────────────────────────────────────────┘
```

| Element | Specs |
|---------|-------|
| Logo | Small icon or stylized "H", Gold color |
| "HYVEXA" | Gold (`#C9A24D`), Cinzel 700, 1.25rem, tracking-widest |
| Join button | Gold bg, Dark Sand text, rounded-lg, px-6 py-2, subtle shadow |
| Join hover | Slightly brighter gold, enhanced shadow/glow |

---

### 2. Hero Section

**Height:** 100vh (full viewport)
**Background:** Dark Sand (`#2A241C`)
**Texture:** Subtle noise/grain overlay (5-10% opacity)
**Layout:** Vertically and horizontally centered content

```
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│                                                             │
│                        HYVEXA                               │
│              The Hytale Parkour Experience                  │
│                                                             │
│         [Join Server]      [View Leaderboard]               │
│                                                             │
│                                                             │
│                           ↓                                 │
└─────────────────────────────────────────────────────────────┘
```

| Element | Specs |
|---------|-------|
| "HYVEXA" | Gold (`#C9A24D`), Cinzel 700, 6rem (desktop) / 3rem (mobile), uppercase, letter-spacing 0.2em |
| Tagline | Secondary (`#CFC2A3`), Nunito 400, 1.5rem, normal case |
| Gap | 1.5rem between title and tagline, 2.5rem before buttons |
| Primary CTA | Gold bg, Dark Sand text, Nunito 600, px-8 py-3, rounded-lg, shadow-lg |
| Secondary CTA | Transparent bg, Bronze (`#8C6A2F`) 2px border, Bronze text, same size |
| Button gap | 1rem between buttons |
| Scroll indicator | Muted (`#9A907A`), small down arrow, gentle bounce animation |

**Background enhancement options:**
- Subtle gold particle dust floating slowly upward
- Very faint radial gradient (slightly lighter center)
- Abstract geometric shapes in Stone (10% opacity)

---

### 3. Stats Bar

**Background:** Warm Clay (`#3A3126`)
**Border:** 1px top and bottom in Stone (`#6F5A3A`)
**Padding:** py-8
**Layout:** 4 columns, evenly spaced, centered

```
┌─────────────┬─────────────┬─────────────┬─────────────┐
│     12      │   8,432     │     24      │     47      │
│   ONLINE    │    RUNS     │    MAPS     │   RECORDS   │
│      •      │             │             │    TODAY    │
└─────────────┴─────────────┴─────────────┴─────────────┘
```

| Element | Specs |
|---------|-------|
| Numbers | 3rem, Nunito 700 |
| "12" (Online) | Emerald (`#2FAF8F`) |
| Other numbers | Gold (`#C9A24D`) |
| Green dot | 8px circle, Emerald, next to "ONLINE" label |
| Labels | Muted (`#9A907A`), 0.75rem, uppercase, tracking-widest |
| Column dividers | Optional 1px Stone lines between columns |

**Animation:** Numbers count up from 0 when scrolled into view

---

### 4. Leaderboard

**Background:** Dark Sand (page bg)
**Layout:** Centered container, max-width 800px
**Padding:** py-16

```
┌─────────────────────────────────────────────────────────────┐
│                      TOP PLAYERS                            │
│                                                             │
│  ┌────────────────────────────────────────────────────┐    │
│  │  #1   ★  PlayerName                     00:42.831  │    │
│  ├────────────────────────────────────────────────────┤    │
│  │  #2      AnotherPlayer                  00:43.102  │    │
│  ├────────────────────────────────────────────────────┤    │
│  │  #3      ThirdPlayer                    00:44.567  │    │
│  ├────────────────────────────────────────────────────┤    │
│  │  #4      FourthPlayer                   00:45.234  │    │
│  │  ...                                               │    │
│  │  #10     TenthPlayer                    00:52.891  │    │
│  └────────────────────────────────────────────────────┘    │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

| Element | Specs |
|---------|-------|
| Section title | Primary (`#F2E6C8`), Cinzel 700, 1.5rem, uppercase, tracking-wide, centered, mb-8 |
| Table container | Warm Clay bg (`#3A3126`), rounded-lg, overflow hidden |
| Row padding | px-6 py-4 |
| Row dividers | 1px Stone (`#6F5A3A`) |
| #1 row | Gold text (`#C9A24D`), gold left border (4px), star icon before name |
| #2-3 rows | Bronze text (`#8C6A2F`) |
| #4-10 rows | Secondary text (`#CFC2A3`) |
| Rank numbers | Nunito 700, 1rem, fixed width |
| Player names | Nunito 600, 1rem |
| Times | JetBrains Mono 500, right-aligned |
| Row hover | Background lightens slightly (Warm Clay +5% brightness) |

---

### 5. How to Join

**Background:** Warm Clay (`#3A3126`)
**Layout:** Full-width section, centered content container (max-width 600px)
**Padding:** py-16

```
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│                      HOW TO JOIN                            │
│                                                             │
│               1. Launch Hytale                              │
│               2. Click "Direct Connect"                     │
│               3. Enter: play.hyvexa.com                     │
│               4. Start running!                             │
│                                                             │
│                    [Copy Server IP]                         │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

| Element | Specs |
|---------|-------|
| Title | Primary (`#F2E6C8`), Cinzel 700, 1.5rem, uppercase, tracking-wide, centered, mb-8 |
| Steps | Secondary (`#CFC2A3`), Nunito 400, 1.125rem, numbered list, line-height 2 |
| Server IP in step 3 | Gold (`#C9A24D`), JetBrains Mono, slightly larger |
| Copy button | Bronze border (2px), transparent bg, Bronze text, rounded-lg, px-6 py-3, mt-6 |
| Copy hover | Bronze bg, Dark Sand text |
| Copy success state | Emerald border and text, checkmark icon, "Copied!" text for 2 seconds |

---

### 6. Footer

**Background:** Stone Shadow (`#1E1A14`)
**Padding:** py-8
**Layout:** Flex between logo/copyright and social icons

```
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│   HYVEXA                              [Discord]  [Twitter]  │
│   © 2025 Hyvexa                                             │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

| Element | Specs |
|---------|-------|
| "HYVEXA" | Gold (`#C9A24D`), Cinzel 700, 1rem |
| Copyright | Muted (`#9A907A`), Nunito 400, 0.875rem |
| Social icons | 24px, Muted color, hover to Gold |
| Icon gap | 1rem between icons |

---

## Animations

| Element | Animation |
|---------|-----------|
| Page load | Fade in hero content (0.5s ease) |
| Scroll indicator | Gentle bounce (infinite, 2s) |
| Stats numbers | Count up from 0 when in viewport (1s ease-out) |
| Buttons | Scale 1.02 + shadow increase on hover (0.15s ease) |
| Leaderboard rows | Background lighten on hover (0.1s ease) |
| Copy button | State change to "Copied!" with checkmark (0.2s) |
| Section reveals | Fade up slightly when scrolling into view (optional) |

---

## Responsive Breakpoints

### Desktop (1024px+)
- Full layout as designed
- Hero title: 6rem
- Stats: 4 columns

### Tablet (768px - 1023px)
- Hero title: 4rem
- Stats: 2x2 grid
- Leaderboard: slightly narrower

### Mobile (< 768px)
- Hero title: 3rem
- Buttons stack vertically
- Stats: single column, stacked
- Leaderboard: compact rows, smaller text
- Footer: stack vertically, centered

---

## Visual Notes

- **No harsh whites** — warmest text color is Primary (`#F2E6C8`)
- **Gold is the star** — use sparingly for maximum impact (#1 rank, CTAs, logo)
- **Depth through layering** — Stone Shadow → Dark Sand → Warm Clay creates hierarchy
- **Emerald = life/activity** — online status, success states, fresh records
- **Keep it minimal** — let the gold and typography do the work, avoid clutter
