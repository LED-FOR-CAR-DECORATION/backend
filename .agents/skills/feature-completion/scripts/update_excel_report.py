# -*- coding: utf-8 -*-
"""
Generates a SEP490/FPT Unit Test Report (.xlsx) from the template
`Unit Test.xls` using Microsoft Excel COM automation so 100% of original cell
styles, borders, merged cells, colors, and formulas are preserved identically.

Every run creates a new timestamped copy in `d:\\Khanh Stuffs\\Codes\\SEP490\\antigravity-output`
(or a user-specified output path) and asserts that all unit test cases have
status == "P" (Passed).
"""

import argparse
import datetime
import json
import os
import shutil
import subprocess
import tempfile


DEFAULT_TEMPLATE = r"d:\Khanh Stuffs\Codes\SEP490\antigravity-input\Unit Test.xls"
DEFAULT_OUTPUT_DIR = r"d:\Khanh Stuffs\Codes\SEP490\antigravity-output"


def validate_payload(data: dict) -> None:
    functions = data.get("functions", [])
    if not functions:
        raise ValueError("Payload must contain at least one entry in 'functions'.")

    for fn in functions:
        fn_name = fn.get("functionName", "<unknown>")
        tcs = fn.get("testCases", [])
        if not tcs:
            raise ValueError(f"Function '{fn_name}' must contain at least one test case.")

        for idx, tc in enumerate(tcs, start=1):
            tc_id = tc.get("id", f"UTCID{idx:02d}")
            status = str(tc.get("status", "P")).strip().upper()
            if status != "P":
                raise ValueError(
                    f"Test case {tc_id} in function '{fn_name}' has status '{status}'. "
                    "All unit tests must pass (status='P') before generating the Excel report!"
                )
            tc_type = str(tc.get("type", "N")).strip().upper()
            if tc_type not in ("N", "A", "B"):
                raise ValueError(
                    f"Test case {tc_id} in function '{fn_name}' has invalid type '{tc_type}'. "
                    "Allowed types are 'N' (Normal), 'A' (Abnormal), 'B' (Boundary)."
                )


POWERSHELL_SCRIPT = r"""
param(
    [string]$TemplatePath,
    [string]$OutputPath,
    [string]$PayloadPath
)

$ErrorActionPreference = "Stop"

# Helper to unwrap PSObject-wrapped strings from ConvertFrom-Json so Excel COM accepts them
function S($val) {
    if ($null -eq $val) { return "" }
    return ([string]$val).PSObject.BaseObject
}

$jsonText = Get-Content -Raw -Encoding UTF8 -Path $PayloadPath
$payload = $jsonText | ConvertFrom-Json

$excel = New-Object -ComObject Excel.Application
$excel.Visible = $false
$excel.DisplayAlerts = $false

try {
    $wb = $excel.Workbooks.Open($TemplatePath)

    # 1. Update Cover sheet
    $wsCover = $wb.Worksheets.Item("Cover")
    if ($payload.projectName)  { $wsCover.Cells.Item(4, 2).Value2 = (S $payload.projectName) }
    if ($payload.projectCode)  { $wsCover.Cells.Item(5, 2).Value2 = (S $payload.projectCode) }
    if ($payload.creator)      { $wsCover.Cells.Item(4, 6).Value2 = (S $payload.creator) }
    if ($payload.issueDate)    { $wsCover.Cells.Item(5, 6).Value2 = (S $payload.issueDate) }
    if ($payload.version)      { $wsCover.Cells.Item(6, 6).Value2 = (S $payload.version) }
    if ($payload.documentCode) { $wsCover.Cells.Item(6, 2).Value2 = (S $payload.documentCode) }

    # 2. Update Functions sheet metadata
    $wsFuncs = $wb.Worksheets.Item("Functions")
    if ($payload.testEnvironment) {
        $wsFuncs.Cells.Item(7, 5).Value2 = (S $payload.testEnvironment)
    }

    # Clear sample rows in Functions (rows 11..16)
    for ([int]$r = 11; $r -le 16; $r++) {
        for ([int]$c = 1; $c -le 8; $c++) {
            $wsFuncs.Cells.Item([int]$r, [int]$c).Value2 = $null
        }
    }

    # 3. Update Statistics sheet metadata
    $wsStats = $wb.Worksheets.Item("Statistics")
    if ($payload.creator)   { $wsStats.Cells.Item(4, 6).Value2 = (S $payload.creator) }
    if ($payload.reviewer)  { $wsStats.Cells.Item(5, 6).Value2 = (S $payload.reviewer) }
    if ($payload.issueDate) { $wsStats.Cells.Item(6, 6).Value2 = (S $payload.issueDate) }
    if ($payload.notes)     { $wsStats.Cells.Item(7, 2).Value2 = (S $payload.notes) }

    # Clear sample rows in Statistics (rows 12..16)
    for ([int]$r = 12; $r -le 16; $r++) {
        for ([int]$c = 1; $c -le 9; $c++) {
            $wsStats.Cells.Item([int]$r, [int]$c).Value2 = $null
        }
    }

    # Use 'Function 1' as the master template sheet
    $wsTemplate = $wb.Worksheets.Item("Function 1")

    $functions = @($payload.functions)
    [int]$funcCount = $functions.Count

    # If more than 5 functions, insert extra rows in Functions and Statistics before subtotal
    if ($funcCount -gt 5) {
        [int]$extra = $funcCount - 5
        for ([int]$i = 0; $i -lt $extra; $i++) {
            $wsFuncs.Rows.Item(15).Insert() | Out-Null
            $wsStats.Rows.Item(16).Insert() | Out-Null
        }
    }

    $createdSheets = @()

    for ([int]$fIdx = 0; $fIdx -lt $funcCount; $fIdx++) {
        $fn = $functions[$fIdx]
        [int]$rowFuncs = 11 + $fIdx
        [int]$rowStats = 12 + $fIdx

        $sheetName = if ($fn.sheetName) { S $fn.sheetName } else { S "Function $($fIdx + 1)" }
        if ($sheetName.Length -gt 31) { $sheetName = S $sheetName.Substring(0, 31) }

        $funcCode = if ($fn.functionCode) { S $fn.functionCode } else { S "FN-0$($fIdx + 1)" }
        $funcName = S $fn.functionName
        $className = S $fn.className
        $reqName = if ($fn.requirementName) { S $fn.requirementName } else { $funcName }
        $desc = if ($fn.description) { S $fn.description } else { $funcName }
        $preCondText = if ($fn.preConditionSummary) { S $fn.preConditionSummary } else { S (@($fn.preconditions) -join "; ") }

        # Populate Functions row (align left for text columns so wide strings don't overflow left)
        $wsFuncs.Cells.Item([int]$rowFuncs, 1).Value2 = [int]($fIdx + 1)
        $wsFuncs.Cells.Item([int]$rowFuncs, 2).HorizontalAlignment = -4131
        $wsFuncs.Cells.Item([int]$rowFuncs, 2).Value2 = $reqName
        $wsFuncs.Cells.Item([int]$rowFuncs, 3).HorizontalAlignment = -4131
        $wsFuncs.Cells.Item([int]$rowFuncs, 3).Value2 = $className
        $wsFuncs.Cells.Item([int]$rowFuncs, 4).HorizontalAlignment = -4131
        $wsFuncs.Cells.Item([int]$rowFuncs, 4).Value2 = $funcName
        $wsFuncs.Cells.Item([int]$rowFuncs, 5).HorizontalAlignment = -4131
        $wsFuncs.Cells.Item([int]$rowFuncs, 5).Value2 = $funcCode
        $wsFuncs.Cells.Item([int]$rowFuncs, 6).HorizontalAlignment = -4131
        $wsFuncs.Cells.Item([int]$rowFuncs, 6).Value2 = $sheetName
        $wsFuncs.Cells.Item([int]$rowFuncs, 7).HorizontalAlignment = -4131
        $wsFuncs.Cells.Item([int]$rowFuncs, 7).Value2 = $desc
        $wsFuncs.Cells.Item([int]$rowFuncs, 8).HorizontalAlignment = -4131
        $wsFuncs.Cells.Item([int]$rowFuncs, 8).Value2 = $preCondText

        # Copy template sheet before Example
        $wsExample = $wb.Worksheets.Item("Example")
        $wsTemplate.Copy($wsExample)
        $wsFn = $wb.Worksheets.Item([int]($wsExample.Index - 1))

        # Avoid duplicate name collision with existing placeholder sheets
        foreach ($existing in $wb.Worksheets) {
            if ($existing.Name -eq $sheetName -and $existing.Index -ne $wsFn.Index) {
                $existing.Name = (S "_old_$($existing.Name)")
            }
        }
        $wsFn.Name = $sheetName
        $createdSheets += $sheetName

        # Ensure C2 and L2 have General/Standard format (copied from C3) before setting formulas
        $wsFn.Cells.Item(2, 3).NumberFormat = $wsFn.Cells.Item(3, 3).NumberFormat
        $wsFn.Cells.Item(2, 3).Formula = (S "=Functions!E$rowFuncs")
        $wsFn.Cells.Item(2, 12).NumberFormat = $wsFn.Cells.Item(3, 3).NumberFormat
        $wsFn.Cells.Item(2, 12).Formula = (S "=Functions!D$rowFuncs")

        $creatorStr = if ($fn.createdBy) { S $fn.createdBy } elseif ($payload.creator) { S $payload.creator } else { S "Developer" }
        $executorStr = if ($fn.executedBy) { S $fn.executedBy } elseif ($payload.creator) { S $payload.creator } else { S "Developer" }
        $locVal = if ($fn.loc) { S $fn.loc } else { S "50" }
        $wsFn.Cells.Item(3, 3).Value2 = $creatorStr
        $wsFn.Cells.Item(3, 12).HorizontalAlignment = -4131
        $wsFn.Cells.Item(3, 12).Value2 = $executorStr
        $wsFn.Cells.Item(4, 3).Formula = $locVal
        $wsFn.Cells.Item(5, 3).Value2 = if ($fn.testRequirement) { S $fn.testRequirement } else { $desc }

        # Unmerge D19:E19 in template if merged
        if ($wsFn.Cells.Item(19, 4).MergeCells) {
            $wsFn.Cells.Item(19, 4).MergeArea.UnMerge() | Out-Null
        }

        # Calculate required rows in Condition (starts at R10) and Confirm
        $preconds = @($fn.preconditions)
        $inputs = @($fn.inputs)
        [int]$condRowsNeeded = 1 + [Math]::Max($preconds.Count, 1) # Header 'Precondition' + items
        foreach ($inp in $inputs) {
            $vals = @($inp.values)
            $condRowsNeeded += (1 + $vals.Count)
        }

        $returns = @($fn.returns)
        $exceptions = @($fn.exceptions)
        $logs = @($fn.logs)
        [int]$confRowsNeeded = (1 + $returns.Count) + (1 + $exceptions.Count) + (1 + $logs.Count)

        # Template has Condition rows 10..30 (21 rows) and Confirm rows 31..38 (8 rows)
        [int]$templateCondCapacity = 21
        if ($condRowsNeeded -gt $templateCondCapacity) {
            [int]$extraCond = $condRowsNeeded - $templateCondCapacity
            for ([int]$i = 0; $i -lt $extraCond; $i++) {
                $wsFn.Rows.Item(29).Insert() | Out-Null
            }
        } else {
            [int]$extraCond = 0
        }

        [int]$confirmStartRow = 31 + $extraCond
        [int]$templateConfCapacity = 8
        if ($confRowsNeeded -gt $templateConfCapacity) {
            [int]$extraConf = $confRowsNeeded - $templateConfCapacity
            for ([int]$i = 0; $i -lt $extraConf; $i++) {
                $wsFn.Rows.Item([int]($confirmStartRow + 6)).Insert() | Out-Null
            }
        } else {
            [int]$extraConf = 0
        }

        [int]$resultStartRow = 39 + $extraCond + $extraConf

        # Clear Condition area (rows 10 .. confirmStartRow-1, cols 2..25)
        for ([int]$r = 10; $r -lt $confirmStartRow; $r++) {
            for ([int]$c = 2; $c -le 25; $c++) {
                $wsFn.Cells.Item([int]$r, [int]$c).Value2 = $null
            }
        }

        # Clear Confirm area (rows confirmStartRow .. resultStartRow-1, cols 2..25)
        for ([int]$r = $confirmStartRow; $r -lt $resultStartRow; $r++) {
            for ([int]$c = 2; $c -le 25; $c++) {
                $wsFn.Cells.Item([int]$r, [int]$c).Value2 = $null
            }
        }

        # Clear UTCID header row 9 and Result rows (resultStartRow .. resultStartRow+3)
        for ([int]$c = 6; $c -le 25; $c++) {
            $wsFn.Cells.Item(9, [int]$c).Value2 = $null
            for ([int]$r = $resultStartRow; $r -le ($resultStartRow + 3); $r++) {
                $wsFn.Cells.Item([int]$r, [int]$c).Value2 = $null
            }
        }

        # Ensure summary formulas in Row 7 point to the exact dynamic result rows
        [int]$typeRow = $resultStartRow
        [int]$pfRow = $resultStartRow + 1
        [int]$dateRow = $resultStartRow + 2
        $wsFn.Cells.Item(7, 1).Formula = (S "=COUNTIF(F${pfRow}:HQ${pfRow},`"P`")")
        $wsFn.Cells.Item(7, 3).Formula = (S "=COUNTIF(F${pfRow}:HQ${pfRow},`"F`")")
        $wsFn.Cells.Item(7, 6).Formula = (S "=SUM(O7,-A7,-C7)")
        $wsFn.Cells.Item(7, 12).Formula = (S "=COUNTIF(E${typeRow}:HQ${typeRow},`"N`")")
        $wsFn.Cells.Item(7, 13).Formula = (S "=COUNTIF(E${typeRow}:HQ${typeRow},`"A`")")
        $wsFn.Cells.Item(7, 14).Formula = (S "=COUNTIF(E${typeRow}:HQ${typeRow},`"B`")")
        $wsFn.Cells.Item(7, 15).Formula = (S "=COUNTA(E9:HT9)")

        # Write Condition rows and record row index map for "O" markers
        $precondRowMap = @{}
        $inputRowMap = @{}

        [int]$curRow = 10
        $wsFn.Cells.Item([int]$curRow, 2).HorizontalAlignment = -4131
        $wsFn.Cells.Item([int]$curRow, 2).Value2 = (S "Precondition ")
        $curRow++
        foreach ($p in $preconds) {
            $pStr = S $p
            $wsFn.Cells.Item([int]$curRow, 4).HorizontalAlignment = -4131
            $wsFn.Cells.Item([int]$curRow, 4).Value2 = $pStr
            $precondRowMap[$pStr] = [int]$curRow
            $curRow++
        }

        foreach ($inp in $inputs) {
            $inpName = S $inp.name
            $wsFn.Cells.Item([int]$curRow, 2).HorizontalAlignment = -4131
            $wsFn.Cells.Item([int]$curRow, 2).Value2 = $inpName
            $curRow++
            $inputRowMap[$inpName] = @{}
            foreach ($val in @($inp.values)) {
                $valStr = S $val
                $wsFn.Cells.Item([int]$curRow, 4).HorizontalAlignment = -4131
                if ($valStr -match '^[=+\-@]') {
                    $wsFn.Cells.Item([int]$curRow, 4).Value2 = (S ("'" + $valStr))
                } else {
                    $wsFn.Cells.Item([int]$curRow, 4).Value2 = $valStr
                }
                $inputRowMap[$inpName][$valStr] = [int]$curRow
                $curRow++
            }
        }

        # Write Confirm rows and record row index map for "O" markers
        $returnRowMap = @{}
        $excRowMap = @{}
        $logRowMap = @{}

        [int]$curRow = $confirmStartRow
        $wsFn.Cells.Item([int]$curRow, 2).HorizontalAlignment = -4131
        $wsFn.Cells.Item([int]$curRow, 2).Value2 = (S "Return")
        $curRow++
        foreach ($ret in $returns) {
            $retStr = S $ret
            $wsFn.Cells.Item([int]$curRow, 4).HorizontalAlignment = -4131
            $wsFn.Cells.Item([int]$curRow, 4).Value2 = $retStr
            $returnRowMap[$retStr] = [int]$curRow
            $curRow++
        }

        $wsFn.Cells.Item([int]$curRow, 2).HorizontalAlignment = -4131
        $wsFn.Cells.Item([int]$curRow, 2).Value2 = (S "Exception")
        $curRow++
        foreach ($ex in $exceptions) {
            $exStr = S $ex
            $wsFn.Cells.Item([int]$curRow, 4).HorizontalAlignment = -4131
            $wsFn.Cells.Item([int]$curRow, 4).Value2 = $exStr
            $excRowMap[$exStr] = [int]$curRow
            $curRow++
        }

        $wsFn.Cells.Item([int]$curRow, 2).HorizontalAlignment = -4131
        $wsFn.Cells.Item([int]$curRow, 2).Value2 = (S "Log message")
        $curRow++
        foreach ($lg in $logs) {
            $lgStr = S $lg
            $wsFn.Cells.Item([int]$curRow, 4).HorizontalAlignment = -4131
            $wsFn.Cells.Item([int]$curRow, 4).Value2 = $lgStr
            $logRowMap[$lgStr] = [int]$curRow
            $curRow++
        }

        # Populate Test Cases (UTCID01..N starting at col 6 = F)
        $tcs = @($fn.testCases)
        for ([int]$tIdx = 0; $tIdx -lt $tcs.Count; $tIdx++) {
            $tc = $tcs[$tIdx]
            [int]$col = 6 + $tIdx
            $tcId = if ($tc.id) { S $tc.id } else { S ("UTCID{0:D2}" -f ($tIdx + 1)) }
            $wsFn.Cells.Item(9, [int]$col).Value2 = $tcId

            # Mark Preconditions
            foreach ($p in @($tc.preconditions)) {
                $pStr = S $p
                if ($precondRowMap.ContainsKey($pStr)) {
                    $wsFn.Cells.Item([int]$precondRowMap[$pStr], [int]$col).Value2 = (S "O")
                }
            }

            # Mark Inputs
            if ($tc.inputs) {
                foreach ($prop in $tc.inputs.PSObject.Properties) {
                    $k = S $prop.Name
                    $v = S $prop.Value
                    if ($inputRowMap.ContainsKey($k) -and $inputRowMap[$k].ContainsKey($v)) {
                        $wsFn.Cells.Item([int]$inputRowMap[$k][$v], [int]$col).Value2 = (S "O")
                    }
                }
            }

            # Mark Returns
            foreach ($rVal in @($tc.returns)) {
                $rStr = S $rVal
                if ($returnRowMap.ContainsKey($rStr)) {
                    $wsFn.Cells.Item([int]$returnRowMap[$rStr], [int]$col).Value2 = (S "O")
                }
            }

            # Mark Exceptions
            foreach ($eVal in @($tc.exceptions)) {
                $eStr = S $eVal
                if ($excRowMap.ContainsKey($eStr)) {
                    $wsFn.Cells.Item([int]$excRowMap[$eStr], [int]$col).Value2 = (S "O")
                }
            }

            # Mark Logs
            foreach ($lVal in @($tc.logs)) {
                $lStr = S $lVal
                if ($logRowMap.ContainsKey($lStr)) {
                    $wsFn.Cells.Item([int]$logRowMap[$lStr], [int]$col).Value2 = (S "O")
                }
            }

            # Result rows
            $wsFn.Cells.Item([int]$typeRow, [int]$col).Value2 = (S $tc.type)
            $wsFn.Cells.Item([int]$pfRow, [int]$col).Value2 = (S $tc.status)
            $execDate = if ($tc.executedDate) { S $tc.executedDate } else { S (Get-Date).ToString("MM/dd") }
            $wsFn.Cells.Item([int]$dateRow, [int]$col).NumberFormat = "@"
            $wsFn.Cells.Item([int]$dateRow, [int]$col).HorizontalAlignment = -4108
            $wsFn.Cells.Item([int]$dateRow, [int]$col).Value2 = $execDate
        }

        # Populate Statistics row for this function
        $escapedSheet = "'" + $sheetName.Replace("'", "''") + "'"
        $wsStats.Cells.Item([int]$rowStats, 1).Value2 = [int]($fIdx + 1)
        $wsStats.Cells.Item([int]$rowStats, 2).HorizontalAlignment = -4131
        $wsStats.Cells.Item([int]$rowStats, 2).Value2 = $funcCode
        $wsStats.Cells.Item([int]$rowStats, 3).Formula = (S "=${escapedSheet}!A7")
        $wsStats.Cells.Item([int]$rowStats, 4).Formula = (S "=${escapedSheet}!C7")
        $wsStats.Cells.Item([int]$rowStats, 5).Formula = (S "=${escapedSheet}!F7")
        $wsStats.Cells.Item([int]$rowStats, 6).Formula = (S "=${escapedSheet}!L7")
        $wsStats.Cells.Item([int]$rowStats, 7).Formula = (S "=${escapedSheet}!M7")
        $wsStats.Cells.Item([int]$rowStats, 8).Formula = (S "=${escapedSheet}!N7")
        $wsStats.Cells.Item([int]$rowStats, 9).Formula = (S "=${escapedSheet}!O7")
    }

    # Remove old placeholder function sheets ('Function 1', 'Function 2', 'Function3', or '_old_*')
    $toDelete = @()
    foreach ($ws in $wb.Worksheets) {
        $n = $ws.Name
        if ($n -like "_old_*" -or (($n -in @("Function 1", "Function 2", "Function3")) -and ($n -notin $createdSheets))) {
            $toDelete += $ws
        }
    }
    foreach ($ws in $toDelete) {
        $ws.Delete()
    }

    # Save first as .xlsx (FileFormat = 51) so Excel upgrades the 65536-row BIFF8 grid to 1048576-row OpenXML
    # and then recalculate all formulas cleanly!
    $ext = [System.IO.Path]::GetExtension($OutputPath).ToLower()
    $fileFormat = if ($ext -eq ".xls") { 56 } else { 51 }
    $wb.SaveAs($OutputPath, $fileFormat)
    $excel.CalculateFullRebuild()
    $wb.Save()
    $wb.Close($false)
    Write-Output "SUCCESS: Saved unit test report to $OutputPath"
}
finally {
    $excel.Quit()
    [System.Runtime.Interopservices.Marshal]::ReleaseComObject($excel) | Out-Null
}
"""


def generate_report(payload_path: str, template_path: str, output_path: str | None) -> str:
    with open(payload_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    validate_payload(data)

    if not os.path.exists(template_path):
        raise FileNotFoundError(f"Template Excel file not found: {template_path}")

    os.makedirs(DEFAULT_OUTPUT_DIR, exist_ok=True)

    if not output_path:
        feature_slug = data.get("featureSlug", "feature").strip().replace(" ", "-")
        timestamp = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
        output_filename = f"Unit_Test_{feature_slug}_{timestamp}.xlsx"
        output_path = os.path.join(DEFAULT_OUTPUT_DIR, output_filename)
    else:
        output_path = os.path.abspath(output_path)
        os.makedirs(os.path.dirname(output_path), exist_ok=True)

    if os.path.exists(output_path):
        os.remove(output_path)

    with tempfile.NamedTemporaryFile(mode="w", suffix=".ps1", delete=False, encoding="utf-8") as ps_file:
        ps_file.write(POWERSHELL_SCRIPT)
        ps_script_path = ps_file.name

    try:
        ps_exe = shutil.which("pwsh") or shutil.which("powershell") or "powershell"
        cmd = [
            ps_exe,
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            ps_script_path,
            "-TemplatePath",
            os.path.abspath(template_path),
            "-OutputPath",
            os.path.abspath(output_path),
            "-PayloadPath",
            os.path.abspath(payload_path),
        ]
        res = subprocess.run(cmd, capture_output=True, text=True, check=False)
        if res.returncode != 0:
            raise RuntimeError(
                f"Excel generation failed (exit {res.returncode}):\nSTDOUT:\n{res.stdout}\nSTDERR:\n{res.stderr}"
            )
        print(res.stdout.strip())
        return output_path
    finally:
        if os.path.exists(ps_script_path):
            os.remove(ps_script_path)


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate SEP490 Unit Test Report (.xlsx) from template.")
    parser.add_argument("--input", "-i", required=True, help="Path to JSON payload describing tested functions and test cases.")
    parser.add_argument("--template", "-t", default=DEFAULT_TEMPLATE, help="Path to Unit Test.xls template.")
    parser.add_argument("--output", "-o", default=None, help="Optional explicit output .xlsx path. Defaults to a new timestamped copy in antigravity-output.")
    args = parser.parse_args()

    generate_report(args.input, args.template, args.output)


if __name__ == "__main__":
    main()
