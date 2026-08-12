/**
 * Protein Structure Representation Builder
 * Cartoon · Sphere · Stick · Surface — all from parsed PDB atom data
 */
import * as THREE from 'three'
import { buildStableRibbon, buildLoopTube } from './ribbon'

// ---- Types ----
export interface AtomData {
  serial: number
  name: string
  altLoc: string
  resName: string
  chainID: string
  resSeq: number
  iCode: string
  x: number; y: number; z: number
  occupancy: number
  tempFactor: number
  element: string
  charge: string
}

export interface ResidueData {
  resSeq: number
  resName: string
  iCode: string
  atoms: AtomData[]
  ca: AtomData | null
  ssType: 'helix' | 'sheet' | 'loop'
}

export interface ChainData {
  chainID: string
  residues: Map<number, ResidueData>
}

export type RepresentationMode = 'cartoon' | 'sphere' | 'stick' | 'surface'

// ---- Element Colors (CPK) ----
const ELEMENT_COLORS: Record<string, number> = {
  H: 0xffffff, C: 0x404040, N: 0x3050f8, O: 0xff2010,
  S: 0xffff30, P: 0xff8020, FE: 0xe07030, ZN: 0x7d80b0,
  MG: 0x8aff00, CA: 0x3dff00, NA: 0xab5cf2, CL: 0x1ff01f,
  K: 0x8f40d4, MN: 0x9c7ac4, F: 0x90e050, I: 0x940094,
  default: 0xff1493
}

// ---- Secondary Structure Colors ----
const SS_COLORS: Record<string, number> = {
  helix: 0xe74c3c,  // red
  sheet: 0x3498db,  // blue
  loop: 0x2ecc71    // green
}

function getElementColor(el: string): number {
  return ELEMENT_COLORS[el.toUpperCase()] || ELEMENT_COLORS.default
}

// ---- Parse PDB atoms into structured data ----
export function parsePDBAtoms(pdbText: string): { chains: Map<string, ChainData>; atoms: AtomData[] } {
  const atoms: AtomData[] = []
  const chains = new Map<string, ChainData>()
  const helixResidues = new Set<string>()
  const sheetResidues = new Set<string>()

  // First pass: parse HELIX/SHEET records
  for (const line of pdbText.split('\n')) {
    if (line.startsWith('HELIX')) {
      const initChain = line.substring(19, 20).trim()
      const initSeq = parseInt(line.substring(21, 25).trim(), 10)
      const endSeq = parseInt(line.substring(33, 37).trim(), 10)
      for (let s = initSeq; s <= endSeq; s++) {
        helixResidues.add(`${initChain}:${s}`)
      }
    }
    if (line.startsWith('SHEET')) {
      const initChain = line.substring(21, 22).trim()
      const initSeq = parseInt(line.substring(22, 26).trim(), 10)
      const endSeq = parseInt(line.substring(33, 37).trim(), 10)
      for (let s = initSeq; s <= endSeq; s++) {
        sheetResidues.add(`${initChain}:${s}`)
      }
    }
  }

  // Second pass: parse ATOM records
  for (const line of pdbText.split('\n')) {
    if (!line.startsWith('ATOM') && !line.startsWith('HETATM')) continue

    const atom: AtomData = {
      serial: parseInt(line.substring(6, 11).trim(), 10),
      name: line.substring(12, 16).trim(),
      altLoc: line.substring(16, 17).trim(),
      resName: line.substring(17, 20).trim(),
      chainID: line.substring(21, 22).trim() || 'A',
      resSeq: parseInt(line.substring(22, 26).trim(), 10),
      iCode: line.substring(26, 27).trim(),
      x: parseFloat(line.substring(30, 38).trim()),
      y: parseFloat(line.substring(38, 46).trim()),
      z: parseFloat(line.substring(46, 54).trim()),
      occupancy: parseFloat(line.substring(54, 60).trim()) || 1.0,
      tempFactor: parseFloat(line.substring(60, 66).trim()) || 0,
      element: line.substring(76, 78).trim() || line.substring(13, 14).trim() || 'C',
      charge: line.substring(78, 80).trim()
    }

    atoms.push(atom)

    if (!chains.has(atom.chainID)) {
      chains.set(atom.chainID, { chainID: atom.chainID, residues: new Map() })
    }
    const chain = chains.get(atom.chainID)!

    const resKey = atom.resSeq
    if (!chain.residues.has(resKey)) {
      const ssKey = `${atom.chainID}:${atom.resSeq}`
      const ssType = helixResidues.has(ssKey) ? 'helix' : sheetResidues.has(ssKey) ? 'sheet' : 'loop'
      chain.residues.set(resKey, {
        resSeq: atom.resSeq,
        resName: atom.resName,
        iCode: atom.iCode,
        atoms: [],
        ca: null,
        ssType
      })
    }
    const residue = chain.residues.get(resKey)!
    residue.atoms.push(atom)
    if (atom.name === 'CA') residue.ca = atom
  }

  return { chains, atoms }
}

// ---- Build Cartoon (SS-aware Ribbon with stable frames) ----
export function buildCartoon(chains: Map<string, ChainData>): THREE.Group {
  const group = new THREE.Group()

  for (const [, chain] of chains) {
    const residues = Array.from(chain.residues.values())
      .filter(r => r.ca !== null)
      .sort((a, b) => a.resSeq - b.resSeq)
    if (residues.length < 2) continue

    // Group by SS type
    const segments: { ssType: string; residues: typeof residues }[] = []
    let current: typeof residues = [residues[0]]
    for (let i = 1; i < residues.length; i++) {
      if (residues[i].ssType === residues[i - 1].ssType) {
        current.push(residues[i])
      } else {
        segments.push({ ssType: residues[i - 1].ssType, residues: current })
        current = [residues[i]]
      }
    }
    segments.push({ ssType: residues[residues.length - 1].ssType, residues: current })

    for (const seg of segments) {
      const caPoints = seg.residues.map(r => {
        const ca = r.ca!
        return new THREE.Vector3(ca.x, ca.y, ca.z)
      })
      const color = new THREE.Color(SS_COLORS[seg.ssType] || SS_COLORS.loop)

      if (seg.residues.length < 2) {
        const ca = seg.residues[0].ca!
        const sph = new THREE.Mesh(
          new THREE.SphereGeometry(0.2, 8, 6),
          new THREE.MeshPhongMaterial({ color, shininess: 30 })
        )
        sph.position.set(ca.x, ca.y, ca.z)
        group.add(sph)
        continue
      }

      // Higher tension = curve follows CA atoms more closely
      const curve = new THREE.CatmullRomCurve3(caPoints, false, 'catmullrom', 0.85)

      if (seg.ssType === 'loop') {
        group.add(buildLoopTube(curve, color))
      } else if (seg.ssType === 'helix') {
        const geom = buildStableRibbon(curve, 0.55, caPoints.length * 12, false)
        const mat = new THREE.MeshPhongMaterial({
          color, shininess: 40, specular: new THREE.Color(0x222222), side: THREE.DoubleSide
        })
        group.add(new THREE.Mesh(geom, mat))
      } else if (seg.ssType === 'sheet') {
        const geom = buildStableRibbon(curve, 0.8, caPoints.length * 12, true)
        const mat = new THREE.MeshPhongMaterial({
          color, shininess: 35, specular: new THREE.Color(0x222222), side: THREE.DoubleSide
        })
        group.add(new THREE.Mesh(geom, mat))
      }
    }
  }

  return group
}

// ---- Build Sphere (Atom) Representation ----
export function buildSphere(atoms: AtomData[]): THREE.Group {
  const group = new THREE.Group()

  const sphereGeom = new THREE.SphereGeometry(0.3, 16, 12)

  for (const atom of atoms) {
    const color = getElementColor(atom.element)
    const mat = new THREE.MeshPhongMaterial({ color, shininess: 30 })
    const sphere = new THREE.Mesh(sphereGeom, mat)
    sphere.position.set(atom.x, atom.y, atom.z)
    group.add(sphere)
  }

  return group
}

// ---- Build Stick (Bond) Representation ----
export function buildStick(atoms: AtomData[]): THREE.Group {
  const group = new THREE.Group()

  // Build bonds: connect atoms within covalent distance
  const COVALENT_CUTOFF = 1.9
  const bonded = new Set<string>()

  for (let i = 0; i < atoms.length; i++) {
    for (let j = i + 1; j < atoms.length; j++) {
      const a = atoms[i]
      const b = atoms[j]

      // Skip if different chains or non-sequential residues
      if (a.chainID !== b.chainID) continue
      if (Math.abs(a.resSeq - b.resSeq) > 1) continue

      const dx = a.x - b.x
      const dy = a.y - b.y
      const dz = a.z - b.z
      const dist = Math.sqrt(dx * dx + dy * dy + dz * dz)

      if (dist > 0.1 && dist < COVALENT_CUTOFF) {
        const mid = new THREE.Vector3((a.x + b.x) / 2, (a.y + b.y) / 2, (a.z + b.z) / 2)
        const dir = new THREE.Vector3(b.x - a.x, b.y - a.y, b.z - a.z)
        const len = dir.length()

        const cylGeom = new THREE.CylinderGeometry(0.1, 0.1, len, 8, 1)
        const color = getElementColor(a.element)
        const cylMat = new THREE.MeshPhongMaterial({ color, shininess: 30 })

        const cyl = new THREE.Mesh(cylGeom, cylMat)
        cyl.position.copy(mid)

        // Orient cylinder along the bond direction
        const up = new THREE.Vector3(0, 1, 0)
        const quat = new THREE.Quaternion().setFromUnitVectors(up, dir.normalize())
        cyl.setRotationFromQuaternion(quat)

        group.add(cyl)
        bonded.add(`${i}-${j}`)
      }
    }
  }

  return group
}

// ---- Build Surface (Basic) Representation ----
// Phase 4B: simplified — uses a sparse point cloud from atom positions
// Full molecular surface requires solvent-accessible surface algorithms
export function buildSurface(atoms: AtomData[]): THREE.Group {
  const group = new THREE.Group()

  const pointsGeom = new THREE.BufferGeometry()
  const positions: number[] = []
  const colors: number[] = []

  // Surface atoms only (solvent-accessible proxy)
  const surfaceAtoms = atoms.filter(a => {
    // Simple heuristic: atoms near the bounding box surface
    // For Phase 4B this is a basic approximation
    return a.name === 'CA' || a.element === 'C' || a.element === 'N' || a.element === 'O'
  })

  for (const atom of surfaceAtoms) {
    positions.push(atom.x, atom.y, atom.z)
    const color = new THREE.Color(getElementColor(atom.element))
    colors.push(color.r, color.g, color.b)
  }

  pointsGeom.setAttribute('position', new THREE.BufferAttribute(new Float32Array(positions), 3))
  pointsGeom.setAttribute('color', new THREE.BufferAttribute(new Float32Array(colors), 3))

  const mat = new THREE.PointsMaterial({
    size: 0.35,
    vertexColors: true,
    blending: THREE.NormalBlending,
    depthWrite: true
  })

  const points = new THREE.Points(pointsGeom, mat)
  group.add(points)

  return group
}

// ---- Dispose Helper ----
export function disposeGroup(group: THREE.Group): void {
  group.traverse((child: THREE.Object3D) => {
    const mesh = child as THREE.Mesh
    if (mesh.geometry) mesh.geometry.dispose()
    if (mesh.material) {
      if (Array.isArray(mesh.material)) {
        mesh.material.forEach((m: THREE.Material) => m.dispose())
      } else {
        mesh.material.dispose()
      }
    }
  })
  if (group.parent) group.parent.remove(group)
}
