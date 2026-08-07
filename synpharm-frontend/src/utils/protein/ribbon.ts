/**
 * Stable Ribbon Geometry Builder
 *
 * Uses a fixed reference-normal approach per SS segment rather than
 * parallel transport, avoiding normal-flip / twisting artifacts.
 */
import * as THREE from 'three'

export interface RibbonSegment {
  caPoints: THREE.Vector3[]
  ssType: 'helix' | 'sheet' | 'loop'
}

// ---- Build a flat ribbon strip from a curve with a STABLE normal ----
export function buildStableRibbon(
  curve: THREE.CatmullRomCurve3,
  width: number,
  sampleCount: number,
  arrowTail: boolean
): THREE.BufferGeometry {
  const pts = curve.getPoints(sampleCount)
  if (pts.length < 2) return new THREE.BufferGeometry()

  // Compute tangents from curve
  const tangents: THREE.Vector3[] = []
  for (let i = 0; i < pts.length; i++) {
    const t = curve.getTangent(i / (pts.length - 1)).normalize()
    tangents.push(t)
  }

  // Compute a STABLE reference normal for the entire segment:
  // Use the principal plane of the CA points to determine orientation.
  // The reference "up" is the average direction perpendicular to the
  // backbone, derived from the first principal component of the cross
  // products between successive CA-CA vectors.
  const referenceUp = computeStableReferenceUp(pts, tangents)

  // Build ribbon vertices with the fixed reference
  const halfW = width / 2
  const vertices: number[] = []
  const indices: number[] = []

  for (let i = 0; i < pts.length; i++) {
    const p = pts[i]
    const tangent = tangents[i]

    // Normal = cross(tangent, referenceUp), perpendicular to backbone
    const nrm = new THREE.Vector3()
    nrm.crossVectors(tangent, referenceUp)
    if (nrm.length() < 0.001) {
      // Tangent nearly parallel to referenceUp — fallback
      const fb = new THREE.Vector3(1, 0, 0)
      nrm.crossVectors(tangent, fb)
      if (nrm.length() < 0.001) nrm.crossVectors(tangent, new THREE.Vector3(0, 0, 1))
    }
    nrm.normalize()

    // Flip check against previous normal (prevent 180° flips)
    if (i > 0) {
      const prevNrm = new THREE.Vector3(
        vertices[(i - 1) * 6 + 3] - vertices[(i - 1) * 6],
        vertices[(i - 1) * 6 + 4] - vertices[(i - 1) * 6 + 1],
        vertices[(i - 1) * 6 + 5] - vertices[(i - 1) * 6 + 2]
      ).normalize()
      if (nrm.dot(prevNrm) < 0) {
        nrm.negate()
      }
    }

    const left = p.clone().addScaledVector(nrm, -halfW)
    const right = p.clone().addScaledVector(nrm, halfW)
    vertices.push(left.x, left.y, left.z)
    vertices.push(right.x, right.y, right.z)
  }

  // Arrow tail for sheets
  if (arrowTail && pts.length >= 4) {
    const lastIdx = (pts.length - 1) * 2
    // Widen the last two vertices
    const endPt = pts[pts.length - 1]
    const endTangent = tangents[pts.length - 1]
    const endNrm = new THREE.Vector3().crossVectors(endTangent, referenceUp).normalize()

    // Flip check for arrow normal
    const prevArrowNrm = new THREE.Vector3(
      vertices[lastIdx - 2] - vertices[lastIdx - 4],
      vertices[lastIdx - 1] - vertices[lastIdx - 3],
      vertices[lastIdx] - vertices[lastIdx - 2]
    ).normalize()
    if (endNrm.dot(prevArrowNrm) < 0) endNrm.negate()

    const arrowW = width * 1.6
    const arrowLeft = endPt.clone().addScaledVector(endNrm, -arrowW / 2)
    const arrowRight = endPt.clone().addScaledVector(endNrm, arrowW / 2)
    vertices[lastIdx] = arrowLeft.x
    vertices[lastIdx + 1] = arrowLeft.y
    vertices[lastIdx + 2] = arrowLeft.z
    vertices[lastIdx + 2] = arrowRight.x  // right follows left+3
    // Actually redo: left is at index lastIdx (x,y,z), right at lastIdx+1 (+3 offset)
    // Vertex layout: [L0x,L0y,L0z, R0x,R0y,R0z, L1x,L1y,L1z, R1x,R1y,R1z, ...]
    // So left_i = i*6, right_i = i*6+3
    const li = (pts.length - 1) * 6        // offset of last left vertex
    const ri = li + 3                       // offset of last right vertex
    vertices[li] = arrowLeft.x
    vertices[li + 1] = arrowLeft.y
    vertices[li + 2] = arrowLeft.z
    vertices[ri] = arrowRight.x
    vertices[ri + 1] = arrowRight.y
    vertices[ri + 2] = arrowRight.z
  }

  // Triangles
  for (let i = 0; i < pts.length - 1; i++) {
    const a = i * 2       // left_i
    const b = i * 2 + 1   // right_i
    const c = (i + 1) * 2 // left_{i+1}
    const d = (i + 1) * 2 + 1 // right_{i+1}
    indices.push(a, c, b)
    indices.push(b, c, d)
  }

  const geom = new THREE.BufferGeometry()
  geom.setAttribute('position', new THREE.BufferAttribute(new Float32Array(vertices), 3))
  geom.setIndex(indices)
  geom.computeVertexNormals()
  return geom
}

// ---- Compute stable reference "up" direction for a segment ----
// Uses the average direction of cross(CA[i]-CA[i-1], CA[i+1]-CA[i])
// which points approximately along the helix axis for helical segments,
// giving a natural "ribbon plane" orientation.
function computeStableReferenceUp(
  pts: THREE.Vector3[],
  _tangents: THREE.Vector3[]
): THREE.Vector3 {
  const accumulated = new THREE.Vector3()

  for (let i = 1; i < pts.length - 1; i++) {
    const v1 = new THREE.Vector3().subVectors(pts[i], pts[i - 1])
    const v2 = new THREE.Vector3().subVectors(pts[i + 1], pts[i])
    const cr = new THREE.Vector3().crossVectors(v1, v2)
    if (cr.length() > 0.0001) {
      cr.normalize()
      accumulated.add(cr)
    }
  }

  if (accumulated.length() < 0.0001) {
    // Fallback: use world Y axis
    accumulated.set(0, 1, 0)
  }

  accumulated.normalize()
  return accumulated
}

// ---- Build loop tube (thin, no centerline) ----
export function buildLoopTube(
  curve: THREE.CatmullRomCurve3,
  color: THREE.Color
): THREE.Mesh {
  const tubeGeom = new THREE.TubeGeometry(curve, curve.points.length * 6, 0.1, 6, false)
  const mat = new THREE.MeshPhongMaterial({
    color,
    shininess: 30,
    specular: new THREE.Color(0x111111)
  })
  return new THREE.Mesh(tubeGeom, mat)
}
