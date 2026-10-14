param(
    [Parameter(Mandatory = $true)][string]$DeviceSerial,
    [Parameter(Mandatory = $true)][string]$OutputPath,
    [string]$AdbPath = 'adb'
)

# Read DEVICE_SAFETY.md and verify the device before invoking this host-only capture.
# Screens may contain private data. Keep the output outside version control.
$captureInfo = New-Object System.Diagnostics.ProcessStartInfo
$captureInfo.FileName = (Get-Command $AdbPath -ErrorAction Stop).Source
$captureInfo.ArgumentList.Add('-s')
$captureInfo.ArgumentList.Add($DeviceSerial)
$captureInfo.ArgumentList.Add('exec-out')
$captureInfo.ArgumentList.Add('screencap')
$captureInfo.ArgumentList.Add('-p')
$captureInfo.UseShellExecute = $false
$captureInfo.CreateNoWindow = $true
$captureInfo.RedirectStandardOutput = $true
$captureInfo.RedirectStandardError = $true
$captureProcess = [System.Diagnostics.Process]::Start($captureInfo)
$imageFile = [System.IO.File]::Create($OutputPath)
try {
    $captureProcess.StandardOutput.BaseStream.CopyTo($imageFile)
} finally {
    $imageFile.Dispose()
}
$captureError = $captureProcess.StandardError.ReadToEnd()
$captureProcess.WaitForExit()
try {
    if ($captureProcess.ExitCode -ne 0) { throw $captureError }
} finally {
    $captureProcess.Dispose()
}
