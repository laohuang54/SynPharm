/**
 * Structure Resolver — RCSB PDB dynamic query + smart ranking
 *
 * Resolves a UniProt accession to the best representative experimental structure,
 * with offline fallback via pdbMapping.ts.
 */
import { getPdbIdFromUniProt } from './pdbMapping'

export interface StructureResult {
  source: 'pdb' | 'fallback' | 'none'
  structureId: string | null
  uniprotId: string
  structureUrl: string | null
}

const RCSB_SEARCH_URL = 'https://search.rcsb.org/rcsbsearch/v2/query'
const RCSB_ENTRY_URL = 'https://data.rcsb.org/rest/v1/core/entry'
const PDB_DOWNLOAD_BASE = 'https://files.rcsb.org/download'

interface RCSBEntry {
  identifier: string
  score: number
}

interface CandidateMeta {
  pdbId: string
  title: string
  resolution: number | null
  polymerCount: number
}

// ---- Bad keywords: these suggest the structure is NOT a clean target-protein view ----
const BAD_TITLE_PATTERNS = [
  /antibody/i, /nanobody/i, /\bfab\b/i, /\bscfv\b/i, /\bvh\b/i,
  /fragment screening/i, /fragment hits/i, /fragment-based/i,
  /stapled peptide/i, /macrocyclic peptide/i,
  /in complex with.*antibody/i, /in complex with.*fab/i, /in complex with.*nanobody/i,
  /fusion/i, /chimeric/i, /maltose binding/i, /maltose-binding/i,
  /\bHLA\b/i, /\bMHC\b/i, /histocompatibility/i,
  /amyloid forming segment/i, /MicroED/i,
  /kinase domain/i,
  /in complex with.*COPI/i, /in complex with.*importin/i,
  // Additional generic exclusions
  /cyclophilin/i, /protein disulfide isomerase/i, /oxidized/i,
  /\bpeptide\b.*from\b/i, /\bpeptides\b.*from\b/i, /viral peptides/i,
  /\bL858R\b/i, /\bT790M\b/i, /\bV948R\b/i, // kinase domain mutant constructs
  /in complex with.*peptide/i, // target present only as small peptide
  /\bCbl-c\b/i, /\bCOPI\b/i, /\bWD40\b/i, // non-target scaffold proteins
]

// ---- Good keywords: clean representative structure ----
const GOOD_TITLE_PATTERNS = [
  /\bapo\b/i, /extracellular domain/i, /ectodomain/i,
  /full.?length/i, /\bspike glycoprotein\b/i, /\btrimer\b/i, /trimeric/i,
  /native/i,
]

/**
 * Fetch metadata for a list of PDB candidates.
 */
async function fetchCandidateMeta(pdbIds: string[]): Promise<CandidateMeta[]> {
  const results: CandidateMeta[] = []

  const fetches = pdbIds.map(async (id) => {
    try {
      const res = await fetch(`${RCSB_ENTRY_URL}/${id}`)
      if (!res.ok) return
      const data = await res.json() as any
      const meta: CandidateMeta = {
        pdbId: id,
        title: data.struct?.title || '',
        resolution: data.rcsb_entry_info?.resolution_combined?.[0] ?? null,
        polymerCount: data.rcsb_entry_info?.deposited_polymer_entity_instance_count ?? 1,
      }
      results.push(meta)
    } catch (_) { /* skip failed fetches */ }
  })

  await Promise.allSettled(fetches)
  return results
}

/**
 * Score a candidate structure. Higher = better for web 3D display.
 */
function scoreCandidate(c: CandidateMeta): number {
  let score = 0

  // Resolution bonus: prefer better resolution, normalized roughly: 1/resolution * 5
  if (c.resolution && c.resolution > 0) {
    score += (1.0 / c.resolution) * 3
  }

  // Penalize bad keywords
  for (const pattern of BAD_TITLE_PATTERNS) {
    if (pattern.test(c.title)) {
      score -= 20
      break // only penalize once per bad category
    }
  }

  // Bonus for good keywords
  for (const pattern of GOOD_TITLE_PATTERNS) {
    if (pattern.test(c.title)) {
      score += 8
      break
    }
  }

  // Penalize multi-polymer complexes (more chains = more likely to be a complex)
  if (c.polymerCount > 2) score -= (c.polymerCount - 2) * 3

  return score
}

/**
 * Search RCSB and return the best representative PDB ID.
 */
async function searchRCSB(uniprotId: string): Promise<string | null> {
  const query = {
    query: {
      type: 'group' as const,
      logical_operator: 'and' as const,
      nodes: [
        {
          type: 'terminal' as const,
          service: 'text' as const,
          parameters: {
            attribute: 'rcsb_polymer_entity_container_identifiers.reference_sequence_identifiers.database_accession',
            operator: 'exact_match' as const,
            value: uniprotId
          }
        },
        {
          type: 'terminal' as const,
          service: 'text' as const,
          parameters: {
            attribute: 'rcsb_entry_info.structure_determination_methodology',
            operator: 'exact_match' as const,
            value: 'experimental'
          }
        }
      ]
    },
    return_type: 'entry' as const,
    request_options: {
      paginate: { start: 0, rows: 15 },
      sort: [
        { sort_by: 'rcsb_entry_info.resolution_combined', direction: 'asc' as const }
      ],
      results_content_type: ['experimental' as const],
      scoring_strategy: 'combined' as const
    }
  }

  const response = await fetch(RCSB_SEARCH_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(query)
  })

  if (!response.ok) return null

  const data = await response.json() as { result_set?: RCSBEntry[] }
  const entries = data.result_set || []
  if (entries.length === 0) return null

  // Fetch metadata for all candidates
  const pdbIds = entries.map(e => e.identifier)
  const candidates = await fetchCandidateMeta(pdbIds)

  if (candidates.length === 0) return null

  // Score and pick best
  let best = candidates[0]
  let bestScore = scoreCandidate(best)

  for (let i = 1; i < candidates.length; i++) {
    const s = scoreCandidate(candidates[i])
    if (s > bestScore) {
      bestScore = s
      best = candidates[i]
    }
  }

  console.log(`[StructureResolver] ${uniprotId}: scored ${candidates.length} candidates, best=${best.pdbId} (score=${bestScore.toFixed(1)}, title="${best.title.substring(0, 80)}")`)

  // If the best candidate scored poorly (complex/fragment), return null to use fallback
  // Threshold of 2.5: structures scoring below this are almost certainly fragments/complexes,
  // and our offline fallback mappings typically have better representative structures.
  if (bestScore < 2.5) {
    console.log(`[StructureResolver] ${uniprotId}: best candidate ${best.pdbId} scored ${bestScore.toFixed(1)} < 2.5 — using fallback`)
    return null
  }

  return best.pdbId
}

/**
 * Resolve the best 3D structure for a UniProt accession.
 *
 * Priority:
 *   1. RCSB PDB experimental structures (scored for representativeness)
 *   2. Offline pdbMapping.ts fallback
 *   3. 'none' if nothing found
 */
export async function resolveStructure(uniprotId: string): Promise<StructureResult> {
  if (!uniprotId) {
    return { source: 'none', structureId: null, uniprotId, structureUrl: null }
  }

  const upper = uniprotId.toUpperCase()

  // Try RCSB dynamic query with smart ranking
  try {
    const pdbId = await searchRCSB(upper)
    if (pdbId) {
      return {
        source: 'pdb',
        structureId: pdbId,
        uniprotId: upper,
        structureUrl: `${PDB_DOWNLOAD_BASE}/${pdbId}.pdb`
      }
    }
  } catch (err) {
    console.warn('[StructureResolver] RCSB query failed for', upper, err)
  }

  // Fallback: offline pdbMapping
  const fallbackPdb = getPdbIdFromUniProt(upper)
  if (fallbackPdb) {
    console.log(`[StructureResolver] ${upper}: using fallback ${fallbackPdb}`)
    return {
      source: 'fallback',
      structureId: fallbackPdb,
      uniprotId: upper,
      structureUrl: `${PDB_DOWNLOAD_BASE}/${fallbackPdb}.pdb`
    }
  }

  return { source: 'none', structureId: null, uniprotId: upper, structureUrl: null }
}

// Re-export for convenience
export { getPdbIdFromUniProt } from './pdbMapping'
