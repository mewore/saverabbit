import java.io.FileOutputStream
import java.io.OutputStream

plugins {
    java
}

group = "moe.mewore"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val lombokArtifact = "org.projectlombok:lombok:1.18.24"

dependencies {
    annotationProcessor(lombokArtifact)
    compileOnly(lombokArtifact)
    compileOnly("org.checkerframework:checker-qual:3.25.0")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.0")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

val editorClass = "moe.mewore.saverabbit.SaveRabbit"

tasks.jar {
    manifest {
        attributes["Main-Class"] = editorClass
    }

    duplicatesStrategy = DuplicatesStrategy.WARN

    doFirst("Include dependency jars in the jar (make a fat jar)") {
        from(configurations.runtimeClasspath.get().files.filter { it.isFile && it.name.endsWith(".jar") }.map {
            zipTree(it).matching { exclude("about.html", "META-INF/**") }
        })
    }
}

fun getEnvVar(varName: String) = System.getenv()[varName]
        ?: throw kotlin.Error("The environment variable '$varName' has not been set!")
fun getLinuxJavaPath() = System.getenv()["LINUX_JAVA_PATH"] ?: "${getEnvVar("HOME")}/.jdks/jdk-lin64"
fun getWindowsJavaPath() = System.getenv()["WINDOWS_JAVA_PATH"] ?: "${getEnvVar("HOME")}/.jdks/jdk-win64"
fun getLaunch4JPath() = System.getenv()["LAUNCH4J_PATH"] ?: "${getEnvVar("HOME")}/.jdks/launch4j"

val determineJarDependencies = tasks.create("determineJarDependencies") {
    setDependsOn(listOf(tasks.jar))
    val jarFile = tasks.jar.get().outputs.files.filter { it.extension == "jar" }.singleFile
    inputs.files(jarFile)
    val outputDir = projectDir.resolve("build/jar-dependencies")
    val outputFile = outputDir.resolve("dependencies.txt")
    outputs.files(outputFile)

    doLast("Get .jar file dependencies") {
        exec {
            setCommandLine("${getLinuxJavaPath()}/bin/jdeps", jarFile.absolutePath)
            standardOutput = JarDependencyFilteringStream(FileOutputStream(outputFile))
        }
    }
}

class JarDependencyFilteringStream(private val targetStream: OutputStream) : OutputStream() {
    private val writtenDependencies = HashSet<String>()
    private val buffer = StringBuilder()
    private var hasPrevious = false

    override fun write(b: Int) {
        val c = b.toChar()
        if (c == '\n') {
            val dependency = buffer.toString()
            buffer.clear()
            if (!writtenDependencies.contains(dependency) && dependency.startsWith("java.")) {
                if (hasPrevious) {
                    targetStream.write(','.toInt())
                }
                targetStream.write(dependency.toByteArray())
                writtenDependencies.add(dependency)
                hasPrevious = true
            }
        } else if (c == ' ' || c == '\t') {
            buffer.clear()
        } else {
            buffer.append(c)
        }
    }
}

val createLinuxJre = tasks.create("createLinuxJre") {
    setDependsOn(listOf(determineJarDependencies))
    val dependencyFile = determineJarDependencies.outputs.files.singleFile
    inputs.files(tasks.jar.get().outputs.files.plus(dependencyFile))
    val outputDir = projectDir.resolve("build/jre/linux")
    outputs.dir(outputDir)

    doLast("Remove the Linux JRE") {
        if (outputDir.exists()) {
            outputDir.deleteRecursively()
        }
    }
    doLast("Create the Linux JRE") {
        exec {
            setCommandLine(
                    "${getLinuxJavaPath()}/bin/jlink",
                    "--add-modules",
                    dependencyFile.readText(),
                    "--strip-debug",
                    "--no-man-pages",
                    "--no-header-files",
                    "--output",
                    outputDir
            )
        }
    }
}

val createWindowsJre = tasks.create("createWindowsJre") {
    setDependsOn(listOf(determineJarDependencies))
    val dependencyFile = determineJarDependencies.outputs.files.singleFile
    inputs.files(tasks.jar.get().outputs.files.plus(dependencyFile))
    val outputDir = projectDir.resolve("build/jre/windows")
    outputs.dir(outputDir)

    doLast("Remove the Windows JRE") {
        if (outputDir.exists()) {
            outputDir.deleteRecursively()
        }
    }
    doLast("Create the Windows JRE") {
        exec {
            setCommandLine(
                    "wine",
                    "${getWindowsJavaPath()}/bin/jlink.exe",
                    "--add-modules",
                    dependencyFile.readText(),
                    "--strip-debug",
                    "--no-man-pages",
                    "--no-header-files",
                    "--output",
                    outputDir
            )
        }
    }
}

val createWindowsExecutable = tasks.create("createWindowsExecutable") {
    setDependsOn(listOf(tasks.jar))
    val configFileName = "launch4j-config.xml"
    val sourceConfigFile = projectDir.resolve(configFileName)
    val jarFile = tasks.jar.get().outputs.files.singleFile
    inputs.files(jarFile, sourceConfigFile)
    val outputDir = projectDir.resolve("build/windows-executable")
    val exeFile = outputDir.resolve("${jarFile.nameWithoutExtension}.exe")
    val configFile = outputDir.resolve(configFileName)
    outputs.files(exeFile, configFile)

    doLast("Prepare the Launch4J config file") {
        copy {
            from(sourceConfigFile)
            filter {
                it.replace("<jar/>", "<jar>$jarFile</jar>").replace("<outfile/>", "<outfile>$exeFile</outfile>")
            }
            into(outputDir)
        }
    }
    doLast("Use Launch4J to turn a .jar into an .exe") {
        exec {
            println("createWindowsExecutable :: '${getLaunch4JPath()}/launch4jc' '$configFile'")
            setCommandLine("${getLaunch4JPath()}/launch4jc", configFile)
        }
    }
}

val prepareWindowsExecutable = tasks.create("prepareWindowsExecutable") {
    setDependsOn(listOf(createWindowsExecutable, createWindowsJre))
    val exeFile = createWindowsExecutable.outputs.files.filter { it.extension == "exe" }.singleFile
    val jreDir = createWindowsJre.outputs.files.filter { it.extension == "" }.single()
    inputs.dir(jreDir)
    inputs.files(exeFile)
    val outputDir = projectDir.resolve("build/executable/windows")
    val archiveRootDir = outputDir.resolve(System.getenv()["WINDOWS_ROOT_DIR"] ?: "${project.name} x64")
    outputs.dir(archiveRootDir)

    doLast("Create the directory") {
        if (!archiveRootDir.exists() && !archiveRootDir.mkdirs()) {
            throw kotlin.Error("Failed to create ${archiveRootDir.absolutePath}")
        }
    }
    doLast("Copy the .exe file") {
        copy {
            from(exeFile)
            into(archiveRootDir)
        }
    }
    doLast("Copy the JRE directory") {
        copy {
            from(jreDir)
            include { it.file.canRead() && it.file.canWrite() }
            into(archiveRootDir.resolve("jre"))
        }
    }
}

val windowsExecutableZip = tasks.create<Zip>("windowsExecutableZip") {
    setDependsOn(listOf(prepareWindowsExecutable))
    val dirToCompress = prepareWindowsExecutable.outputs.files.filter { it.extension == "" }.single()
    inputs.dir(dirToCompress)
    val destinationDir = projectDir.resolve("build/executable/windows")
    val archiveName = "${System.getenv()["WINDOWS_ARCHIVE_NAME"] ?: dirToCompress.name}.zip"
    outputs.files(destinationDir.resolve(archiveName))

    from(dirToCompress.parentFile)
    include("${dirToCompress.name}/**")
    destinationDirectory.set(destinationDir)
    archiveFileName.set(archiveName)
}

val prepareLinuxExecutable = tasks.create("prepareLinuxExecutable") {
    val jarTask = tasks.jar.get()
    setDependsOn(listOf(createLinuxJre, jarTask))
    val jarFile = jarTask.outputs.files.filter { it.extension == "jar" }.singleFile
    val jreDir = createLinuxJre.outputs.files.filter { it.extension == "" }.single()
    inputs.dir(jreDir)
    inputs.files(jarFile)
    val outputDir = projectDir.resolve("build/executable/linux")
    val archiveRootDir = outputDir.resolve(System.getenv()["LINUX_ROOT_DIR"] ?: "${project.name}-lin64")
    outputs.dir(archiveRootDir)

    doLast("Create the directory") {
        if (!archiveRootDir.exists() && !archiveRootDir.mkdirs()) {
            throw kotlin.Error("Failed to create ${archiveRootDir.absolutePath}")
        }
    }
    doLast("Copy the .jar file") {
        copy {
            from(jarFile)
            into(archiveRootDir)
        }
    }
    doLast("Copy the JRE directory") {
        copy {
            from(jreDir)
            include { it.file.canRead() && it.file.canWrite() }
            into(archiveRootDir.resolve("jre"))
        }
    }
    doLast("Create a .sh script that runs the JRE") {
        val scriptFile = archiveRootDir.resolve(jarFile.nameWithoutExtension + ".sh")
        scriptFile.writeText("./jre/bin/java -jar ./${jarFile.name}")
        scriptFile.setExecutable(true)
    }
}

val linuxExecutableTar = tasks.create<Tar>("linuxExecutableTar") {
    setDependsOn(listOf(prepareLinuxExecutable))
    val dirToCompress = prepareLinuxExecutable.outputs.files.filter { it.extension == "" }.single()
    inputs.dir(dirToCompress)
    val destinationDir = projectDir.resolve("build/executable/linux")
    val archiveName = "${System.getenv()["LINUX_ARCHIVE_NAME"] ?: dirToCompress.name}.tar.gz"
    outputs.files(destinationDir.resolve(archiveName))

    from(dirToCompress.parentFile)
    include("${dirToCompress.name}/**")
    destinationDirectory.set(destinationDir)
    archiveFileName.set(archiveName)
}

val packageAll = tasks.create("packageAll") {
    setDependsOn(listOf(windowsExecutableZip, linuxExecutableTar))
}
