# Hyvexa Website - Technical & Design Plan

## Overview

A website for Hyvexa, a Hytale parkour server, designed to:
- Rank for keywords like "hytale parkour", "hytale server", "hytale parkour server"
- Display real-time data from the server database (leaderboards, stats, players)
- Capture the visual identity of Hytale + parkour aesthetics

---

## Technical Approach

### Recommended Stack

| Layer | Technology | Reasoning |
|-------|------------|-----------|
| Framework | **Next.js 14+ (App Router)** | SSR/SSG for SEO, React ecosystem, API routes for database |
| Styling | **Tailwind CSS** | Rapid styling, easy theming, responsive |
| Database | **MySQL (existing)** | Direct connection to your server's database |
| ORM | **Prisma** or **Drizzle** | Type-safe queries, easy MySQL integration |
| Hosting | **Vercel** | Vercel for simplicity |
| Analytics | **Plausible** or **Umami** | Privacy-friendly, see what keywords bring traffic |

### Architecture

```
┌─────────────────────────────────────────────────────────┐
│                      Next.js App                        │
├─────────────────────────────────────────────────────────┤
│  Pages (SSR/SSG)           │  API Routes               │
│  ├── / (home)              │  ├── /api/leaderboard     │
│  ├── /leaderboard          │  ├── /api/players/[id]    │
│  ├── /players              │  ├── /api/stats           │
│  ├── /maps                 │  └── /api/server-status   │
│  ├── /play (how to join)   │                           │
│  └── /about                │                           │
├─────────────────────────────────────────────────────────┤
│                     Prisma ORM                          │
├─────────────────────────────────────────────────────────┤
│              MySQL Database (your server)               │
└─────────────────────────────────────────────────────────┘
```

### Database Integration

Connect to your existing parkour database read-only:

```ts
// Example: Fetch leaderboard data
const leaderboard = await prisma.parkourRuns.findMany({
  orderBy: { time: 'asc' },
  take: 100,
  include: { player: true, map: true }
});
```

**Security considerations:**
- Create a read-only MySQL user for the website
- Never expose write operations to the public API
- Use connection pooling (PlanetScale proxy or PgBouncer equivalent)
- Cache frequently-accessed data (leaderboards) with ISR (Incremental Static Regeneration)

### SEO Strategy

**On-page SEO:**
- Server-side render all pages (Next.js SSR/SSG)
- Semantic HTML (`<main>`, `<article>`, `<nav>`)
- Meta tags per page with target keywords
- OpenGraph images for social sharing

**Target keywords:**
| Page | Primary Keyword | Secondary Keywords |
|------|-----------------|-------------------|
| Home | hytale parkour server | hytale server, play hytale parkour |
| Leaderboard | hytale parkour leaderboard | best hytale parkour times |
| Maps | hytale parkour maps | hytale custom maps |
| Play | how to play hytale parkour | join hytale server |

**Content strategy:**
- Individual pages for each map (long-tail SEO)
- Player profile pages (user-generated content density)
- Detailed "How to Play" guide page

**Technical SEO:**
- `sitemap.xml` auto-generated
- `robots.txt` configured
- Structured data (JSON-LD) for gaming content
- Fast Core Web Vitals (Next.js + Vercel handles this well)

---

## Design Approach

### Visual Identity

**Hyvexa aesthetic:**
- Warm, earthy tones grounded in fantasy/adventure
- Gold accents evoking achievement and prestige
- Stone and sand textures for depth
- Emerald highlights for progress and success

**Parkour energy:**
- Dynamic angles and movement suggestion
- Vertical layouts suggesting climbing/jumping
- Progress bars and timers as visual motifs
- Gold shimmer effects on achievements/records

### Color Palette

**Brand**
| Name | Hex | Usage |
|------|-----|-------|
| Gold | `#C9A24D` | Primary actions, buttons, rank #1, highlights |
| Bronze | `#8C6A2F` | Secondary actions, rank badges, borders |
| Stone | `#6F5A3A` | Tertiary elements, dividers, inactive states |

**Accent**
| Name | Hex | Usage |
|------|-----|-------|
| Emerald | `#2FAF8F` | Success states, progress bars, new records, online status |
| Emerald Dark | `#1E7F68` | Hover states, active indicators |

**Background**
| Name | Hex | Usage |
|------|-----|-------|
| Dark Sand | `#2A241C` | Page background |
| Stone Shadow | `#1E1A14` | Navbar, footer, deepest background |
| Warm Clay | `#3A3126` | Cards, surfaces, modals |

**Text**
| Name | Hex | Usage |
|------|-----|-------|
| Primary | `#F2E6C8` | Headings, important text |
| Secondary | `#CFC2A3` | Body text, descriptions |
| Muted | `#9A907A` | Timestamps, hints, disabled text |

**Tailwind config:**
```js
colors: {
  brand: {
    gold: '#C9A24D',
    bronze: '#8C6A2F',
    stone: '#6F5A3A',
  },
  accent: {
    emerald: '#2FAF8F',
    'emerald-dark': '#1E7F68',
  },
  bg: {
    sand: '#2A241C',
    shadow: '#1E1A14',
    clay: '#3A3126',
  },
  text: {
    primary: '#F2E6C8',
    secondary: '#CFC2A3',
    muted: '#9A907A',
  },
}
```

### Typography

- **Headings:** Bold, fantasy-inspired sans-serif (e.g., "Cinzel", "Philosopher", "Almendra")
- **Body:** Warm, readable sans-serif (e.g., "Nunito", "Source Sans Pro")
- **Monospace:** For times/stats (e.g., "JetBrains Mono", "Fira Code")

### Key UI Components

**Leaderboard table:**
- Rank badges: #1 Gold (`#C9A24D`), #2-3 Bronze (`#8C6A2F`), rest Stone (`#6F5A3A`)
- Player avatars (Hytale-style if available, or generated)
- Time displayed prominently with milliseconds
- Map thumbnail beside each entry
- Gold glow on hover for top ranks

**Player cards:**
- Warm Clay (`#3A3126`) card background with subtle Stone border
- Stats grid (total runs, best times, XP)
- Emerald progress bars for XP/completion
- Achievement badges with Gold highlights

**Map cards:**
- Difficulty indicator: Easy (Emerald), Medium (Gold), Hard (Bronze)
- Warm Clay background with thumbnail
- Completion count with Emerald accent
- World record holder highlighted in Gold

**Server status widget:**
- Emerald dot for online, Stone for offline
- Player count in Primary text
- Gold "Join Now" CTA button

**Buttons:**
- Primary: Gold background, Dark Sand text
- Secondary: Bronze border, transparent background
- Success: Emerald background (for confirmations, records)

### Layout Principles

```
┌─────────────────────────────────────────────────────────────────┐
│  NAVBAR (Stone Shadow bg)                                       │
│  Logo | Leaderboard | Maps | Players | [Join Now - Gold CTA]    │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   HERO SECTION (Dark Sand bg with subtle texture)               │
│   ┌───────────────────────────────────────────────────────┐    │
│   │  "HYVEXA" (Gold, large)                               │    │
│   │  "The Ultimate Hytale Parkour Experience" (Secondary) │    │
│   │  [Join Server - Gold] [Leaderboard - Bronze outline]  │    │
│   │  Background: Parkour gameplay with dark overlay       │    │
│   └───────────────────────────────────────────────────────┘    │
│                                                                 │
│   LIVE STATS BAR (Warm Clay bg)                                 │
│   ┌──────────┬──────────┬──────────┬──────────┐                │
│   │ Players  │ Total    │ Maps     │ Records  │                │
│   │ Online   │ Runs     │ Count    │ Today    │                │
│   │ (Emerald)│ (Gold)   │ (Gold)   │ (Emerald)│                │
│   └──────────┴──────────┴──────────┴──────────┘                │
│                                                                 │
│   FEATURED SECTIONS (Warm Clay cards on Dark Sand)              │
│   ┌───────────────┐ ┌───────────────┐ ┌───────────────┐        │
│   │ Top 10        │ │ Featured      │ │ Recent        │        │
│   │ Leaderboard   │ │ Maps          │ │ Records       │        │
│   │ (Gold ranks)  │ │ (Difficulty)  │ │ (Emerald new) │        │
│   └───────────────┘ └───────────────┘ └───────────────┘        │
│                                                                 │
│   FOOTER (Stone Shadow bg)                                      │
│   Links | Discord | Social | Copyright (Muted text)             │
└─────────────────────────────────────────────────────────────────┘
```

### Responsive Design

- **Desktop:** Full layouts, large leaderboard tables
- **Tablet:** Condensed nav, stacked grids
- **Mobile:** Hamburger menu, card-based leaderboard, touch-friendly

### Animations & Polish

- Page transitions (subtle fade/slide)
- Leaderboard number counting animations
- Gold shimmer effect on hover for interactive elements
- Skeleton loaders in Warm Clay with subtle pulse
- Golden particle burst on new records
- Emerald pulse on live data updates

---

## Page Breakdown

### 1. Home (`/`)
- Hero with tagline and CTAs
- Live server stats
- Top 10 leaderboard preview
- Featured maps carousel
- Recent records feed
- "How to join" section

### 2. Leaderboard (`/leaderboard`)
- Filterable by map
- Sortable columns
- Pagination
- Search by player name
- Personal best highlighting (if logged in later)

### 3. Maps (`/maps`)
- Grid of map cards
- Filter by difficulty
- Sort by popularity/newest
- Individual map pages (`/maps/[slug]`) with:
  - Map-specific leaderboard
  - Description, difficulty, author
  - Completion stats

### 4. Players (`/players`)
- Search/browse players
- Individual profiles (`/players/[name]`) with:
  - All-time stats
  - Best times per map
  - Recent activity
  - Rank history (future)

### 5. Play (`/play`)
- Step-by-step guide to join
- Server IP/connection info
- FAQ
- Requirements

### 6. About (`/about`)
- Server history
- Team/credits
- Contact

---

## Implementation Phases

### Phase 1: Foundation
- [ ] Set up Next.js project with Tailwind
- [ ] Configure Prisma with MySQL connection
- [ ] Build basic layout (navbar, footer)
- [ ] Create home page with static content
- [ ] Deploy to Vercel

### Phase 2: Core Features
- [ ] Leaderboard page with real data
- [ ] Maps listing and individual map pages
- [ ] Player profiles
- [ ] Server status API endpoint

### Phase 3: Polish & SEO
- [ ] Implement full design system
- [ ] Add animations and transitions
- [ ] SEO meta tags and sitemap
- [ ] OpenGraph images
- [ ] Performance optimization

### Phase 4: Enhancements
- [ ] Real-time updates (WebSocket or polling)
- [ ] Discord integration (embed server stats, link profiles)
- [ ] Player authentication (optional, for claiming profiles)

---

## Hosting Options

| Option | Pros | Cons |
|--------|------|------|
| **Vercel** | Zero-config, fast, free tier | Database must be accessible externally |
| **VPS (same server)** | Direct DB access, full control | More setup, manage Node.js yourself |
| **Cloudflare Pages** | Fast, cheap | Less Next.js feature support |

**Recommendation:** Start with Vercel. Expose your MySQL with a read-only user on a non-standard port, or use a database proxy.

---

## Summary

- **Stack:** Next.js + Tailwind + Prisma + MySQL
- **Design:** Warm fantasy palette (Gold/Bronze/Emerald on dark sand), earthy and prestigious
- **SEO:** SSR pages, keyword-targeted content, structured data
- **Data:** Real-time leaderboards and stats from your existing database
- **Vibe:** Premium, adventure-themed website that feels like part of the Hytale universe
