<template>
  <div class="molstar-viewer">
    <!-- Loading -->
    <div v-if="loading" class="molstar-viewer__status">
      <span class="molstar-viewer__status-icon">⏳</span>
      <span>{{ statusText }}</span>
    </div>

    <!-- Error -->
    <div v-if="error" class="molstar-viewer__status molstar-viewer__status--error">
      <span class="molstar-viewer__status-icon">⚠️</span>
      <span>{{ errorText }}</span>
    </div>

    <!-- Mol* -->
    <div
      ref="containerRef"
      class="molstar-viewer__canvas"
    ></div>
  </div>
</template>

<script setup lang="ts">
import {
  ref,
  watch,
  onMounted,
  onBeforeUnmount,
} from 'vue'

import type { PluginUIContext } from 'molstar/lib/mol-plugin-ui/context'

/* =========================================================
 * Props
 * ========================================================= */

const props = withDefaults(
  defineProps<{
    pdbId: string
    autoRotate?: boolean
    showGrid?: boolean
  }>(),
  {
    autoRotate: false,
    showGrid: false,
  }
)

/* =========================================================
 * Emits
 * ========================================================= */

const emit = defineEmits<{
  (e: 'structure-loaded'): void
}>()

/* =========================================================
 * Vue State
 * ========================================================= */

const containerRef = ref<HTMLElement | null>(null)

const loading = ref(true)
const error = ref(false)

const statusText = ref('初始化 Mol* 引擎...')
const errorText = ref('')

/* =========================================================
 * Mol* State
 * ========================================================= */

let plugin: PluginUIContext | null = null
let root: any = null

let currentPdbId: string | null = null

let autoRotateId: number | null = null

let mainStructure: any = null

let mainRepresentation: any = null
let labelRepresentation: any = null

let currentRepType = 'cartoon'
let currentColor = 'chain-id'

let labelsOn = false

/* =========================================================
 * Initial Camera State
 *
 * 每个 PDB 加载完成后保存其放大后的初始视角。
 * resetView() 始终恢复此状态。
 * ========================================================= */

let initialCameraState: {
  position: number[]
  target: number[]
  up: number[]
} | null = null

/* =========================================================
 * Grid State (Mol* native Shape)
 *
 * 3 个独立 representation 实现不同视觉层级：
 *   gridAxisRepr  — XYZ 坐标轴 (alpha 0.7, sizeFactor 4)
 *   gridPlaneRepr — XY/XZ/YZ 网格面 (alpha 0.12, sizeFactor 1)
 *   gridTickRepr  — 刻度标记 (alpha 0.35, sizeFactor 2)
 * ========================================================= */

let gridAxisRepr: any = null
let gridPlaneRepr: any = null
let gridTickRepr: any = null

interface GridBounds {
  center: [number, number, number]
  radius: number
  extent: number
}

function computeGridBounds(): GridBounds | null {
  const bs = plugin?.canvas3d?.boundingSphere
  if (!bs || bs.radius <= 0) return null
  return {
    center: [bs.center[0], bs.center[1], bs.center[2]],
    radius: bs.radius,
    extent: bs.radius * 1.3,
  }
}

/* =========================================================
 * Operation Queue
 *
 * 防止用户快速点击：
 *
 * Cartoon -> Sphere -> Stick
 *
 * 三个异步操作同时修改 Mol* State Tree。
 * ========================================================= */

let operationQueue: Promise<void> = Promise.resolve()

function enqueue(operation: () => Promise<void>): Promise<void> {
  operationQueue = operationQueue
    .then(operation)
    .catch((err) => {
      console.error('[Molstar] operation failed:', err)
    })

  return operationQueue
}

/* =========================================================
 * PDB URL
 * ========================================================= */

function getPdbUrl(id: string): string {
  return `https://files.rcsb.org/download/${id}.pdb`
}

/* =========================================================
 * Init Mol*
 * ========================================================= */

async function initMolstar(): Promise<void> {
  if (!containerRef.value) {
    console.error('[Molstar] container not found')
    return
  }

  try {
    const ReactDOMClient = await import('react-dom/client')

    const {
      createPluginUI,
    } = await import(
      'molstar/lib/mol-plugin-ui/index.js'
    )

    const {
      DefaultPluginUISpec,
    } = await import(
      'molstar/lib/mol-plugin-ui/spec.js'
    )

    const {
      PluginConfig,
    } = await import(
      'molstar/lib/mol-plugin/config.js'
    )

    statusText.value = '启动 Mol* 引擎...'

    const spec = DefaultPluginUISpec()

    spec.config = [
      ...(spec.config || []),

      [
        PluginConfig.Viewport.ShowExpand,
        false,
      ],

      [
        PluginConfig.Viewport.ShowControls,
        false,
      ],

      [
        PluginConfig.Viewport.ShowAnimation,
        false,
      ],

      [
        PluginConfig.Viewport.ShowSelectionMode,
        false,
      ],
    ]

    spec.layout = {
      initial: {
        isExpanded: false,
        showControls: false,
        controlsDisplay: 'outside' as any,
      },
    }

    root = ReactDOMClient.createRoot(
      containerRef.value
    )

    plugin = await createPluginUI({
      target: containerRef.value,

      render: (
        component: any,
        _container: Element
      ) => {
        root.render(component)
      },

      spec,
    })

    console.log('[Molstar] Plugin initialized')

    await loadStructure(props.pdbId)

  } catch (err) {
    console.error(
      '[Molstar] initialization failed:',
      err
    )

    error.value = true
    loading.value = false

    errorText.value =
      'Mol* 引擎初始化失败'
  }
}

/* =========================================================
 * Load Structure
 * ========================================================= */

async function loadStructure(
  pdbId: string
): Promise<void> {

  const p = plugin

  if (!p) return

  return enqueue(async () => {

    loading.value = true
    error.value = false

    statusText.value =
      `加载 ${pdbId} ...`

    try {

      /* -----------------------------------------
       * 清理旧结构
       * ----------------------------------------- */

      if (currentPdbId) {

        console.log(
          '[Molstar] removing old structure:',
          currentPdbId
        )

        await p.managers.structure.hierarchy.remove(
          'all' as any,
          true
        )
      }

      mainStructure = null
      mainRepresentation = null
      labelRepresentation = null

      labelsOn = false
      initialCameraState = null

      /* -----------------------------------------
       * 清理旧 Grid
       * ----------------------------------------- */

      removeAllGrids()

      /* -----------------------------------------
       * Download PDB
       * ----------------------------------------- */

      const pdbUrl = getPdbUrl(pdbId)

      console.log(
        '[Molstar] loading:',
        pdbUrl
      )

      const data =
        await p.builders.data.download(
          {
            url: pdbUrl,
          },
          {
            state: {
              isGhost: true,
            },
          }
        )

      /* -----------------------------------------
       * Parse trajectory
       * ----------------------------------------- */

      const trajectory =
        await p.builders.structure.parseTrajectory(
          data,
          'pdb'
        )

      /* -----------------------------------------
       * Create model
       * ----------------------------------------- */

      const model =
        await p.builders.structure.createModel(
          trajectory
        )

      /* -----------------------------------------
       * Create structure
       * ----------------------------------------- */

      const structure =
        await p.builders.structure.createStructure(
          model,
          {
            name: pdbId,
          } as any
        )

      mainStructure = structure

      /* -----------------------------------------
       * Create initial representation
       * ----------------------------------------- */

      const representation =
        await p.builders.structure.representation.addRepresentation(
          structure,
          {
            type: 'cartoon',
            color: 'chain-id',
          } as any
        )

      mainRepresentation = representation

      currentRepType = 'cartoon'
      currentColor = 'chain-id'

      currentPdbId = pdbId

      /* -----------------------------------------
       * Grid (after structure is ready)
       * ----------------------------------------- */

      if (props.showGrid) {
        await createSpatialGrid()
      }

      /* -----------------------------------------
       * Save initial camera (after auto-fit + zoom)
       * ----------------------------------------- */

      // 等待 Mol* 完成 auto-fit 渲染
      await new Promise(r => requestAnimationFrame(r))
      await new Promise(r => requestAnimationFrame(r))

      saveInitialCameraState()

      /* -----------------------------------------
       * Finish
       * ----------------------------------------- */

      loading.value = false

      console.log(
        '[Molstar] structure loaded:',
        pdbId
      )

      emit('structure-loaded')

    } catch (err) {

      console.error(
        `[Molstar] failed to load ${pdbId}:`,
        err
      )

      loading.value = false
      error.value = true

      errorText.value =
        `无法加载 ${pdbId} 蛋白质结构`
    }
  })
}

/* =========================================================
 * Save Initial Camera State
 *
 * 在 PDB 加载 + Mol* 自动 fit 完成后调用。
 * 将当前 Camera 放大 ~20% 后保存为 initialCameraState。
 * ========================================================= */

function saveInitialCameraState(): void {
  const p = plugin
  if (!p) return

  const cam = (p.canvas3d as any)?.camera as any
  const s = cam?.state
  if (!s?.position || !s?.target || !s?.up) return

  const pos = s.position as number[]
  const target = s.target as number[]
  const up = s.up as number[]

  // 方向向量: camera → target
  const dx = pos[0] - target[0]
  const dy = pos[1] - target[1]
  const dz = pos[2] - target[2]

  // 镜头距离缩短 ~15%，蛋白质显示更大
  const scale = 0.85
  const zoomedPos = [
    target[0] + dx * scale,
    target[1] + dy * scale,
    target[2] + dz * scale,
  ]

  cam.setState({ position: zoomedPos, target, up }, 0)
  cam.update?.()

  initialCameraState = {
    position: zoomedPos,
    target: target as number[],
    up: up as number[],
  }

  p.canvas3d?.requestDraw()

  console.log('[Molstar] initial camera state saved (zoomed 1.25×)')
}

/* =========================================================
 * Reset Camera
 * ========================================================= */

function resetView(): void {
  const p = plugin
  if (!p) return

  console.log('[Molstar] reset view')

  const cam = (p.canvas3d as any)?.camera as any

  if (cam?.setState && initialCameraState) {
    cam.setState(initialCameraState, 0)
    cam.update?.()
  }

  p.canvas3d?.requestDraw()
  requestAnimationFrame(() => {
    p.canvas3d?.requestDraw()
  })
}

/* =========================================================
 * Representation
 *
 * 这里使用：
 *
 * removeRepresentation
 * +
 * addRepresentation
 *
 * 但只针对当前主 representation。
 *
 * 不操作整个 StructureComponentManager。
 * ========================================================= */

async function updateRepresentation(
  type: string
): Promise<void> {

  const p = plugin

  if (!p) return

  if (!mainStructure) {
    console.warn(
      '[Molstar] structure not ready'
    )
    return
  }

  if (type === currentRepType) {
    return
  }

  return enqueue(async () => {

    /* -----------------------------------------
     * 保存当前 Camera State
     *
     * Mol* 在重建 Representation 后可能自动 Fit Camera。
     * 因此先保存用户当前的视角，切换完成后再恢复。
     * ----------------------------------------- */

    const cam = (p.canvas3d as any)?.camera as any
    const savedCamState = cam?.state
      ? {
          position: [...cam.state.position] as number[],
          target: [...cam.state.target] as number[],
          up: [...cam.state.up] as number[],
        }
      : null

    console.log(
      '[Molstar] representation:',
      currentRepType,
      '->',
      type
    )

    try {

      /* -----------------------------------------
       * Remove old representation
       * ----------------------------------------- */

      if (mainRepresentation) {

        await p.runTask(p.state.data.updateTree(
          p.build().delete(
            mainRepresentation.ref
          )
        ))

        mainRepresentation = null
        labelRepresentation = null
        labelsOn = false
      }

      /* -----------------------------------------
       * Add new representation
       * ----------------------------------------- */

      const newRepresentation =
        await p.builders.structure.representation.addRepresentation(
          mainStructure,
          {
            type,
            color: currentColor,
          } as any
        )

      mainRepresentation =
        newRepresentation

      currentRepType = type

      p.canvas3d?.requestDraw()

      /* -----------------------------------------
       * 恢复 Camera State
       *
       * 等待至少 1 帧让 Mol* 完成内部处理，
       * 然后恢复切换前的视角。
       * ----------------------------------------- */

      if (savedCamState && cam?.setState) {
        await new Promise(r => requestAnimationFrame(r))
        cam.setState(savedCamState, 0)
        cam.update?.()
        p.canvas3d?.requestDraw()
      }

      console.log(
        '[Molstar] representation changed:',
        type
      )

    } catch (err) {

      console.error(
        '[Molstar] representation change failed:',
        err
      )
    }
  })
}

/* =========================================================
 * Color
 * ========================================================= */

async function setColorScheme(
  color: string
): Promise<void> {

  const p = plugin

  if (!p) return

  if (!mainStructure) {
    console.warn(
      '[Molstar] structure not ready'
    )
    return
  }

  if (color === currentColor) {
    return
  }

  return enqueue(async () => {

    /*
     * 保存当前 Camera State。
     * 与 updateRepresentation 同理，防止 Mol* 自动 Fit。
     */

    const cam = (p.canvas3d as any)?.camera as any
    const savedCamState = cam?.state
      ? {
          position: [...cam.state.position] as number[],
          target: [...cam.state.target] as number[],
          up: [...cam.state.up] as number[],
        }
      : null

    console.log(
      '[Molstar] color:',
      currentColor,
      '->',
      color
    )

    try {

      /*
       * 最稳定的方法：
       *
       * 删除当前 representation
       * 再用新的 color 创建
       *
       * 不使用 updateTransform。
       */

      if (mainRepresentation) {

        await p.runTask(p.state.data.updateTree(
          p.build().delete(
            mainRepresentation.ref
          )
        ))

        mainRepresentation = null
        labelRepresentation = null
        labelsOn = false
      }

      const newRepresentation =
        await p.builders.structure.representation.addRepresentation(
          mainStructure,
          {
            type: currentRepType,
            color,
          } as any
        )

      mainRepresentation =
        newRepresentation

      currentColor = color

      p.canvas3d?.requestDraw()

      if (savedCamState && cam?.setState) {
        await new Promise(r => requestAnimationFrame(r))
        cam.setState(savedCamState, 0)
        cam.update?.()
        p.canvas3d?.requestDraw()
      }

      console.log(
        '[Molstar] color changed:',
        color
      )

    } catch (err) {

      console.error(
        '[Molstar] color change failed:',
        err
      )
    }
  })
}

/* =========================================================
 * Grid — 3D Spatial Reference System
 *
 * 使用 Mol* 原生 LinesBuilder → Shape → Representation，
 * 在蛋白质 3D 坐标空间中绘制 XYZ 坐标轴 + 三平面网格 + 刻度。
 * 所有元素共用 Mol* Scene + Camera（自动同步）。
 *
 * 视觉层级（低到高）：
 *   Grid Planes  <  Ticks  <  Axes  <  Protein
 *
 * 颜色语义：
 *   X 轴 → 柔红 (rgb(239, 68, 68))   — 对应 $error-color
 *   Y 轴 → 柔绿 (rgb(16, 185, 129))   — 对应 $success-color
 *   Z 轴 → 柔蓝 (rgb(59, 130, 246))   — 对应 $accent-color
 * ========================================================= */

const GRID_SPACING = 5   // Å
const TICK_SPACING = 10  // Å

const AXIS_COLORS: [number, number, number][] = [
  [239, 68, 68],   // 0: X — soft red
  [16, 185, 129],  // 1: Y — soft green
  [59, 130, 246],  // 2: Z — soft blue
]

const GRID_COLOR: [number, number, number] = [160, 175, 195] // 淡灰蓝

function computeTickLength(extent: number): number {
  return Math.min(Math.max(extent * 0.04, 1.0), 5.0)
}

/* -----------------------------------------
 * 惰性 import：只在创建 Grid 时加载
 * ----------------------------------------- */

let _LinesBuilder: any = null
let _Lines: any = null
let _Shape: any = null
let _Representation: any = null
let _Color: any = null
let _ParamDefinition: any = null

async function ensureGridModules(): Promise<boolean> {
  if (_LinesBuilder && _Lines && _Shape && _Representation && _Color && _ParamDefinition) return true
  try {
    ;[
      { LinesBuilder: _LinesBuilder },
      { Lines: _Lines },
      { Shape: _Shape },
      { Representation: _Representation },
      { Color: _Color },
      { ParamDefinition: _ParamDefinition },
    ] = await Promise.all([
      import('molstar/lib/mol-geo/geometry/lines/lines-builder.js'),
      import('molstar/lib/mol-geo/geometry/lines/lines.js'),
      import('molstar/lib/mol-model/shape.js'),
      import('molstar/lib/mol-repr/representation.js'),
      import('molstar/lib/mol-util/color/index.js'),
      import('molstar/lib/mol-util/param-definition.js'),
    ])
    return true
  } catch (err) {
    console.error('[Grid] module import failed:', err)
    return false
  }
}

/* -----------------------------------------
 * 通用：Lines → Representation
 * ----------------------------------------- */

function linesToRepr(
  lines: any,
  name: string,
  colorFn: (group: number) => any,
  alpha: number,
  sizeFactor: number,
): any {
  const defaultParams = _ParamDefinition.getDefaultValues(_Lines.Params)

  const shape = _Shape.create(
    name,
    { pdbId: currentPdbId },
    lines,
    colorFn,
    () => 1,
    () => name,
  )

  const renderObject = _Shape.createRenderObject(shape, {
    ...defaultParams,
    alpha,
    lineSizeAttenuation: false,
    sizeFactor,
  })

  return _Representation.fromRenderObject(name, renderObject)
}

/* -----------------------------------------
 * XYZ 坐标轴
 * ----------------------------------------- */

function createAxesRepr(bounds: GridBounds): any {
  const [cx, cy, cz] = bounds.center
  const e = bounds.extent
  const builder = _LinesBuilder.create(6, 12)

  // 每个轴画完整线（正负方向）
  // X axis (group 0) — 红色
  builder.add(cx - e, cy, cz, cx + e, cy, cz, 0)
  // Y axis (group 1) — 绿色
  builder.add(cx, cy - e, cz, cx, cy + e, cz, 1)
  // Z axis (group 2) — 蓝色
  builder.add(cx, cy, cz - e, cx, cy, cz + e, 2)

  const lines = builder.getLines()
  if (lines.lineCount === 0) return null

  return linesToRepr(
    lines,
    'Grid Axes',
    (g) => _Color.fromRgb(...AXIS_COLORS[g] ?? [180, 180, 180]),
    0.7,
    4,
  )
}

/* -----------------------------------------
 * 三平面网格 (XY / XZ / YZ)
 * ----------------------------------------- */

function createGridPlanesRepr(bounds: GridBounds): any {
  const [cx, cy, cz] = bounds.center
  const e = bounds.extent
  const spacing = GRID_SPACING

  // 计算所需行数
  const lineCount = Math.ceil((2 * e) / spacing) * 3 * 2
  const builder = _LinesBuilder.create(lineCount, lineCount * 2)

  const step = spacing

  // XY plane (Z = cz)
  for (let x = cx - e; x <= cx + e + 0.001; x += step) {
    builder.add(x, cy - e, cz, x, cy + e, cz, 0)
  }
  for (let y = cy - e; y <= cy + e + 0.001; y += step) {
    builder.add(cx - e, y, cz, cx + e, y, cz, 0)
  }

  // XZ plane (Y = cy)
  for (let x = cx - e; x <= cx + e + 0.001; x += step) {
    builder.add(x, cy, cz - e, x, cy, cz + e, 0)
  }
  for (let z = cz - e; z <= cz + e + 0.001; z += step) {
    builder.add(cx - e, cy, z, cx + e, cy, z, 0)
  }

  // YZ plane (X = cx)
  for (let y = cy - e; y <= cy + e + 0.001; y += step) {
    builder.add(cx, y, cz - e, cx, y, cz + e, 0)
  }
  for (let z = cz - e; z <= cz + e + 0.001; z += step) {
    builder.add(cx, cy - e, z, cx, cy + e, z, 0)
  }

  const lines = builder.getLines()
  if (lines.lineCount === 0) return null

  return linesToRepr(
    lines,
    'Grid Planes',
    () => _Color.fromRgb(...GRID_COLOR),
    0.12,
    1,
  )
}

/* -----------------------------------------
 * 刻度标记
 * ----------------------------------------- */

function createTicksRepr(bounds: GridBounds): any {
  const [cx, cy, cz] = bounds.center
  const e = bounds.extent
  const step = TICK_SPACING
  const tickLen = computeTickLength(e)

  // 沿各轴的刻度数
  const tickCount = Math.floor(e / step)
  const lineCount = tickCount * 3 * 2
  const builder = _LinesBuilder.create(lineCount + 10, (lineCount + 10) * 2)

  // X axis ticks → 垂直方向 (Y)
  for (let x = cx + step; x <= cx + e + 0.001; x += step) {
    builder.add(x, cy, cz, x, cy + tickLen, cz, 0)
    builder.add(x, cy, cz, x, cy - tickLen, cz, 0)
  }
  for (let x = cx - step; x >= cx - e - 0.001; x -= step) {
    builder.add(x, cy, cz, x, cy + tickLen, cz, 0)
    builder.add(x, cy, cz, x, cy - tickLen, cz, 0)
  }

  // Y axis ticks → 水平方向 (X)
  for (let y = cy + step; y <= cy + e + 0.001; y += step) {
    builder.add(cx, y, cz, cx + tickLen, y, cz, 1)
    builder.add(cx, y, cz, cx - tickLen, y, cz, 1)
  }
  for (let y = cy - step; y >= cy - e - 0.001; y -= step) {
    builder.add(cx, y, cz, cx + tickLen, y, cz, 1)
    builder.add(cx, y, cz, cx - tickLen, y, cz, 1)
  }

  // Z axis ticks → Y 方向 (更有机会被看到)
  for (let z = cz + step; z <= cz + e + 0.001; z += step) {
    builder.add(cx, cy, z, cx, cy + tickLen, z, 2)
    builder.add(cx, cy, z, cx, cy - tickLen, z, 2)
  }
  for (let z = cz - step; z >= cz - e - 0.001; z -= step) {
    builder.add(cx, cy, z, cx, cy + tickLen, z, 2)
    builder.add(cx, cy, z, cx, cy - tickLen, z, 2)
  }

  const lines = builder.getLines()
  if (lines.lineCount === 0) return null

  return linesToRepr(
    lines,
    'Grid Ticks',
    (g) => _Color.fromRgb(...AXIS_COLORS[g] ?? [180, 180, 180]),
    0.35,
    2,
  )
}

/* -----------------------------------------
 * 统一创建入口
 * ----------------------------------------- */

async function createSpatialGrid(): Promise<void> {
  const p = plugin
  if (!p || !p.canvas3d || !mainStructure) return

  removeAllGrids()

  const ok = await ensureGridModules()
  if (!ok) return

  const bounds = computeGridBounds()
  if (!bounds) return

  try {
    gridAxisRepr = createAxesRepr(bounds)
    if (gridAxisRepr) p.canvas3d.add(gridAxisRepr)
  } catch (err) { console.error('[Grid] axes creation failed:', err) }

  try {
    gridPlaneRepr = createGridPlanesRepr(bounds)
    if (gridPlaneRepr) p.canvas3d.add(gridPlaneRepr)
  } catch (err) { console.error('[Grid] planes creation failed:', err) }

  try {
    gridTickRepr = createTicksRepr(bounds)
    if (gridTickRepr) p.canvas3d.add(gridTickRepr)
  } catch (err) { console.error('[Grid] ticks creation failed:', err) }

  p.canvas3d.requestDraw()
}

/* -----------------------------------------
 * 清理
 * ----------------------------------------- */

function removeOneGrid(r: any): void {
  if (!r || !plugin?.canvas3d) return
  try {
    plugin.canvas3d.remove(r)
    r.destroy?.()
  } catch (_) {}
}

function removeAllGrids(): void {
  removeOneGrid(gridAxisRepr);  gridAxisRepr = null
  removeOneGrid(gridPlaneRepr); gridPlaneRepr = null
  removeOneGrid(gridTickRepr);  gridTickRepr = null
}

/* -----------------------------------------
 * 可见性
 * ----------------------------------------- */

async function setGridVisible(visible: boolean): Promise<void> {
  if (!plugin?.canvas3d) return

  if (visible) {
    // 如果未创建则创建，否则恢复可见
    if (!gridAxisRepr && !gridPlaneRepr && !gridTickRepr) {
      await createSpatialGrid()
    } else {
      gridAxisRepr?.setState?.({ visible: true })
      gridPlaneRepr?.setState?.({ visible: true })
      gridTickRepr?.setState?.({ visible: true })
      plugin.canvas3d.requestDraw()
    }
  } else {
    gridAxisRepr?.setState?.({ visible: false })
    gridPlaneRepr?.setState?.({ visible: false })
    gridTickRepr?.setState?.({ visible: false })
    plugin.canvas3d.requestDraw()
  }
}

/* =========================================================
 * Labels
 * ========================================================= */

async function setLabelsVisible(
  visible: boolean
): Promise<void> {

  const p = plugin

  if (!p) return

  if (!mainStructure) {
    console.warn(
      '[Molstar] structure not ready'
    )
    return
  }

  if (visible === labelsOn) {
    return
  }

  return enqueue(async () => {

    try {

      if (visible) {

        console.log(
          '[Molstar] labels ON'
        )

        if (!labelRepresentation) {

          labelRepresentation =
            await p.builders.structure.representation.addRepresentation(
              mainStructure,
              {
                type: 'label',
                color: currentColor,
              } as any
            )
        }

        labelsOn = true

      } else {

        console.log(
          '[Molstar] labels OFF'
        )

        if (labelRepresentation) {

          await p.runTask(p.state.data.updateTree(
            p.build().delete(
              labelRepresentation.ref
            )
          ))

          labelRepresentation = null
        }

        labelsOn = false
      }

    } catch (err) {

      console.error(
        '[Molstar] labels failed:',
        err
      )
    }
  })
}

/* =========================================================
 * Export Image
 * ========================================================= */

async function exportImage(): Promise<Blob | null> {

  if (!plugin) {
    return null
  }

  /* -----------------------------------------
   * Mol* Screenshot Manager
   * ----------------------------------------- */

  try {

    const manager =
      (plugin.managers as any)
        .viewportScreenshot

    if (manager) {

      const dataUri =
        await manager.getImageDataUri()

      if (dataUri) {

        const response =
          await fetch(dataUri)

        return await response.blob()
      }
    }

  } catch (err) {

    console.warn(
      '[Molstar] screenshot manager failed:',
      err
    )
  }

  /* -----------------------------------------
   * Canvas fallback
   * ----------------------------------------- */

  try {

    const element =
      containerRef.value

    if (!element) {
      return null
    }

    const canvas =
      element.querySelector('canvas')

    if (!canvas) {
      return null
    }

    const dataUrl =
      canvas.toDataURL('image/png')

    const response =
      await fetch(dataUrl)

    return await response.blob()

  } catch (err) {

    console.error(
      '[Molstar] canvas export failed:',
      err
    )

    return null
  }
}

function triggerDownload(
  blob: Blob,
  filename: string
): void {

  const url =
    URL.createObjectURL(blob)

  const link =
    document.createElement('a')

  link.href = url
  link.download = filename

  document.body.appendChild(link)

  link.click()

  link.remove()

  setTimeout(() => {
    URL.revokeObjectURL(url)
  }, 100)
}

async function doExportImage(): Promise<void> {

  const blob =
    await exportImage()

  if (!blob) {
    console.error(
      '[Molstar] export image failed'
    )

    return
  }

  triggerDownload(
    blob,
    `protein-${currentPdbId || props.pdbId || 'protein'}-${Date.now()}.png`
  )
}

/* =========================================================
 * Auto Rotate
 * ========================================================= */

function startAutoRotate(): void {

  if (!plugin) return

  stopAutoRotate()

  let lastTime =
    performance.now()

  const degreesPerSecond = 20

  function spin(): void {

    if (!plugin?.canvas3d) {

      autoRotateId = null

      return
    }

    const now =
      performance.now()

    const delta =
      Math.min(
        (now - lastTime) / 1000,
        0.1
      )

    lastTime = now

    try {

      const camera =
        (plugin.canvas3d as any).camera

      if (!camera?.state) {

        autoRotateId =
          requestAnimationFrame(spin)

        return
      }

      const {
        position,
        target,
      } = camera.state

      if (!position || !target) {

        autoRotateId =
          requestAnimationFrame(spin)

        return
      }

      const angle =
        degreesPerSecond *
        delta *
        Math.PI /
        180

      const dx =
        position[0] -
        target[0]

      const dz =
        position[2] -
        target[2]

      const cos =
        Math.cos(angle)

      const sin =
        Math.sin(angle)

      camera.setState?.({
        ...camera.state,

        position: [
          target[0] +
            dx * cos -
            dz * sin,

          position[1],

          target[2] +
            dx * sin +
            dz * cos,
        ],
      })

      plugin.canvas3d.requestDraw()

    } catch (err) {

      console.warn(
        '[Molstar] auto rotate error:',
        err
      )
    }

    autoRotateId =
      requestAnimationFrame(spin)
  }

  autoRotateId =
    requestAnimationFrame(spin)
}

function stopAutoRotate(): void {

  if (autoRotateId !== null) {

    cancelAnimationFrame(
      autoRotateId
    )

    autoRotateId = null
  }
}

/* =========================================================
 * Expose API
 * ========================================================= */

defineExpose({

  resetView,

  exportImage:
    doExportImage,

  updateRepresentation,

  setColorScheme,

  setGridVisible,

  setLabelsVisible,
})

/* =========================================================
 * Watch PDB
 * ========================================================= */

watch(
  () => props.pdbId,
  async (newId, oldId) => {

    if (
      newId &&
      newId !== oldId &&
      plugin
    ) {

      await loadStructure(newId)
    }
  }
)

/* =========================================================
 * Watch showGrid
 * ========================================================= */

watch(
  () => props.showGrid,
  async (visible) => {
    await setGridVisible(visible)
  },
  { immediate: true }
)

/* =========================================================
 * Watch Auto Rotate
 * ========================================================= */

watch(
  () => props.autoRotate,
  (value) => {

    if (value) {

      startAutoRotate()

    } else {

      stopAutoRotate()
    }
  }
)

/* =========================================================
 * Mounted
 * ========================================================= */

onMounted(() => {

  initMolstar()
})

/* =========================================================
 * Unmounted
 * ========================================================= */

onBeforeUnmount(() => {

  removeAllGrids()

  stopAutoRotate()

  operationQueue =
    Promise.resolve()

  try {

    plugin?.dispose()

  } catch (_) {}

  plugin = null

  try {

    root?.unmount()

  } catch (_) {}

  root = null

  mainStructure = null
  mainRepresentation = null
  labelRepresentation = null

  currentPdbId = null
})
</script>

<style lang="scss" scoped>

.molstar-viewer {
  width: 100%;
  height: 480px;
  position: relative;
  border-radius: $border-radius-lg;
  border: 1px solid $border-light;
  overflow: hidden;
  background: #000;
}

.molstar-viewer__canvas {
  width: 100%;
  height: 100%;

  :deep(canvas) {
    display: block;
  }
}

.molstar-viewer__status {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: $spacing-sm;
  background: rgba(0, 0, 0, 0.85);
  z-index: 10;
  font-size: $font-size-sm;
  color: rgba(255, 255, 255, 0.85);
}

.molstar-viewer__status--error {
  background: rgba(220, 38, 38, 0.15);
  color: $error-color;
}

.molstar-viewer__status-icon {
  font-size: 36px;
}

</style>
