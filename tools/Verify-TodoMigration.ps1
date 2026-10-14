param()
$project = Split-Path $PSScriptRoot -Parent
$source = Get-Content -LiteralPath (Join-Path $project 'app/src/main/java/com/example/timemanager/data/local/TodoMigration.kt') -Raw
$statements = @([regex]::Matches($source, '"CREATE TABLE[^\r\n]+"') | ForEach-Object { $_.Value.Trim('"') })
if ($statements.Count -ne 2) { throw 'Expected exactly two additive migration statements.' }
$generated = Get-Content -LiteralPath (Join-Path $project 'app/build/generated/ksp/debug/kotlin/com/example/timemanager/data/local/AppDatabase_Impl.kt') -Raw
foreach ($statement in $statements) {
    if (-not $generated.Contains($statement)) { throw 'Migration does not match the generated Room table definition.' }
}
$testCode = @'
import json, os, sqlite3
db = sqlite3.connect(":memory:")
db.execute("CREATE TABLE usage_history(packageName TEXT NOT NULL,usageDate TEXT NOT NULL,usageSeconds INTEGER NOT NULL,PRIMARY KEY(packageName,usageDate))")
db.execute("INSERT INTO usage_history VALUES (?,?,?)", ("fixture.app", "2026-10-10", 1234))
for sql in json.loads(os.environ["TIMEMANAGER_TODO_MIGRATION"]):
    db.execute(sql)
assert db.execute("SELECT * FROM usage_history").fetchall() == [("fixture.app", "2026-10-10", 1234)]
db.execute("INSERT INTO todos(title,createdAtMillis,createdUsageDay) VALUES (?,?,?)", ("migration fixture", 1, "2026-10-09"))
db.execute("UPDATE todos SET completedAtMillis=2 WHERE id=1")
assert db.execute("SELECT completedAtMillis FROM todos WHERE id=1").fetchone() == (2,)
db.execute("UPDATE todos SET completedAtMillis=NULL WHERE id=1")
assert db.execute("SELECT COUNT(*) FROM todos WHERE completedAtMillis IS NULL AND createdUsageDay < ?", ("2026-10-12",)).fetchone() == (1,)
first = db.execute("INSERT OR IGNORE INTO todo_reminder_receipts VALUES (?,?,?)", ("2026-10-12", "LATE", 1)).rowcount
duplicate = db.execute("INSERT OR IGNORE INTO todo_reminder_receipts VALUES (?,?,?)", ("2026-10-12", "LATE", 2)).rowcount
assert (first, duplicate) == (1, 0)
print("PASS: Room schema matches migration; history preserved; original todo retained; receipt deduplicated")
'@
$info = New-Object System.Diagnostics.ProcessStartInfo
$info.FileName = (Get-Command python).Source
$info.ArgumentList.Add('-c')
$info.ArgumentList.Add($testCode)
$info.Environment['TIMEMANAGER_TODO_MIGRATION'] = ConvertTo-Json -InputObject $statements -Compress
$info.UseShellExecute = $false
$info.CreateNoWindow = $true
$info.RedirectStandardOutput = $true
$info.RedirectStandardError = $true
$process = [System.Diagnostics.Process]::Start($info)
$result = $process.StandardOutput.ReadToEnd()
$errors = $process.StandardError.ReadToEnd()
$process.WaitForExit()
if ($process.ExitCode -ne 0) { throw "Migration verification failed: $errors" }
Write-Output $result.Trim()
$process.Dispose()
