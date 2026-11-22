// migrationRisk.js — looks at a migration script and lists operations that could break
// live traffic (shadow replay) before you apply it.
//
// Simple regex-based check. A JSqlParser-based backend check can replace this later via POST /api/analysis.
//
// analyzeMigration(sql) returns an array of findings:
//   { level: "high" | "medium" | "low", rule, message, line }
//   line = the line number (starting at 1) where the statement begins.
//
// Steps:
//   1. Remove comments (-- and /* */) and blank out text inside '...' strings,
//      keeping every newline so line numbers stay correct.
//   2. Split the script into statements on ";".
//   3. Check each statement with case-insensitive regular expressions.
//
// Limits (it is only regex): PostgreSQL $$...$$ blocks are not understood, and a
// statement split across odd syntax may be missed. Treat it as a helper, not a guarantee.

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
// Returns [{ text, line }] for every statement that is not empty.
// (Strings were blanked in step 1, so a ";" inside a string can't split a statement.)
function splitStatements(cleanSql) {
  const statements = []
  let line = 1 // the line we are on while walking through the text
  let current = '' // text of the statement being collected
  let startLine = null // line of its first non-space character

  for (const ch of cleanSql) {
    if (ch === ';') {
      // End of a statement: save it if it has any content.
      if (current.trim()) statements.push({ text: current.trim(), line: startLine })
      current = ''
      startLine = null
    } else {
      // Remember the line of the first real character.
      if (startLine === null && /\S/.test(ch)) startLine = line
      current += ch
    }
    if (ch === '\n') line++
  }
  // The last statement may not end with ";".
  if (current.trim()) statements.push({ text: current.trim(), line: startLine })
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
// add(level, rule, message) records a finding.
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
function checkStatement(text, add) {
  let m

  // ALTER TABLE [IF EXISTS] [ONLY] name <clauses separated by commas>
  m = text.match(new RegExp(`^ALTER\\s+TABLE\\s+(?:IF\\s+EXISTS\\s+)?(?:ONLY\\s+)?${NAME}\\s+([\\s\\S]*)$`, 'i'))
  if (m) {
    const table = cleanName(m[1])
    for (const clause of splitTopLevelCommas(m[2])) {
      checkAlterClause(clause, table, add)
    }
    return
  }

  // ---- HIGH ----
  // DROP TABLE [IF EXISTS] a, b [CASCADE]
  m = text.match(/^DROP\s+TABLE\s+(?:IF\s+EXISTS\s+)?([\s\S]+?)(?:\s+(?:CASCADE|RESTRICT))?$/i)
  if (m) {
    const tables = m[1].split(',').map((name) => `"${cleanName(name.trim())}"`).join(', ')
    add('high', 'DROP_TABLE',
      `Dropping table ${tables} will break every live query and insert that still uses it, and its data is gone.`)
    return
  }
  if ((m = text.match(new RegExp(`^TRUNCATE\\s+(?:TABLE\\s+)?(?:ONLY\\s+)?${NAME}`, 'i')))) {
    add('high', 'TRUNCATE',
      `Truncating "${cleanName(m[1])}" deletes every row in it at once.`)
    return
  }
  // MySQL: RENAME TABLE a TO b
  if ((m = text.match(new RegExp(`^RENAME\\s+TABLE\\s+${NAME}\\s+TO\\s+${NAME}`, 'i')))) {
    add('high', 'RENAME_TABLE',
      `Renaming table "${cleanName(m[1])}" to "${cleanName(m[2])}" will break every live query that still uses the old name.`)
    return
  }
  if ((m = text.match(new RegExp(`^DELETE\\s+FROM\\s+(?:ONLY\\s+)?${NAME}`, 'i')))) {
    if (!/\bWHERE\b/i.test(text)) {
      add('high', 'DELETE_WITHOUT_WHERE',
        `DELETE on "${cleanName(m[1])}" has no WHERE clause, so it removes every row.`)
    }
    return
  }
  if ((m = text.match(new RegExp(`^UPDATE\\s+(?:ONLY\\s+)?${NAME}`, 'i')))) {
    if (!/\bWHERE\b/i.test(text)) {
      add('high', 'UPDATE_WITHOUT_WHERE',
        `UPDATE on "${cleanName(m[1])}" has no WHERE clause, so it changes every row.`)
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
      `Creating an index without CONCURRENTLY blocks writes to ${table} on PostgreSQL until it finishes.`)
  }
}

// ---------- The public function ----------
// Returns all findings, in the order the statements appear in the script.
export function analyzeMigration(sql) {
  const findings = []
  const statements = splitStatements(stripCommentsAndStrings(sql || ''))

  for (const statement of statements) {
    // "add" records a finding for this statement (with its start line).
    const add = (level, rule, message) => findings.push({ level, rule, message, line: statement.line })
    checkStatement(statement.text, add)
  }
  return findings
}

// Counts findings per level: { high, medium, low }.
export function countByLevel(findings) {
  const counts = { high: 0, medium: 0, low: 0 }
  for (const finding of findings) counts[finding.level] += 1
  return counts
}
