# Checks Android string references and escaping without the Android SDK.
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$valuesPath = Join-Path $project 'app/src/main/res/values'
$strings = @(Get-ChildItem -LiteralPath $valuesPath -Filter '*.xml' -File | ForEach-Object {
    [xml]$xml = Get-Content -LiteralPath $_.FullName -Raw -Encoding UTF8
    if ($null -ne $xml.resources.string) {
        @($xml.resources.string)
    }
})
$names = @($strings | ForEach-Object { $_.GetAttribute('name') })
$duplicates = @($names | Group-Object | Where-Object Count -gt 1)
if ($duplicates) { throw "Duplicate strings: $($duplicates.Name -join ', ')" }

$references = @(Get-ChildItem (Join-Path $project 'app/src') -Recurse -File |
    Where-Object { $_.Extension -in '.kt', '.java' } | ForEach-Object {
        $source = Get-Content -LiteralPath $_.FullName -Raw -Encoding UTF8
        [regex]::Matches($source, '(?<!android\.)\bR\.string\.([A-Za-z_][A-Za-z0-9_]*)') |
            ForEach-Object { $_.Groups[1].Value }
    } | Sort-Object -Unique)
$missing = @($references | Where-Object { $_ -cnotin $names })
if ($missing) { throw "Missing R.string resources: $($missing -join ', ')" }

# An apostrophe requires an odd number of preceding backslashes, including XML entities.
$unescaped = @($strings | Where-Object {
    [regex]::IsMatch($_.InnerText, "(?<!\\)(?:\\\\)*'")
} | ForEach-Object { $_.GetAttribute('name') })
if ($unescaped) { throw "Unescaped apostrophes: $($unescaped -join ', ')" }
Write-Output "PASS: values XML files parse as XML ($($names.Count) strings)."
Write-Output "PASS: all $($references.Count) referenced R.string names exist."
Write-Output 'PASS: all apostrophes are escaped.'
