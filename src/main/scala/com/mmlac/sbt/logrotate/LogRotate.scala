package com.mmlac.sbt.logrotate

import com.typesafe.sbt.packager.archetypes.TemplateWriter
import com.typesafe.sbt.packager.linux.LinuxMappingDSL
import sbt._
import com.typesafe.sbt.packager.Keys.{packageName}
import com.typesafe.sbt.packager.linux
import linux.LinuxPlugin
import linux.LinuxPlugin.autoImport.{
  Linux,
  packageArchitecture,
  linuxScriptReplacements,
  linuxPackageMappings,
  linuxPackageSymlinks,
  defaultLinuxLogsLocation,
  serverLoading,
  daemonShell
}
import sbt.Keys.{ name, normalizedName, target, mappings, sourceDirectory }


object LogRotate extends AutoPlugin with LinuxMappingDSL {

  override def requires = LinuxPlugin
  override def trigger  = allRequirements
  override lazy val projectSettings = defaultSettings

  object autoImport {
    val logrotateScriptFile   = SettingKey[File](
      "logrotate-script-directory",
      "File that is storing the logrotate config for this application, " +
        "by default 'src/linux/logrotate.conf'")

    val logrotateReplacements = SettingKey[Seq[(String, String)]](
      "logrotate-replacements",
      "Stores all replacements that can be made in logrotate file." +
        "Default is logdir -> $defaultLinuxLogLocation / $packageName")
  }

  import autoImport._

  val logrotateParseConfig     =  TaskKey[File]("logrotate-parse-config", "Parses the user submitted or default config file and writes it to a temp directory")

  def defaultSettings : Seq[Setting[_]] = Seq (
    logrotateReplacements := Seq( ("logdir", defaultLinuxLogsLocation.value + "/" + (packageName in Linux).value) ),
    logrotateScriptFile <<= (sourceDirectory in Linux) ( _ / "logrotate.conf"),

    logrotateParseConfig <<= (logrotateScriptFile, logrotateReplacements, target in Compile) map { (file, replace, target) =>
      file.exists match {
        case true  => parseTemplate(IO.readLines(file), replace, target)
        // getClass getPackage is null. :|
        case false => parseTemplate(IO.readLinesURL(getClass getResource "logrotate.conf"), replace, target)

      }
    },

    linuxPackageMappings <+= (logrotateParseConfig, normalizedName) map { (configFile, normalizedName) =>
      packageMapping((configFile, s"/etc/logrotate.d/$normalizedName"))
    }
  )

  private final def parseTemplate(file : Seq[String], replacements : Seq[(String, String)], target : File) : File = {
    val template = TemplateWriter.generateScriptFromLines(file, replacements)
    val script = target / "tmp" / "logrotate" / "logrotate.conf"
    IO.writeLines(script, template)
    script
  }
}
