/**
 * UniProt ID → PDB ID mapping
 * Only mappings confirmed to exist in RCSB PDB are included.
 * Used by Visualization.vue and MolstarViewer to load correct structures.
 */

const UNIPROT_TO_PDB: Record<string, string> = {
  // ACE2 — Angiotensin-converting enzyme 2 (extracellular domain, no complex)
  'Q9BYF1': '1R42',

  // SARS-CoV-2 Spike glycoprotein
  'P0DTC2': '6VYB',

  // EGFR — Epidermal growth factor receptor (extracellular domain, inactive monomer)
  'P00533': '1NQL',

  // HER2 / ERBB2
  'P04626': '1N8Z',

  // Hsp90 — Heat shock protein 90 (N-terminal domain, apo form)
  'P07900': '1BYQ',

  // CDK2 — Cyclin-dependent kinase 2
  'P24941': '1AQ1',

  // PPAR-gamma
  'P37231': '2PRG',

  // Alpha-synuclein
  'P37840': '1XQ8',

  // Crambin (test structure, 1CRN)
  // Used only for development/testing, not in production mapping
}

/**
 * Get the canonical PDB ID for a given UniProt accession.
 * Returns null if no mapping exists — callers should show an appropriate message.
 */
export function getPdbIdFromUniProt(uniprotId: string): string | null {
  if (!uniprotId) return null
  const upper = uniprotId.toUpperCase()
  return UNIPROT_TO_PDB[upper] ?? null
}

/**
 * Check if a string looks like a 4-character PDB ID.
 */
export function isPdbId(id: string): boolean {
  return /^[A-Za-z0-9]{4}$/.test(id)
}
