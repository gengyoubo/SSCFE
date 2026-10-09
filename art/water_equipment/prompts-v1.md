# 储水装备贴图

使用内置 ImageGen 绘制，透明物品图采用真实 alpha 通道。生成原图保存在 `source/`，游戏版本导出为 32×32 RGBA PNG。`export.ps1` 仅以最近邻方式导出游戏尺寸，保留透明通道；可再次运行以重新导出。

`preview.png` 显示六张游戏贴图的放大预览。

| 资产 | 项目内游戏文件 |
| --- | --- |
| 湿润的月之尘 | `src/main/resources/assets/sscfe/textures/item/wet_moondust.png` |
| 保湿剂 | `src/main/resources/assets/sscfe/textures/item/moisturizer.png` |
| 储罐核心 | `src/main/resources/assets/sscfe/textures/item/tank_core.png` |
| 大储罐 | `src/main/resources/assets/sscfe/textures/item/large_water_tank.png` |
| 以水为食 | `src/main/resources/assets/sscfe/textures/item/water_as_food.png` |
| 大储罐方块表面 | `src/main/resources/assets/sscfe/textures/block/large_water_tank_side.png` |

五个物品模型使用各自的透明图标。大储罐方块模型的罐体表面和破坏粒子使用方块贴图，金属端盖沿用 Minecraft 金属纹理。方块表面的水位为静态装饰，实际水量见物品说明或右键方块的信息。

## 最终提示词

五张物品图均使用以下公共提示词，再追加各自的 Subject。

```text
Use case: stylized-concept. Asset type: production Minecraft inventory texture for a Java Edition 1.20.1 mod, ONE isolated sprite. Style: authentic restrained Minecraft pixel art, a strict 32 by 32 logical pixel grid magnified with nearest-neighbor square pixels, very limited palette, deliberate hard pixel clusters, no anti-aliasing, no gradients, no vector smoothness, no glow outside the sprite. Palette: charcoal outline, muted iron silver, dark steel, moonlight pale cyan, deep water blue. Consistent lighting from upper left. Composition: centered and filling about 85 percent of a square canvas with 2 logical pixels of transparent padding. Background must be actual alpha transparency, not a checkerboard painted into the image. No labels, text, numerals, watermark, scene, UI, extra objects or border. Single finished usable game texture, not a sprite sheet or mockup.
```

### 湿润的月之尘

```text
Subject: A small irregular mound of damp silvery lunar dust, pale gray mineral grains with a few clear pale cyan crystalline flecks and one small blue water droplet clinging at upper right. Low wide irregular silhouette, precious moist powder rather than a gemstone or bottle.
```

### 保湿剂

```text
Subject: A compact wearable moisturizing canister: squat iron housing with three small cyan water-filled glass chambers, a silver screw cap, dark belt buckle and tiny short outlet tube. Rounded rectangular stout silhouette, small practical water apparatus, clearly different from the tall large tank.
```

### 储罐核心

```text
Subject: A mechanical water storage core: chunky square iron frame with beveled pixel corners, a bright cyan circular water chamber at the center, dark gray reinforcement brackets and small pipe sockets. A compact component rather than a complete tank, readable like a crafting ingredient.
```

### 大储罐

```text
Subject: A large wearable water reservoir: tall reinforced dark steel rectangular tank with silver horizontal bands, a broad bright cyan water-level window, short pipe connector on the left and a cap on top. Slight three-quarter Minecraft item view, heavy backpack-sized apparatus, darker and taller than the moisturizer. One object.
```

### 以水为食

```text
Subject: A wearable water-nourishment charm: small silver ring-shaped medallion with a vivid blue water droplet set in its center, cyan three-pixel gill accents on each side like an axolotl, a simple iron loop at the top and a tiny short dark cord. Elegant readable charm icon; aquatic nourishment, no writing or heart-of-the-sea object.
```

### 大储罐方块表面

```text
Use case: stylized-concept. Asset type: production Minecraft Java Edition block surface texture, ONE square texture for the vertical side of a large water tank. A FLAT orthographic straight-on full-frame 32 by 32 logical pixel-art tile, magnified with perfectly hard square nearest-neighbor pixels, no perspective, no 3D render, no object cutout. The texture fills every pixel all the way to the image edges. Industrial dark steel water tank panel: charcoal steel border on the outermost 3 logical pixels, silver bolted reinforcement bands along the top and bottom, a large central cyan blue water-level glass window occupying the inner rectangle, deep blue water in the lower 70% and a small dark air gap at the top, pale cyan pixels at the waterline and a few sparse pixel bubbles. Minecraft vanilla restrained shading, muted silver iron highlights at upper left, dark steel framing, vivid blue water. Very limited color palette, clear pixel clusters. Every corner and edge is opaque dark steel. No text, numerals, labels, logos, watermark, background space, display mockup, grid or padding. One flat square game texture only.
```
