# Run JVM unit tests without Gradle.
#
# Requires kotlinc in PATH (or KOTLIN_HOME).
# JUnit and Hamcrest are downloaded once into build/test-libs.
#
#   powershell -ExecutionPolicy Bypass -File run_unit_tests.ps1

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot

$kotlincCmd = Get-Command kotlinc.bat -ErrorAction SilentlyContinue
if (-not $kotlincCmd) { $kotlincCmd = Get-Command kotlinc -ErrorAction SilentlyContinue }
if (-not $kotlincCmd) {
    throw "kotlinc not found. Add Kotlin bin to PATH or set KOTLIN_HOME."
}
$kotlinc = $kotlincCmd.Source
$kotlinHome = Split-Path -Parent (Split-Path -Parent $kotlinc)
$stdlib = Join-Path $kotlinHome "lib\kotlin-stdlib.jar"

$libs = Join-Path $root "build\test-libs"
New-Item -ItemType Directory -Force $libs | Out-Null

$junit = Join-Path $libs "junit-4.13.2.jar"
$hamcrest = Join-Path $libs "hamcrest-core-1.3.jar"

if (-not (Test-Path $junit)) {
    Write-Host "Downloading JUnit..."
    Invoke-WebRequest -UseBasicParsing -Uri "https://repo1.maven.org/maven2/junit/junit/4.13.2/junit-4.13.2.jar" -OutFile $junit
}
if (-not (Test-Path $hamcrest)) {
    Write-Host "Downloading Hamcrest..."
    Invoke-WebRequest -UseBasicParsing -Uri "https://repo1.maven.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar" -OutFile $hamcrest
}

$out = Join-Path $root "build\unit-test-classes"
Remove-Item $out -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $out | Out-Null

$mainFiles = @(
    (Join-Path $root "Models.kt"),
    (Join-Path $root "WordTimestamps.kt"),
    (Join-Path $root "AudioSlicer.kt"),
    (Join-Path $root "AudioChunker.kt")
)
$testFiles = Get-ChildItem (Join-Path $root "src\test\kotlin") -Recurse -Filter *.kt |
    ForEach-Object { $_.FullName }

$sources = $mainFiles + $testFiles
& $kotlinc -cp $junit $sources -d $out
if ($LASTEXITCODE -ne 0) { throw "Test compilation failed" }

$testClasses = @(
    "com.nuclearhack.transcription.WordTimestampsTest",
    "com.nuclearhack.transcription.AudioSlicerTest",
    "com.nuclearhack.transcription.AudioChunkerTest"
)

$javaCmd = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaCmd) { throw "java not found in PATH" }

& $javaCmd.Source -cp "$out;$junit;$hamcrest;$stdlib" org.junit.runner.JUnitCore @testClasses
if ($LASTEXITCODE -ne 0) { throw "Tests failed" }
