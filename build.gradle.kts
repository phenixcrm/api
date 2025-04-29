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
  compileOnly(libs.servlet)
  api(project(":types"))
  api(project(":core"))
  api(project(":util"))
  api(project(":validation"))
  api(project(":file"))
  api(project(":sql"))
  api("net.inetalliance.potion:annotations")
  implementation("net.inetalliance.potion:plugin")
  testImplementation(enforcedPlatform("io.zonky.test.postgres:embedded-postgres-binaries-bom:14.5.0"))
  testImplementation("io.zonky.test:embedded-postgres:2.1.0")
  testImplementation(libs.servlet)
  testImplementation(project(":validation"))
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
