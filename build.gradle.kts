plugins {
  id("java-library")
  id("net.bytebuddy.byte-buddy-gradle-plugin") version "1.17.5"
}
buildscript {
  dependencies {
    classpath("net.inetalliance.potion:plugin")
  }
}
group = "net.inetalliance.potion"

dependencies {
  implementation(libs.jsoup)
  implementation(libs.byteBuddy)
  implementation(libs.postgresql)
  implementation(libs.hikariCP)
  implementation(libs.redis)
  implementation(project(":libs:validation"))
  compileOnly(libs.servlet)
  api(project(":libs:types"))
  api(project(":libs:core"))
  api(project(":libs:util"))
  api(project(":libs:file"))
  api(project(":libs:sql"))
  api("net.inetalliance.potion:annotations")
  implementation("net.inetalliance.potion:plugin")
  testImplementation(enforcedPlatform("io.zonky.test.postgres:embedded-postgres-binaries-bom:14.5.0"))
  testImplementation("io.zonky.test:embedded-postgres:2.1.0")
  testImplementation(libs.servlet)
}
byteBuddy {
  transformation {
    pluginName = "net.inetalliance.potion.Potion"
  }
}
testByteBuddy {
  transformation {
    pluginName = "net.inetalliance.potion.Potion"
  }
}
