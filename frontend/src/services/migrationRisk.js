// migrationRisk.js — looks at a migration script and lists operations that could break
// live traffic (shadow replay) before you apply it.
//
// Simple regex-based check. A JSqlParser-based backend check can replace this later via POST /api/analysis.
// That server check now exists (POST /api/analysis/migration-risk): the page calls
// analyzeMigrationWithFallback(sql), which asks the server first and uses this local check
// in mock mode or when the server can't be reached.
//
// analyzeMigration(sql) returns an array of findings:
//   { level: "high" | "medium" | "low", rule, message, line, statement, table }
//   line      = the line number (starting at 1) where the statement begins
//   statement = the statement's text on one line (comments before it are left out)
//   table     = the table it changes, or null if unknown
//
// Steps:
//   1. Remove comments (-- and /* */) and blank out text inside '...' strings,
//      keeping every newline so line numbers stay correct.
//   2. Split the script into statements on ";".
//   3. Check each statement with case-insensitive regular expressions.
//
// Limits (it is only regex): PostgreSQL $$...$$ blocks are not understood, and a
// statement split across odd syntax may be missed. Treat it as a helper, not a guarantee.
import { isMockMode, analyzeMigrationRemote, getErrorMessage } from './api.js'

// Matches one table/column name, with or without quotes: users, "Users", `users`, [users], public.users
const NAME = '((?:[`"\\[]?[\\w$]+[`"\\]]?\\.)?[`"\\[]?[\\w$]+[`"\\]]?)'

// Removes quotes/backticks/brackets from a name: "Users" → Users
function cleanName(name) {
  return (name || '').replace(/[`"[\]]/g, '')
}

// ---------- Step 1: remove comments and string contents ----------
// Goes through the text one character at a time. Comment characters and characters
// inside '...' strings become spaces; newlines are always kept.
function stripCommentsAndStrings(sql) {
  let out = ''
  let i = 0
  while (i < sql.length) {
    const ch = sql[i]
    const next = sql[i + 1]

    if (ch === '-' && next === '-') {
      // "--" comment: blank everything up to the end of the line.
      while (i < sql.length && sql[i] !== '\n') {
        out += ' '
        i++
      }
    } else if (ch === '/' && next === '*') {
      // "/* ... */" comment: blank everything up to "*/" (keep newlines).
      out += '  '
      i += 2
      while (i < sql.length && !(sql[i] === '*' && sql[i + 1] === '/')) {
        out += sql[i] === '\n' ? '\n' : ' '
        i++
      }
      if (i < sql.length) {
        out += '  ' // the closing "*/"
        i += 2
      }
    } else if (ch === "'") {
      // '...' string: keep the quotes, blank what is inside ('' is an escaped quote).
      out += "'"
      i++
      while (i < sql.length) {
        if (sql[i] === "'" && sql[i + 1] === "'") {
          out += '  '
          i += 2
        } else if (sql[i] === "'") {
          break
        } else {
          out += sql[i] === '\n' ? '\n' : ' '
          i++
        }
      }
      if (i < sql.length) {
        out += "'"
        i++
      }
    } else {
      out += ch
      i++
    }
  }
  return out
}

// ---------- Step 2: split into statements ----------
// Returns [{ text, line, start, end }] for every statement that is not empty.
//   start / end = where the statement is in the text, so we can cut the ORIGINAL
//   statement out of the script (step 1 keeps every character in the same place).
// (Strings were blanked in step 1, so a ";" inside a string can't split a statement.)
function splitStatements(cleanSql) {
  const statements = []
  let line = 1 // the line we are on while walking through the text
  let current = '' // text of the statement being collected
  let startLine = null // line of its first non-space character
  let startIndex = null // position of its first non-space character

  for (let i = 0; i < cleanSql.length; i++) {
    const ch = cleanSql[i]
    if (ch === ';') {
      // End of a statement: save it if it has any content.
      if (current.trim()) {
        statements.push({ text: current.trim(), line: startLine, start: startIndex, end: i })
      }
      current = ''
      startLine = null
      startIndex = null
    } else {
      // Remember where the first real character is.
      if (startLine === null && /\S/.test(ch)) {
        startLine = line
        startIndex = i
      }
      current += ch
    }
    if (ch === '\n') line++
  }
  // The last statement may not end with ";".
  if (current.trim()) {
    statements.push({ text: current.trim(), line: startLine, start: startIndex, end: cleanSql.length })
  }
  return statements
}

// Splits text on commas that are not inside ( ). Used for "ALTER TABLE t ADD ..., DROP ...".
function splitTopLevelCommas(text) {
  const parts = []
  let depth = 0
  let current = ''
  for (const ch of text) {
    if (ch === '(') depth++
    if (ch === ')') depth = Math.max(0, depth - 1)
    if (ch === ',' && depth === 0) {
      parts.push(current.trim())
      current = ''
    } else {
      current += ch
    }
  }
  if (current.trim()) parts.push(current.trim())
  return parts
}

// ---------- Step 3a: checks for one part of an ALTER TABLE statement ----------
// "clause" is e.g. 'DROP COLUMN phone' or 'ADD COLUMN city VARCHAR(100)'.
// add(level, rule, message) records a finding (the table is added by the caller).
function checkAlterClause(clause, table, add) {
  let m

  // ---- HIGH ----
  if ((m = clause.match(new RegExp(`^DROP\\s+COLUMN\\s+(?:IF\\s+EXISTS\\s+)?${NAME}`, 'i')))) {
    add('high', 'DROP_COLUMN',
      `Dropping column "${cleanName(m[1])}" from "${table}" will break any live query or insert that still uses it.`)
    return
  }
  if ((m = clause.match(new RegExp(`^RENAME\\s+COLUMN\\s+${NAME}\\s+TO\\s+${NAME}`, 'i')))) {
    add('high', 'RENAME_COLUMN',
      `Renaming column "${cleanName(m[1])}" to "${cleanName(m[2])}" in "${table}" will break every live query that still uses the old name.`)
    return
  }
  if ((m = clause.match(new RegExp(`^RENAME\\s+(?:TO|AS)\\s+${NAME}`, 'i')))) {
    add('high', 'RENAME_TABLE',
      `Renaming table "${table}" to "${cleanName(m[1])}" will break every live query that still uses the old name.`)
    return
  }

  // ---- MEDIUM ----
  // PostgreSQL: ALTER COLUMN x TYPE ... / ALTER COLUMN x SET DATA TYPE ...
  if ((m = clause.match(new RegExp(`^ALTER\\s+(?:COLUMN\\s+)?${NAME}\\s+(?:SET\\s+DATA\\s+)?TYPE\\b`, 'i')))) {
    add('medium', 'CHANGE_COLUMN_TYPE',
      `Changing the type of column "${cleanName(m[1])}" in "${table}" can reject values that old code still sends, and may rewrite (lock) the whole table.`)
    return
  }
  // MySQL: MODIFY [COLUMN] x <new type>
  if ((m = clause.match(new RegExp(`^MODIFY\\s+(?:COLUMN\\s+)?${NAME}`, 'i')))) {
    add('medium', 'CHANGE_COLUMN_TYPE',
      `Modifying column "${cleanName(m[1])}" in "${table}" changes its type or rules; inserts from old code may start failing.`)
    return
  }
  if ((m = clause.match(new RegExp(`^ALTER\\s+(?:COLUMN\\s+)?${NAME}\\s+SET\\s+NOT\\s+NULL`, 'i')))) {
    add('medium', 'SET_NOT_NULL',
      `Making column "${cleanName(m[1])}" in "${table}" NOT NULL will reject inserts that leave it empty, and fails if any existing row is empty.`)
    return
  }
  if ((m = clause.match(new RegExp(`^DROP\\s+(?:CONSTRAINT|FOREIGN\\s+KEY)\\s+(?:IF\\s+EXISTS\\s+)?${NAME}`, 'i')))) {
    add('medium', 'DROP_CONSTRAINT',
      `Dropping constraint "${cleanName(m[1])}" on "${table}" removes a data check; bad data can get in without an error.`)
    return
  }
  if ((m = clause.match(new RegExp(`^DROP\\s+(?:INDEX|KEY)\\s+(?:IF\\s+EXISTS\\s+)?${NAME}`, 'i')))) {
    add('medium', 'DROP_INDEX',
      `Dropping index "${cleanName(m[1])}" on "${table}" can make live queries much slower (or remove a uniqueness rule).`)
    return
  }
  if (/^ADD\s+(?:CONSTRAINT\b|UNIQUE\b|FOREIGN\s+KEY\b|PRIMARY\s+KEY\b|CHECK\b)/i.test(clause)) {
    const named = clause.match(new RegExp(`^ADD\\s+CONSTRAINT\\s+${NAME}`, 'i'))
    const label = named ? `constraint "${cleanName(named[1])}"` : 'a constraint'
    add('medium', 'ADD_CONSTRAINT',
      `Adding ${label} to "${table}" will reject inserts and updates from old code that break the new rule, and fails if existing rows already break it.`)
    return
  }

  // ---- ADD COLUMN (medium if NOT NULL without DEFAULT, otherwise low) ----
  // Skip MySQL "ADD INDEX / ADD KEY / ADD FULLTEXT ..." — those are not columns.
  if (/^ADD\s+(?:INDEX|KEY|FULLTEXT|SPATIAL|PARTITION)\b/i.test(clause)) return
  if ((m = clause.match(new RegExp(`^ADD\\s+(?:COLUMN\\s+)?(?:IF\\s+NOT\\s+EXISTS\\s+)?${NAME}`, 'i')))) {
    const column = cleanName(m[1])
    const notNull = /\bNOT\s+NULL\b/i.test(clause)
    const hasDefault = /\bDEFAULT\b/i.test(clause)
    if (notNull && !hasDefault) {
      add('medium', 'ADD_NOT_NULL_COLUMN',
        `Adding NOT NULL column "${column}" to "${table}" without a DEFAULT fails if the table has rows, and old inserts that don't send "${column}" will be rejected.`)
    } else {
      add('low', 'ADD_COLUMN',
        `Adding column "${column}" to "${table}" is usually safe: old queries keep working${notNull ? ' because it has a DEFAULT' : ' because it allows NULL'}.`)
    }
  }
}

// ---------- Step 3b: checks for one whole statement ----------
// add(level, rule, message, table) records a finding.
function checkStatement(text, add) {
  let m

  // ALTER TABLE [IF EXISTS] [ONLY] name <clauses separated by commas>
  m = text.match(new RegExp(`^ALTER\\s+TABLE\\s+(?:IF\\s+EXISTS\\s+)?(?:ONLY\\s+)?${NAME}\\s+([\\s\\S]*)$`, 'i'))
  if (m) {
    const table = cleanName(m[1])
    // Every finding in an ALTER TABLE statement is about this table.
    const addWithTable = (level, rule, message) => add(level, rule, message, table)
    for (const clause of splitTopLevelCommas(m[2])) {
      checkAlterClause(clause, table, addWithTable)
    }
    return
  }

  // ---- HIGH ----
  // DROP TABLE [IF EXISTS] a, b [CASCADE]
  m = text.match(/^DROP\s+TABLE\s+(?:IF\s+EXISTS\s+)?([\s\S]+?)(?:\s+(?:CASCADE|RESTRICT))?$/i)
  if (m) {
    const names = m[1].split(',').map((name) => cleanName(name.trim()))
    const tables = names.map((name) => `"${name}"`).join(', ')
    add('high', 'DROP_TABLE',
      `Dropping table ${tables} will break every live query and insert that still uses it, and its data is gone.`,
      names.join(', '))
    return
  }
  if ((m = text.match(new RegExp(`^TRUNCATE\\s+(?:TABLE\\s+)?(?:ONLY\\s+)?${NAME}`, 'i')))) {
    add('high', 'TRUNCATE',
      `Truncating "${cleanName(m[1])}" deletes every row in it at once.`, cleanName(m[1]))
    return
  }
  // MySQL: RENAME TABLE a TO b
  if ((m = text.match(new RegExp(`^RENAME\\s+TABLE\\s+${NAME}\\s+TO\\s+${NAME}`, 'i')))) {
    add('high', 'RENAME_TABLE',
      `Renaming table "${cleanName(m[1])}" to "${cleanName(m[2])}" will break every live query that still uses the old name.`,
      cleanName(m[1]))
    return
  }
  if ((m = text.match(new RegExp(`^DELETE\\s+FROM\\s+(?:ONLY\\s+)?${NAME}`, 'i')))) {
    if (!/\bWHERE\b/i.test(text)) {
      add('high', 'DELETE_WITHOUT_WHERE',
        `DELETE on "${cleanName(m[1])}" has no WHERE clause, so it removes every row.`, cleanName(m[1]))
    }
    return
  }
  if ((m = text.match(new RegExp(`^UPDATE\\s+(?:ONLY\\s+)?${NAME}`, 'i')))) {
    if (!/\bWHERE\b/i.test(text)) {
      add('high', 'UPDATE_WITHOUT_WHERE',
        `UPDATE on "${cleanName(m[1])}" has no WHERE clause, so it changes every row.`, cleanName(m[1]))
    }
    return
  }

  // ---- MEDIUM ----
  if ((m = text.match(new RegExp(`^DROP\\s+INDEX\\s+(?:CONCURRENTLY\\s+)?(?:IF\\s+EXISTS\\s+)?${NAME}`, 'i')))) {
    add('medium', 'DROP_INDEX',
      `Dropping index "${cleanName(m[1])}" can make live queries much slower (or remove a uniqueness rule).`)
    return
  }

  // ---- LOW ----
  // CREATE [UNIQUE] INDEX without CONCURRENTLY (PostgreSQL locks writes while it builds).
  if (/^CREATE\s+(?:UNIQUE\s+)?INDEX\b/i.test(text) && !/^CREATE\s+(?:UNIQUE\s+)?INDEX\s+CONCURRENTLY\b/i.test(text)) {
    const on = text.match(new RegExp(`\\bON\\s+(?:ONLY\\s+)?${NAME}`, 'i'))
    const table = on ? `"${cleanName(on[1])}"` : 'the table'
    add('low', 'CREATE_INDEX_LOCKS',
      `Creating an index without CONCURRENTLY blocks writes to ${table} on PostgreSQL until it finishes.`,
      on ? cleanName(on[1]) : null)
  }
}

// ---------- The public function ----------
// Returns all findings, in the order the statements appear in the script.
export function analyzeMigration(sql) {
  const findings = []
  const statements = splitStatements(stripCommentsAndStrings(sql || ''))

  for (const statement of statements) {
    // The original statement text (with its strings), squeezed onto one line.
    const original = (sql || '').slice(statement.start, statement.end).replace(/\s+/g, ' ').trim()
    // "add" records a finding for this statement (with its start line and text).
    const add = (level, rule, message, table = null) =>
      findings.push({ level, rule, message, line: statement.line, statement: original, table })
    checkStatement(statement.text, add)
  }
  return findings
}

// Counts findings per level: { high, medium, low }.
export function countByLevel(findings) {
  const counts = { high: 0, medium: 0, low: 0 }
  for (const finding of findings) {
    if (finding.level in counts) counts[finding.level] += 1
  }
  return counts
}

// ---------- Server check with local fallback ----------

// Makes one finding look the same whichever check produced it:
// level always lowercase, line a number (or null), missing fields as null.
function normalizeFinding(finding) {
  const line = Number(finding?.line)
  return {
    level: String(finding?.level || 'low').toLowerCase(),
    rule: finding?.rule ?? '',
    message: finding?.message ?? '',
    line: Number.isInteger(line) && line > 0 ? line : null,
    statement: finding?.statement || null,
    table: finding?.table || null,
  }
}

// Runs the local (browser) check and returns it in the same shape as the server check.
// "reason" is set when we fell back because the server failed.
function localResult(sql, reason) {
  const findings = analyzeMigration(sql).map(normalizeFinding)
  const counts = countByLevel(findings)
  return {
    source: 'local',
    reason: reason || null,
    summary: { ...counts, total: findings.length, hasBreakingRisks: counts.high > 0 },
    findings,
  }
}

// Checks a migration script for risks.
// - Real backend: POST /api/analysis/migration-risk → { source: "server", summary, findings }
// - Mock mode, or the server request fails: the local check → { source: "local", reason?, ... }
// summary = { high, medium, low, total, hasBreakingRisks }
export async function analyzeMigrationWithFallback(sql) {
  if (isMockMode()) return localResult(sql, null)

  try {
    const data = await analyzeMigrationRemote(sql)
    const findings = (Array.isArray(data?.findings) ? data.findings : []).map(normalizeFinding)
    const counts = countByLevel(findings)
    return {
      source: 'server',
      reason: null,
      summary: {
        // Prefer the server's own counts; use our count if a number is missing.
        high: data?.highRisks ?? counts.high,
        medium: data?.mediumRisks ?? counts.medium,
        low: data?.lowRisks ?? counts.low,
        total: data?.totalRisks ?? findings.length,
        hasBreakingRisks: Boolean(data?.hasBreakingRisks) || counts.high > 0,
      },
      findings,
    }
  } catch (err) {
    return localResult(sql, `Server check failed: ${getErrorMessage(err)}`)
  }
}
