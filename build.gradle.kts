plugins {
  id("com.github.johnrengelman.shadow") version "8.1.1"
  war
}
description = "Phénix objects shared between modules"

dependencies {
  implementation(project(":common"))
  implementation(project(":core"))
  implementation("net.inetalliance.potion:annotations")
  implementation(project(":potion:api"))
  implementation(project(":validation"))
  implementation(project(":types"))
  implementation(project(":util"))
  implementation(project(":cli"))
  implementation(project(":sql"))
  implementation(libs.csv)
  implementation(libs.postgresql)
  implementation(libs.servlet)
  implementation(libs.websocket)
  implementation(libs.sendgrid)
  implementation(libs.twilio)
  implementation(libs.redis)
  implementation(libs.dotenv)
  implementation(libs.bundles.mail)
  implementation("com.tupilabs:human-name-parser:0.2")
}
