# Run JVM unit tests for diarization without Gradle.
#
# Чистые классы (модели, назначение слов, склейка реплик, auto-K по порогам)
# не зависят от Android и sherpa-onnx, поэтому компилируются и тестируются
# обычным kotlinc.
#
# Requires kotlinc in PATH (or KOTLIN_HOME); JUnit/Hamcrest download once.
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

$transcriptionDir = Join-Path $root "..\..\..\transcription\kotlin\transcribers"

$mainFiles = @(
    (Join-Path $root "DiarizationModels.kt"),
    (Join-Path $root "Diarizer.kt"),
    (Join-Path $root "WordSpeakerAssigner.kt"),
    (Join-Path $root "UtteranceBuilder.kt"),
    (Join-Path $root "SpeakerCountSweep.kt"),
    (Join-Path $transcriptionDir "Models.kt"),
    (Join-Path $transcriptionDir "AudioSlicer.kt")
)
$testFiles = Get-ChildItem (Join-Path $root "src\test\kotlin") -Recurse -Filter *.kt |
    ForEach-Object { $_.FullName }

$sources = $mainFiles + $testFiles
& $kotlinc -cp $junit $sources -d $out
if ($LASTEXITCODE -ne 0) { throw "Test compilation failed" }

$testClasses = @(
    "com.nuclearhack.diarization.SpeakerCountSweepTest",
    "com.nuclearhack.diarization.WordSpeakerAssignerTest",
    "com.nuclearhack.diarization.UtteranceBuilderTest",
    "com.nuclearhack.diarization.DiarizationModelsTest"
)

$javaCmd = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaCmd) { throw "java not found in PATH" }

& $javaCmd.Source -cp "$out;$junit;$hamcrest;$stdlib" org.junit.runner.JUnitCore @testClasses
if ($LASTEXITCODE -ne 0) { throw "Tests failed" }
