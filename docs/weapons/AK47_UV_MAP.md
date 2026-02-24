# AK47 UV Texture Map Reference

Texture: `Common/Items/Weapons/AK47/AK47.png` (128x128 pixels)
Model: `Common/Items/Weapons/AK47/AK47.blockymodel`

All coordinates are `(x, y) WxH` in pixels from top-left origin.

## Part Summary

| Part | Cubes | Pixels | Description |
|------|-------|--------|-------------|
| body | 20 | ~2570 | Main receiver/frame |
| front grip | 36 | ~1794 | Handguard and front rail |
| stock2 | 7 | ~1652 | Rear stock assembly |
| mag | 10 | ~1086 | Magazine |
| barrel | 9 | ~884 | Barrel tubes |
| barrel hold | 37 | ~805 | Barrel shroud/gas block area |
| grip2 | 10 | ~614 | Pistol grip |
| sights3 | 24 | ~579 | Iron sights (front + rear) |
| trigger guard | 14 | ~202 | Trigger guard loop |
| trigger | 4 | ~31 | Trigger itself |
| bolt | 4 | ~228 | Charging handle/bolt carrier |

## Part Hierarchy

```
R-Attachment
 +- grip2 (9 child cubes)
 +- body (19 child cubes)
 +- mag (10 child cubes)
 +- front grip (35 child cubes)
 +- stock2 (6 child cubes)
 +- barrel (7 cubes + 1 quad)
 |   +- barrel hold (36 child cubes)
 +- sights3 (23 child cubes)
 +- trigger guard (13 child cubes)
 |   +- trigger (3 child cubes)
 +- bolt (3 child cubes)
```

---

## body (~2570 px)

The largest part. Dominates the left and center of the texture.

### Major regions (>= 4px area)

| Region | Size | Face | Source |
|--------|------|------|--------|
| (0, 0) | 34x5 | right | body |
| (0, 5) | 34x5 | left | body |
| (5, 44) | 5x34 | top | body |
| (10, 10) | 5x34 | bottom | body |
| (10, 44) | 2x27 | bottom | cube-C2 |
| (8, 71) | 2x27 | top | cube-C2 |
| (15, 41) | 5x31 | top | cube-C1 |
| (20, 10) | 5x31 | bottom | cube-C1 |
| (45, 11) | 27x2 | right | cube-C2 |
| (45, 13) | 27x2 | left | cube-C2 |
| (51, 10) | 31x1 | right | cube-C1 |
| (55, 19) | 31x1 | left | cube-C1 |
| (47, 20) | 27x2 | right | cube-C14 |
| (47, 22) | 27x2 | left | cube-C14 |
| (47, 24) | 26x2 | right | cube-C6 |
| (47, 26) | 26x2 | left | cube-C6 |
| (47, 28) | 8x2 | right | cube-C4 |
| (55, 15) | 16x2 | right | cube-C10 |
| (55, 17) | 16x2 | left | cube-C10 |
| (55, 33) | 3x2 | right | cube-C15 |
| (60, 52) | 27x1 | right | cube-C7 |
| (60, 53) | 27x1 | left | cube-C7 |
| (60, 54) | 27x1 | right | cube-C13 |
| (65, 50) | 13x2 | right | cube-C17 |
| (65, 55) | 13x2 | left | cube-C17 |
| (65, 57) | 13x2 | right | cube-C19 |
| (65, 59) | 13x2 | left | cube-C19 |
| (65, 61) | 5x5 | front | body |
| (35, 65) | 5x5 | back | body |
| (85, 58) | 5x3 | right | cube-C16 |
| (85, 61) | 5x3 | left | cube-C16 |
| (85, 65) | 13x1 | right | cube-C18 |
| (84, 37) | 16x1 | right | cube-C8 |
| (84, 38) | 16x1 | left | cube-C8 |
| (85, 11) | 8x2 | left | cube-C4 |
| (87, 16) | 13x1 | left | cube-C18 |
| (88, 6) | 5x2 | back | cube-C3 |
| (88, 8) | 5x2 | front | cube-C3 |
| (88, 40) | 5x2 | back | cube-C15 |
| (88, 42) | 5x2 | front | cube-C15 |
| (90, 82) | 5x3 | top | cube-C15 |
| (91, 46) | 5x3 | bottom | cube-C15 |
| (93, 43) | 8x1 | right | cube-C5 |
| (93, 44) | 8x1 | left | cube-C5 |
| (77, 93) | 2x3 | back | cube-C16 |
| (93, 78) | 2x3 | front | cube-C16 |
| (30, 93) | 2x5 | top | cube-C16 |
| (32, 88) | 2x5 | bottom | cube-C16 |
| (19, 56) | 1x27 | bottom | cube-C7 |
| (59, 61) | 1x27 | bottom | cube-C14 |
| (61, 61) | 1x27 | bottom | cube-C13 |
| (2, 65) | 1x26 | bottom | cube-C6 |
| (6, 82) | 1x27 | top | cube-C7 |
| (20, 83) | 1x27 | top | cube-C14 |
| (1, 91) | 1x26 | top | cube-C6 |
| (60, 88) | 1x27 | top | cube-C13 |
| (62, 88) | 1x27 | top | cube-C12 |
| (41, 62) | 1x27 | bottom | cube-C12 |
| (58, 79) | 1x16 | bottom | cube-C8 |
| (57, 95) | 1x16 | top | cube-C8 |
| (6, 82) | 1x16 | bottom | cube-C10 |
| (40, 96) | 1x16 | top | cube-C10 |
| (36, 87) | 1x13 | bottom | cube-C17 |
| (35, 100) | 1x13 | top | cube-C17 |
| (52, 87) | 1x13 | bottom | cube-C18 |
| (51, 100) | 1x13 | top | cube-C18 |
| (54, 87) | 1x13 | bottom | cube-C19 |
| (53, 100) | 1x13 | top | cube-C19 |
| (94, 34) | 5x1 | back | cube-C1 |
| (94, 35) | 5x1 | front | cube-C1 |
| (99, 24) | 5x1 | top | cube-C3 |
| (11, 74) | 3x2 | left | cube-C15 |

---

## front grip (~1794 px)

Handguard area. UV faces are widely distributed.

### Major regions (>= 4px area)

| Region | Size | Face | Source |
|--------|------|------|--------|
| (40, 15) | 15x5 | right | cube-C3 |
| (40, 30) | 15x5 | left | cube-C3 |
| (30, 55) | 5x15 | top | cube-C3 |
| (20, 75) | 5x5 | back | cube-C3 |
| (30, 75) | 5x5 | front | cube-C3 |
| (45, 35) | 5x15 | bottom | cube-C3 |
| (58, 36) | 5x6 | back | front grip |
| (58, 55) | 5x6 | front | front grip |
| (45, 58) | 5x6 | right | front grip |
| (35, 59) | 5x6 | left | front grip |
| (55, 66) | 5x5 | bottom | front grip |
| (31, 71) | 5x5 | top | front grip |
| (58, 42) | 15x2 | right | cube-C28 |
| (60, 44) | 15x2 | left | cube-C28 |
| (20, 41) | 5x15 | bottom | cube-C28 |
| (15, 56) | 5x15 | top | cube-C28 |
| (88, 44) | 5x2 | back | cube-C28 |
| (58, 88) | 5x2 | front | cube-C28 |
| (79, 17) | 12x2 | right | cube-C4 |
| (72, 29) | 12x2 | left | cube-C4 |
| (45, 50) | 5x12 | bottom | cube-C4 |
| (40, 59) | 5x12 | top | cube-C4 |
| (83, 88) | 5x2 | back | cube-C4 |
| (88, 88) | 5x2 | front | cube-C4 |
| (83, 72) | 12x2 | right | cube-C5 |
| (83, 74) | 12x2 | left | cube-C5 |
| (87, 66) | 12x1 | right | cube-C8 |
| (87, 67) | 12x1 | left | cube-C8 |
| (87, 68) | 12x1 | right | cube-C7 |
| (87, 69) | 12x1 | left | cube-C7 |
| (87, 70) | 12x1 | right | cube-C11 |
| (87, 71) | 12x1 | left | cube-C11 |
| (87, 83) | 12x1 | left | cube-C10 |
| (83, 87) | 12x1 | right | cube-C10 |
| (87, 84) | 12x1 | right | cube-C9 |
| (87, 85) | 12x1 | left | cube-C9 |
| (63, 41) | 12x1 | right | cube-C6 |
| (87, 22) | 12x1 | left | cube-C6 |
| (60, 46) | 5x6 | front | cube-C1 |
| (30, 60) | 5x6 | back | cube-C1 |
| (75, 41) | 5x5 | front | cube-C2 |
| (35, 75) | 5x5 | back | cube-C2 |
| (72, 33) | 12x2 | right | cube-C35 |
| (72, 35) | 12x1 | right | cube-C34 |
| (1, 116) | 5x12 | top | cube-C34 |
| (89, 23) | 5x2 | back | cube-C12 |
| (89, 28) | 5x2 | front | cube-C12 |
| (89, 30) | 5x2 | back | cube-C27 |
| (89, 32) | 5x2 | front | cube-C27 |
| (56, 87) | 1x12 | bottom | cube-C5 |
| (55, 99) | 1x12 | top | cube-C5 |
| (71, 87) | 1x12 | bottom | cube-C6 |
| (70, 99) | 1x12 | top | cube-C6 |
| (73, 87) | 1x12 | bottom | cube-C8 |
| (72, 99) | 1x12 | top | cube-C8 |
| (75, 87) | 1x12 | bottom | cube-C7 |
| (74, 99) | 1x12 | top | cube-C7 |
| (77, 87) | 1x12 | bottom | cube-C11 |
| (76, 99) | 1x12 | top | cube-C11 |
| (22, 88) | 1x12 | bottom | cube-C10 |
| (21, 100) | 1x12 | top | cube-C10 |
| (24, 88) | 1x12 | bottom | cube-C9 |
| (23, 100) | 1x12 | top | cube-C9 |
| (95, 0) | 5x1 | back | cube-C32 |
| (95, 1) | 5x1 | front | cube-C32 |
| (97, 25) | 5x1 | back | cube-C31 |
| (97, 28) | 5x1 | front | cube-C31 |
| (94, 47) | 5x1 | back | cube-C29 |
| (94, 48) | 5x1 | front | cube-C29 |
| (94, 51) | 5x1 | back | cube-C30 |
| (94, 52) | 5x1 | front | cube-C30 |
| (94, 76) | 5x1 | back | cube-C33 |
| (94, 77) | 5x1 | front | cube-C33 |

---

## stock2 (~1652 px)

Rear stock. Concentrated in the left-center area.

### Major regions (>= 4px area)

| Region | Size | Face | Source |
|--------|------|------|--------|
| (20, 10) | 20x5 | right | cube-C6 |
| (20, 15) | 20x5 | left | cube-C6 |
| (30, 20) | 5x20 | bottom | cube-C6 |
| (25, 40) | 5x20 | top | cube-C6 |
| (25, 71) | 5x5 | back | cube-C6 |
| (50, 71) | 5x5 | front | cube-C6 |
| (30, 20) | 17x5 | right | cube-C2 |
| (30, 25) | 17x5 | left | cube-C2 |
| (40, 30) | 5x17 | bottom | cube-C2 |
| (35, 47) | 5x17 | top | cube-C2 |
| (70, 66) | 5x5 | back | cube-C2 |
| (6, 71) | 5x5 | front | cube-C2 |
| (40, 0) | 5x15 | back | cube-C5 |
| (20, 40) | 5x15 | front | cube-C5 |
| (20, 55) | 3x15 | right | cube-C5 |
| (23, 55) | 3x15 | left | cube-C5 |
| (91, 52) | 5x3 | top | cube-C5 |
| (91, 55) | 5x3 | bottom | cube-C5 |
| (45, 35) | 13x5 | right | cube-C1 |
| (45, 40) | 13x5 | left | cube-C1 |
| (35, 47) | 5x13 | bottom | cube-C1 |
| (50, 58) | 5x13 | top | cube-C1 |
| (31, 70) | 5x5 | back | cube-C1 |
| (70, 61) | 5x5 | front | cube-C1 |
| (51, 0) | 10x5 | right | cube-C4 |
| (51, 5) | 10x5 | left | cube-C4 |
| (5, 55) | 5x10 | bottom | cube-C4 |
| (39, 10) | 5x10 | top | cube-C4 |
| (65, 71) | 5x5 | back | cube-C4 |
| (70, 71) | 5x5 | front | cube-C4 |
| (72, 11) | 5x2 | back | cube-C3 |
| (74, 20) | 5x2 | front | cube-C3 |
| (55, 28) | 3x2 | right | cube-C3 |
| (6, 87) | 3x2 | left | cube-C3 |
| (92, 16) | 5x3 | top | cube-C3 |
| (92, 19) | 5x3 | bottom | cube-C3 |
| (65, 66) | 5x5 | back | stock2 |
| (20, 70) | 5x5 | front | stock2 |
| (10, 66) | 1x5 | right | stock2 |
| (38, 94) | 1x5 | left | stock2 |
| (47, 95) | 5x1 | top | stock2 |
| (99, 46) | 5x1 | bottom | stock2 |

---

## mag (~1086 px)

Magazine. Concentrated in the right-center area with repeating 8x3 horizontal strips.

### Major regions (>= 4px area)

| Region | Size | Face | Source |
|--------|------|------|--------|
| (77, 11) | 8x3 | right | cube-C4 |
| (78, 46) | 8x3 | left | cube-C4 |
| (79, 14) | 8x3 | right | cube-C6 |
| (79, 20) | 8x3 | left | cube-C6 |
| (79, 66) | 8x3 | right | cube-C7 |
| (79, 69) | 8x3 | left | cube-C7 |
| (76, 37) | 8x3 | right | cube-C2 |
| (50, 76) | 8x3 | left | cube-C2 |
| (75, 73) | 8x3 | right | cube-C1 |
| (6, 76) | 8x3 | left | cube-C1 |
| (65, 76) | 8x3 | right | cube-C3 |
| (73, 76) | 8x3 | left | cube-C3 |
| (78, 49) | 8x3 | right | cube-C5 |
| (78, 55) | 8x3 | left | cube-C5 |
| (80, 40) | 8x3 | right | cube-C9 |
| (80, 43) | 8x3 | left | cube-C9 |
| (81, 23) | 8x3 | right | cube-C10 |
| (81, 76) | 8x3 | left | cube-C10 |
| (77, 79) | 8x3 | right | cube-C8 |
| (28, 80) | 8x3 | left | cube-C8 |
| (5, 73) | 3x8 | top | cube-C1 |
| (58, 65) | 3x8 | bottom | cube-C1 |
| (14, 74) | 3x8 | top | cube-C2 |
| (9, 79) | 3x8 | bottom | cube-C3 |
| (28, 84) | 3x8 | top | cube-C3 |
| (53, 79) | 3x8 | bottom | cube-C4 |
| (12, 87) | 3x8 | top | cube-C4 |
| (82, 58) | 3x8 | bottom | cube-C5 |
| (56, 87) | 3x8 | top | cube-C5 |
| (71, 79) | 3x8 | bottom | cube-C6 |
| (68, 87) | 3x8 | top | cube-C6 |
| (77, 79) | 3x8 | bottom | cube-C7 |
| (74, 87) | 3x8 | top | cube-C7 |
| (39, 80) | 3x8 | bottom | cube-C8 |
| (23, 88) | 3x8 | top | cube-C8 |
| (85, 58) | 3x8 | bottom | cube-C9 |
| (5, 89) | 3x8 | top | cube-C9 |
| (83, 82) | 3x8 | bottom | cube-C10 |
| (80, 90) | 3x8 | top | cube-C10 |
| (55, 73) | 3x3 | back | cube-C1 |
| (36, 88) | 3x3 | front | cube-C1 |
| (90, 2) | 3x3 | front | cube-C2 |
| (2, 89) | 3x3 | back | cube-C2 |
| (58, 90) | 3x3 | back | cube-C3 |
| (90, 58) | 3x3 | front | cube-C3 |
| (61, 90) | 3x3 | back | cube-C4 |
| (90, 61) | 3x3 | front | cube-C4 |
| (64, 90) | 3x3 | back | cube-C5 |
| (77, 90) | 3x3 | front | cube-C5 |
| (90, 78) | 3x3 | back | cube-C6 |
| (80, 90) | 3x3 | front | cube-C6 |
| (83, 90) | 3x3 | back | cube-C7 |
| (86, 90) | 3x3 | front | cube-C7 |
| (89, 90) | 3x3 | back | cube-C8 |
| (14, 91) | 3x3 | front | cube-C8 |
| (17, 91) | 3x3 | back | cube-C9 |
| (36, 91) | 3x3 | front | cube-C9 |
| (40, 91) | 3x3 | back | cube-C10 |
| (43, 91) | 3x3 | front | cube-C10 |

---

## barrel (~884 px)

Barrel tubes. Mostly long 27x1 horizontal strips (sides) and 1x27 vertical strips (top/bottom).

### Major regions (>= 4px area)

| Region | Size | Face | Source |
|--------|------|------|--------|
| (61, 3) | 27x1 | right | cube-C7 |
| (61, 4) | 27x1 | left | cube-C7 |
| (61, 5) | 27x1 | right | cube-C6 |
| (61, 6) | 27x1 | left | cube-C6 |
| (61, 7) | 27x1 | right | cube-C5 |
| (61, 8) | 27x1 | left | cube-C5 |
| (61, 9) | 27x1 | right | cube-C4 |
| (62, 28) | 27x1 | left | cube-C4 |
| (62, 29) | 27x1 | right | cube-C3 |
| (62, 30) | 27x1 | left | cube-C3 |
| (62, 31) | 27x1 | right | barrel |
| (62, 32) | 27x1 | left | barrel |
| (62, 33) | 27x1 | right | cube-C2 |
| (62, 34) | 27x1 | left | cube-C2 |
| (62, 35) | 27x1 | right | cube-C1 |
| (63, 36) | 27x1 | left | cube-C1 |
| (64, 55) | 1x27 | bottom | cube-C5 |
| (65, 55) | 1x27 | bottom | cube-C1 |
| (43, 62) | 1x27 | bottom | cube-C7 |
| (45, 62) | 1x27 | bottom | cube-C6 |
| (16, 64) | 1x27 | bottom | cube-C4 |
| (18, 64) | 1x27 | bottom | cube-C3 |
| (47, 64) | 1x27 | bottom | barrel |
| (49, 64) | 1x27 | bottom | cube-C2 |
| (63, 88) | 1x27 | top | cube-C5 |
| (42, 89) | 1x27 | top | cube-C7 |
| (44, 89) | 1x27 | top | cube-C6 |
| (15, 91) | 1x27 | top | cube-C4 |
| (17, 91) | 1x27 | top | cube-C3 |
| (46, 91) | 1x27 | top | barrel |
| (48, 91) | 1x27 | top | cube-C2 |
| (50, 91) | 1x27 | top | cube-C1 |
| (17, 20) | 2x2 | front | quad-C8 |

---

## barrel hold (~805 px)

Gas block / barrel shroud. Many small cubes, heavily scattered.

### Major regions (>= 4px area)

| Region | Size | Face | Source |
|--------|------|------|--------|
| (82, 10) | 23x1 | right | barrel hold |
| (83, 82) | 23x1 | left | barrel hold |
| (14, 79) | 1x23 | bottom | barrel hold |
| (13, 102) | 1x23 | top | barrel hold |
| (89, 25) | 8x1 | left | cube-C36 |
| (87, 86) | 8x1 | right | cube-C36 |
| (93, 8) | 8x1 | left | cube-C33 |
| (92, 21) | 8x1 | right | cube-C33 |
| (93, 9) | 8x1 | right | cube-C32 |
| (93, 11) | 8x1 | left | cube-C32 |
| (93, 12) | 8x1 | right | cube-C31 |
| (93, 26) | 8x1 | left | cube-C31 |
| (93, 27) | 8x1 | right | cube-C30 |
| (93, 40) | 8x1 | left | cube-C30 |
| (93, 41) | 8x1 | right | cube-C29 |
| (93, 42) | 8x1 | left | cube-C29 |
| (92, 15) | 8x1 | left | cube-C34 |
| (91, 57) | 8x1 | right | cube-C34 |
| (90, 36) | 8x1 | right | cube-C35 |
| (90, 81) | 8x1 | left | cube-C35 |
| (97, 2) | 5x1 | right | cube-C24 |
| (97, 3) | 5x1 | left | cube-C24 |
| (97, 4) | 5x1 | right | cube-C23 |
| (97, 5) | 5x1 | left | cube-C23 |
| (97, 6) | 5x1 | right | cube-C22 |
| (97, 7) | 5x1 | left | cube-C22 |
| (96, 13) | 5x1 | left | cube-C11 |
| (96, 14) | 5x1 | right | cube-C12 |
| (96, 19) | 5x1 | right | cube-C13 |
| (96, 20) | 5x1 | left | cube-C13 |
| (96, 33) | 5x1 | right | cube-C28 |
| (96, 88) | 5x1 | left | cube-C28 |
| (96, 89) | 5x1 | right | cube-C27 |
| (96, 90) | 5x1 | right | cube-C26 |
| (96, 91) | 5x1 | left | cube-C26 |
| (96, 92) | 5x1 | right | cube-C25 |
| (96, 93) | 5x1 | left | cube-C25 |
| (95, 73) | 5x1 | right | cube-C3 |
| (95, 74) | 5x1 | left | cube-C3 |
| (95, 75) | 5x1 | right | cube-C4 |
| (95, 78) | 5x1 | left | cube-C4 |
| (95, 79) | 5x1 | right | cube-C9 |
| (95, 80) | 5x1 | left | cube-C9 |
| (95, 86) | 5x1 | right | cube-C10 |
| (95, 87) | 5x1 | left | cube-C10 |
| (90, 96) | 5x1 | left | cube-C27 |
| (92, 95) | 5x1 | right | cube-C11 |
| (89, 34) | 5x2 | right | cube-C5 |
| (40, 89) | 5x2 | left | cube-C5 |
| (14, 94) | 3x2 | right | cube-C6 |
| (17, 94) | 3x2 | left | cube-C6 |
| (40, 97) | 2x2 | back | cube-C1 |
| (61, 97) | 2x2 | front | cube-C1 |
| (67, 98) | 2x2 | back | cube-C2 |
| (40, 99) | 2x2 | front | cube-C2 |
| (36, 99) | 2x2 | right | cube-C2 |
| (47, 99) | 2x2 | left | cube-C2 |
| (69, 90) | 1x8 | bottom | cube-C35 |
| (68, 98) | 1x8 | top | cube-C35 |
| (27, 88) | 1x8 | bottom | cube-C36 |
| (8, 92) | 1x8 | bottom | cube-C33 |
| (7, 100) | 1x8 | top | cube-C33 |
| (5, 92) | 1x8 | bottom | cube-C34 |
| (50, 99) | 1x8 | top | cube-C34 |
| (29, 93) | 1x8 | bottom | cube-C32 |
| (9, 100) | 1x8 | top | cube-C32 |
| (31, 93) | 1x8 | bottom | cube-C31 |
| (30, 101) | 1x8 | top | cube-C31 |
| (59, 93) | 1x8 | bottom | cube-C30 |
| (32, 101) | 1x8 | top | cube-C30 |
| (61, 93) | 1x8 | bottom | cube-C29 |
| (60, 101) | 1x8 | top | cube-C29 |

---

## grip2 (~614 px)

Pistol grip. Mix of medium rects and strips.

### Major regions (>= 4px area)

| Region | Size | Face | Source |
|--------|------|------|--------|
| (45, 0) | 6x11 | left | cube-C3 |
| (0, 44) | 6x11 | right | cube-C3 |
| (26, 55) | 4x11 | back | cube-C3 |
| (50, 55) | 4x11 | front | cube-C3 |
| (79, 67) | 4x6 | top | cube-C3 |
| (71, 16) | 8x3 | right | cube-C4 |
| (73, 25) | 8x3 | left | cube-C4 |
| (62, 28) | 4x8 | bottom | cube-C4 |
| (18, 64) | 4x8 | top | cube-C4 |
| (87, 52) | 4x3 | back | cube-C4 |
| (65, 87) | 4x3 | front | cube-C4 |
| (92, 13) | 4x2 | left | cube-C1 |
| (91, 55) | 4x2 | right | cube-C1 |
| (91, 17) | 4x2 | back | cube-C1 |
| (0, 92) | 4x2 | front | cube-C1 |
| (35, 70) | 4x4 | top | cube-C1 |
| (36, 83) | 4x4 | bottom | cube-C1 |
| (92, 19) | 4x2 | back | cube-C2 |
| (92, 90) | 4x2 | front | cube-C2 |
| (83, 83) | 4x4 | back | grip2 |
| (23, 84) | 4x4 | front | grip2 |
| (28, 76) | 2x4 | right | grip2 |
| (23, 80) | 2x4 | left | grip2 |
| (96, 94) | 4x2 | top | grip2 |
| (97, 2) | 4x2 | bottom | grip2 |

---

## sights3 (~579 px)

Iron sights. One large region for the rear sight body, rest scattered small.

### Major regions (>= 4px area)

| Region | Size | Face | Source |
|--------|------|------|--------|
| (50, 45) | 10x5 | right | cube-C15 |
| (50, 50) | 10x5 | left | cube-C15 |
| (14, 56) | 4x10 | bottom | cube-C15 |
| (58, 65) | 4x10 | top | cube-C15 |
| (36, 70) | 4x5 | back | cube-C15 |
| (28, 83) | 4x5 | front | cube-C15 |
| (93, 45) | 8x1 | right | cube-C2 |
| (92, 62) | 8x1 | left | cube-C2 |
| (20, 83) | 2x8 | bottom | cube-C2 |
| (65, 90) | 2x8 | top | cube-C2 |
| (93, 63) | 6x1 | right | cube-C17 |
| (81, 93) | 6x1 | left | cube-C17 |
| (26, 88) | 2x6 | bottom | cube-C17 |
| (90, 6) | 2x6 | top | cube-C17 |
| (93, 58) | 2x4 | back | cube-C1 |
| (61, 93) | 2x4 | front | cube-C1 |
| (97, 6) | 4x2 | top | cube-C16 |
| (85, 13) | 2x1 | left | cube-C16 |
| (58, 44) | 2x1 | right | cube-C16 |
| (99, 47) | 4x1 | back | cube-C16 |
| (99, 48) | 4x1 | front | cube-C16 |
| (79, 23) | 2x2 | back | sights3 |
| (36, 97) | 2x2 | front | sights3 |
| (87, 93) | 3x2 | right | sights3 |
| (93, 88) | 3x2 | left | sights3 |
| (92, 96) | 2x3 | top | sights3 |
| (2, 94) | 2x3 | bottom | sights3 |
| (90, 5) | 3x1 | back | cube-C22 |
| (99, 63) | 3x1 | front | cube-C22 |
| (99, 57) | 3x1 | right | cube-C22 |
| (99, 85) | 3x1 | left | cube-C22 |
| (94, 46) | 3x3 | bottom | cube-C22 |
| (49, 94) | 3x3 | top | cube-C22 |
| (100, 0) | 3x1 | back | cube-C23 |
| (6, 100) | 3x1 | front | cube-C23 |
| (9, 100) | 3x1 | left | cube-C23 |
| (94, 52) | 3x3 | top | cube-C23 |
| (99, 53) | 2x2 | back | cube-C3 |
| (99, 66) | 2x2 | left | cube-C3 |
| (54, 99) | 2x2 | right | cube-C3 |
| (61, 99) | 2x2 | front | cube-C3 |
| (71, 99) | 2x2 | bottom | cube-C3 |
| (101, 70) | 2x2 | top | cube-C3 |

---

## bolt (~228 px)

Charging handle. Mostly 13-pixel strips.

### Major regions (>= 4px area)

| Region | Size | Face | Source |
|--------|------|------|--------|
| (63, 37) | 13x2 | right | cube-C2 |
| (63, 39) | 13x2 | left | cube-C2 |
| (65, 46) | 13x2 | right | bolt |
| (65, 48) | 13x2 | left | bolt |
| (84, 39) | 13x1 | right | cube-C1 |
| (85, 64) | 13x1 | left | cube-C1 |
| (10, 87) | 1x13 | bottom | cube-C2 |
| (28, 97) | 1x13 | top | cube-C2 |
| (12, 87) | 1x13 | bottom | cube-C1 |
| (11, 100) | 1x13 | top | cube-C1 |
| (34, 87) | 1x13 | bottom | bolt |
| (33, 100) | 1x13 | top | bolt |
| (100, 21) | 3x1 | back | cube-C3 |
| (32, 100) | 3x1 | front | cube-C3 |
| (103, 38) | 3x1 | top | cube-C3 |

---

## trigger guard (~202 px)

Small guard loop. Mostly 1-2px faces scattered at edges.

### Major regions (>= 4px area)

| Region | Size | Face | Source |
|--------|------|------|--------|
| (94, 28) | 2x3 | back | cube-C8 |
| (94, 31) | 2x3 | right | cube-C8 |
| (36, 94) | 2x3 | front | cube-C8 |
| (40, 94) | 2x3 | left | cube-C8 |
| (101, 72) | 2x2 | top | cube-C8 |
| (101, 76) | 2x2 | bottom | cube-C8 |
| (101, 83) | 2x2 | bottom | cube-C11 |
| (81, 101) | 2x2 | top | cube-C11 |
| (5, 98) | 1x4 | back | trigger guard |
| (49, 99) | 1x4 | front | trigger guard |
| (38, 99) | 1x4 | right | trigger guard |
| (71, 99) | 1x4 | left | trigger guard |
| (2, 94) | 1x6 | back | cube-C12 |
| (3, 94) | 1x6 | right | cube-C12 |
| (24, 94) | 1x6 | front | cube-C12 |
| (25, 94) | 1x6 | left | cube-C12 |
| (72, 99) | 1x4 | back | cube-C7 |
| (73, 99) | 1x4 | right | cube-C7 |
| (74, 99) | 1x4 | front | cube-C7 |
| (75, 99) | 1x4 | left | cube-C7 |
| (90, 0) | 5x2 | left | cube-C13 |
| (89, 76) | 5x2 | right | cube-C13 |
| (28, 97) | 1x5 | bottom | cube-C13 |
| (20, 102) | 1x5 | top | cube-C13 |
| (90, 99) | 2x2 | right | cube-C9 |
| (92, 99) | 2x2 | left | cube-C9 |

---

## trigger (~31 px)

Tiny part. Mostly single-pixel faces.

### Major regions (>= 2px area)

| Region | Size | Face | Source |
|--------|------|------|--------|
| (102, 15) | 1x2 | right | trigger |
| (15, 102) | 1x2 | back | trigger |
| (16, 102) | 1x2 | front | trigger |
| (17, 102) | 1x2 | left | trigger |
| (102, 19) | 1x2 | front | cube-C3 |
| (18, 102) | 1x2 | back | cube-C3 |
| (19, 102) | 1x2 | right | cube-C3 |
| (25, 102) | 1x2 | left | cube-C3 |

---

## Pixel Overlap Between Parts

UV faces from different parts share some texture pixels. When painting one part's region, you may affect another part. Major overlaps (>50 pixels):

| Parts | Shared pixels |
|-------|---------------|
| front grip + stock2 | 212 |
| body + stock2 | 133 |
| grip2 + stock2 | 92 |
| body + mag | 80 |
| barrel + front grip | 78 |
| body + front grip | 66 |
| barrel hold + front grip | 59 |
| body + sights3 | 53 |
| barrel hold + body | 52 |

**Key takeaway:** When painting a part, check if the pixel also belongs to another part. The body, front grip, and stock2 have the most overlap. The trigger has almost no overlap with other parts.
