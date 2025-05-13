plugins {
  id("com.github.johnrengelman.shadow") version "8.1.1"
  war
}
description = "Phénix objects shared between modules"

dependencies {
  implementation(project(":phenix:common"))
  implementation(project(":libs:core"))
  implementation("net.inetalliance.potion:annotations")
  implementation(project(":libs:potion:api"))
  implementation(project(":libs:validation"))
  implementation(project(":libs:types"))
  implementation(project(":libs:util"))
  implementation(project(":libs:cli"))
  implementation(project(":libs:sql"))
  implementation(libs.csv)
  implementation(libs.postgresql)
  implementation(libs.servlet)
  implementation(libs.websocket)
  implementation(libs.sendgrid)
  implementation(libs.twilio)
  implementation(libs.redis)
  implementation(libs.dotenv)
  implementation(libs.bundles.mail)
  implementation(libs.humanNameParser)
}
