scalaVersion := "2.12.7"

javaOptions in run += "-Djava.library.path=lib"

libraryDependencies += "org.projectlombok" % "lombok" % "1.18.8" % "provided"
libraryDependencies += "com.google.truth" % "truth" % "1.0"
