# Runs the pure Kotlin alert tests without an Android SDK. Reuses a Gradle distribution's JVM jars.
param([string]$GradleLib)
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
if (!$GradleLib) {
    $gradleCache = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
    $compiler = Get-ChildItem (Join-Path $gradleCache 'wrapper/dists') -Recurse -Filter 'kotlin-compiler-embeddable-*.jar' |
        Where-Object { $_.Directory.Name -eq 'lib' } | Select-Object -First 1
    if (!$compiler) { throw 'Pass -GradleLib with the lib directory of an extracted Gradle distribution.' }
    $GradleLib = $compiler.Directory.FullName
}
$stdlib = (Get-ChildItem $GradleLib -Filter 'kotlin-stdlib-*.jar' | Select-Object -First 1).FullName
$junit = Join-Path $GradleLib 'junit-4.13.2.jar'
$hamcrest = Join-Path $GradleLib 'hamcrest-core-1.3.jar'
$outputDir = Join-Path $project 'app/build/alert-jvm'
New-Item -ItemType Directory -Force $outputDir | Out-Null
$output = Join-Path $outputDir 'tests.jar'
$sources = @(
    (Join-Path $project 'app/src/main/java/com/aware/app/classify/Label.kt'),
    (Join-Path $project 'app/src/main/java/com/aware/app/classify/SoundClassifier.kt')
)
# Only the pure-Kotlin files; AlertMessages/AlertReactor need Android.
$main = Join-Path $project 'app/src/main/java/com/aware/app'
$sources += @('alert/Category.kt', 'alert/CategoryMapper.kt', 'alert/AlertEngine.kt', 'haptics/HapticPatterns.kt', 'speech/NameDetector.kt',
    'llm/SituationContext.kt', 'llm/SituationSummarizer.kt', 'llm/TemplateSummarizer.kt', 'llm/OutputSanitizer.kt', 'llm/SituationTrigger.kt') |
    ForEach-Object { Join-Path $main $_ }
$sources += @('demo/DemoScenario.kt', 'demo/DemoClassifier.kt') | ForEach-Object { Join-Path $main $_ }
$sources += (Get-ChildItem (Join-Path $project 'app/src/test/java/com/aware/app') -Recurse -Filter '*.kt').FullName
& java -cp "$GradleLib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -jvm-target 17 -no-stdlib -no-reflect -classpath "$stdlib;$junit;$hamcrest" -d $output @sources
if ($LASTEXITCODE -ne 0) { throw 'Kotlin compilation failed.' }
$model = Join-Path $project 'app/src/main/assets/yamnet.tflite'
& java "-Daware.yamnet=$model" -cp "$output;$stdlib;$junit;$hamcrest" org.junit.runner.JUnitCore com.aware.app.alert.CategoryMapperTest com.aware.app.alert.AlertEngineTest com.aware.app.haptics.HapticPatternsTest com.aware.app.speech.NameDetectorTest com.aware.app.llm.SituationContextTest com.aware.app.llm.TemplateSummarizerTest com.aware.app.llm.OutputSanitizerTest com.aware.app.llm.SituationTriggerTest com.aware.app.demo.DemoScenarioTest
if ($LASTEXITCODE -ne 0) { throw 'Alert tests failed.' }
