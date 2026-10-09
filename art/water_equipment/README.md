# 储水装备贴图

当前游戏贴图由内置 ImageGen 重新绘制，参考 [Mekanism 官方项目](https://github.com/mekanism/Mekanism) 的 16×16 像素密度、灰色金属与蓝色储水部件配色。采用硬边像素、哑光金属和较少装饰。参考材质只用于风格观察，不随模组发布。

第二版原图保存在 `source-v2/`；`source/`、`preview.png` 和 `prompts-v1.md` 保留第一版。`preview-v2.png` 是当前游戏贴图的最近邻放大预览。

运行 `./export.ps1` 生成 16×16 RGBA PNG：物品按 alpha 边界裁掉留白，等比缩放并居中；方块材质整图缩放。运行 `./preview.ps1` 更新预览。

| 资产 | 游戏文件 |
| --- | --- |
| 湿润的月之尘 | `textures/item/wet_moondust.png` |
| 保湿剂 | `textures/item/moisturizer.png` |
| 储罐核心 | `textures/item/tank_core.png` |
| 大储罐 | `textures/item/large_water_tank.png` |
| 以水为食 | `textures/item/water_as_food.png` |
| 储罐透明框架 | `textures/block/large_water_tank_side.png` |

游戏文件均位于 `src/main/resources/assets/sscfe/`。方块框架中央为真实透明窗口，外框三像素宽。`WaterTankRenderer` 使用 Minecraft 水材质，根据同步的储水量绘制内部水体。空罐不绘制水；水桶或 Forge 管道注水、抽水会改变水面高度。物品栏图标是独立的固定图标。

## 第二版提示词

公共编辑提示词：

```text
Edit target: Image 1 is the current SCCFE texture. Redraw it completely to match a restrained Minecraft/Mekanism technical item aesthetic. Image 2 is a STYLE REFERENCE sheet of actual 16x16 Mekanism textures: use its small pixel budget, matte gray shading, compact silhouettes, simple flat color clusters and understated details. Do not reproduce any Mekanism item design or text. The subject identity of Image 1 stays the same, but radically simplify the artwork. Production constraint: design on an EXACT 16 by 16 LOGICAL PIXEL GRID, as if hand-drawn one pixel at a time for a vanilla resource pack. The output may be enlarged, but it must look like an enlarged 16x16 image, not a detailed image merely described as pixel art. Each logical pixel is a flat solid square. Total visible object width about 10-13 logical pixels, with 1-2 empty pixels around it. Only 8-10 matte colors: 4 neutral gray metal shades, 3 muted water blue/cyan shades, charcoal and one small pale highlight shade. Use one-pixel edges and small solid clusters. No glints, bloom, glass reflections, gradients, realistic lighting, glossy chrome, tiny bubbles, microtexture, busy rivets, complicated machinery or ornate jewelry. No painted checkerboard. Actual transparent background. No labels, text, numeral, watermark, scene or UI. The large rectangular white chrome highlights from Image 1 MUST be eliminated. 
```

### wet_moondust

```text
An ingredient pile of damp lunar dust, comparable in simplicity and footprint to vanilla gunpowder: low 9-pixel-wide light gray pile, uneven 3-step upper edge, darker gray bottom, a SINGLE tiny 2x2 muted cyan droplet at upper right. No crystals or glitter. Keep the powder simple and readable.
```

### moisturizer

```text
A squat portable moisturizing canister on a belt: compact gray rectangular housing about 10 pixels wide and 10 high, one short dark gray screw cap, THREE cyan vertical chamber marks each just 1 pixel wide separated by solid gray dividers, a plain dark lower belt strip and one light gray buckle square. Simplify to the density and matte appearance of the Mekanism canteen reference. No external curved hoses.
```

### tank_core

```text
A compact gray mechanical storage core as a flat crafting component: an 8x8 square central frame, a simple 4x4 muted blue chamber or valve inset, just two short gray socket tabs at left and right. The core should have a restrained, slightly beveled outline with 3 gray tones. No glowing orb, gears, pipes, shiny jewels or complex assembly.
```

### large_water_tank

```text
A tall compact steel reservoir inventory icon about 9 pixels wide and 13 high: flat dark gray side rails, thin light gray top/bottom bands, one plain blue rectangular water window, one small square cap at the top. A single darker left edge can hint at thickness, like a Mekanism tank. Water is just two flat blue shades, no bubbles, shine, straps or detailed hoses.
```

### water_as_food

```text
A small practical water-nourishment accessory: simple dark gray square medallion about 8 pixels across, lightly beveled gray edges, one blue droplet inset taking about 3x5 pixels, one tiny gray hanging loop at top. Straightforward Minecraft crafting-item icon, not decorative jewelry. No chain, wings, antlers, gills, extra gems or polished reflective ring.
```

### 月之尘细化

```text
Edit this exact lunar dust sprite. Remove ALL of the soft glow, halo, bloom, smoke and fuzzy shadows around the pile and droplet. Keep only the solid hard-edged flat square gray pixels of the powder and a small hard-edged flat blue droplet, surrounded by ACTUAL alpha transparency. Simplify this to the density of vanilla Minecraft gunpowder on a literal 16x16 logical pixel grid: one low 11-pixel-wide pile with 3 or 4 gray colors, an irregular 3-step upper edge, a 2x2 blue droplet next to its right edge. Limit to 7 colors; NO crystals, glitter, haze, shine, noise, soft pixels or realistic shading. The object should be centered on a SQUARE transparent canvas. Crisp 1-pixel stair-step boundaries like a genuine vanilla Minecraft 16x16 inventory texture. Preserve the damp lunar dust identity, with much quieter colors.
```

### 方块表面

```text
Image 1 is the current large water tank block texture to REPLACE. Image 2 is a reference sheet of genuine Mekanism 16x16 textures. Redraw Image 1 as a much simpler, matte, low-contrast Minecraft machinery surface. An EXACT 16x16 logical pixel tile, each logical pixel a single flat solid square, enlarged with nearest-neighbor. Fill the entire square canvas edge to edge, opaque, no padding, no perspective. The entire panel has a flat matte gray 2-pixel wide outer frame, thin darker inner edge, a plain recessed rectangular blue water window. Upper quarter of the window is dark gray empty space, lower part is flat muted blue with just ONE darker blue stripe at the bottom. Solid 1-pixel light gray upper-left edge and mid-gray lower-right edge show depth. One restrained 1x1 square corner bolt at each corner. Maximum about 8 colors. Match the simplicity, compact pixel grid and gray values of the Mekanism steel casing reference, using water blue instead of green. NO texture noise, NO specular/chrome shine, NO checkerboard shading, NO bubbles, NO reflections, NO gradients, NO glowing edge, NO decorative border, NO wording, NO perspective render, NO labels. A usable flat 16x16 block face texture only.
```

最终透明窗口编辑提示词：

```text
Edit this EXACT square Minecraft water-tank panel texture. Make ONLY the window transparent: replace the ENTIRE recessed inner rectangle (including blue water, darker bottom stripe and dark air space at its top) with true alpha transparency, so the game's dynamically rendered water behind it will be visible. Keep the matte gray outer metal frame, all four small corner bolts, and narrow darker inner frame pixel-perfect in place. Keep the image square, full-bleed, and maintain the same restrained 16x16-logical-pixel aesthetic with hard square clusters, no detailed new artwork. The central hole must be genuinely 100% transparent with sharp rectangular edges, not white, black, checkerboard, fog, glass reflections or painted water. No new labels, text, logos, numerals or decorative elements. Gray frame opaque, central window alpha zero. This is a production block-frame texture with real transparency for Minecraft Java Edition.
```
