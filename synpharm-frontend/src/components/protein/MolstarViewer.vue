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
  }>(),
  {
    autoRotate: false,
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
let gridOn = true
let camPos: number[] | null = null
let camTarget: number[] | null = null
let camUp: number[] | null = null

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
       * Grid
       * ----------------------------------------- */

      setGridVisible(gridOn)

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
 * Reset Camera
 * ========================================================= */

function resetView(): void {

  const p = plugin

  if (!p) return

  console.log('[Molstar] reset view')

  // Save initial camera on first reset if not yet saved
  if (!camPos || !camTarget || !camUp) {
    const cam = (p.canvas3d as any)?.camera as any
    const s = cam?.state
    if (s) {
      camPos = [...s.position]
      camTarget = [...s.target]
      camUp = [...s.up]
    }
  }

  const cam = (p.canvas3d as any)?.camera as any

  if (cam?.setState && camPos && camTarget && camUp) {
    cam.setState({ position: camPos, target: camTarget, up: camUp }, 0)
    cam.update?.()
  }

  // Force immediate + deferred redraw to ensure view refreshes without user interaction
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
 * Grid
 * ========================================================= */

function setGridVisible(
  visible: boolean
): void {

  gridOn = visible

  if (!plugin?.canvas3d) {
    return
  }

  try {

    plugin.canvas3d.setProps({
      grid: {
        visible,
      },
    } as any)

    plugin.canvas3d.requestDraw()

  } catch (err) {

    console.error(
      '[Molstar] grid failed:',
      err
    )
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
