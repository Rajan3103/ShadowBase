// scripts.js — saves migration scripts in the browser (localStorage).
// Each script is { name, sql, updatedAt }. Names are unique: saving an existing
// name overwrites it. If storage is blocked or broken, these functions return
// [] / null / false instead of crashing the page.

const SCRIPTS_KEY = 'shadowbase.savedScripts'

// Reads every saved script. Bad or missing data gives an empty list.
function readAll() {
  try {
    const parsed = JSON.parse(localStorage.getItem(SCRIPTS_KEY) || '[]')
    if (!Array.isArray(parsed)) return []
    // Keep only entries that look like real scripts.
    return parsed.filter((s) => s && typeof s.name === 'string' && typeof s.sql === 'string')
  } catch {
    return []
  }
}

// Writes the whole list back. Returns true if it worked.
function writeAll(scripts) {
  try {
    localStorage.setItem(SCRIPTS_KEY, JSON.stringify(scripts))
    return true
  } catch {
    return false
  }
}

// All saved scripts, newest first.
export function listScripts() {
  return readAll().sort((a, b) => String(b.updatedAt).localeCompare(String(a.updatedAt)))
}

// Saves (or overwrites) a script. Returns the saved script, or null if saving failed.
export function saveScript(name, sql) {
  const script = { name, sql, updatedAt: new Date().toISOString() }
  const others = readAll().filter((s) => s.name !== name)
  return writeAll([...others, script]) ? script : null
}

// Deletes a script by name. Returns true if it worked.
export function deleteScript(name) {
  return writeAll(readAll().filter((s) => s.name !== name))
}

// Finds one script by name, or null if there is none.
export function getScript(name) {
  return readAll().find((s) => s.name === name) || null
}
