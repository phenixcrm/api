description = "Phenix objects shared between modules"
plugins {
  war
}

dependencies {
  implementation(project(":common"))
  implementation("net.inetalliance:potion:6.1-SNAPSHOT")
  implementation("net.inetalliance:types:1.1-SNAPSHOT")
  implementation("net.inetalliance:util:1.1-SNAPSHOT")
  implementation("net.inetalliance:cli:1.1-SNAPSHOT")
  implementation("net.inetalliance:sql:1.1-SNAPSHOT")
  implementation("net.inetalliance:log:1.1-SNAPSHOT")
  implementation("net.inetalliance:funky:1.1-SNAPSHOT")
  implementation("net.inetalliance:validation:1.1-SNAPSHOT")
  implementation("net.inetalliance.msg:aj:1.1-SNAPSHOT")
  implementation("org.apache.commons:commons-csv:1.9.0")
  implementation("net.inetalliance.msg:bjx:6.1-SNAPSHOT")
  implementation("org.postgresql:postgresql:42.3.3")
  implementation("jakarta.servlet:jakarta.servlet-api:5.0.0")
  implementation("jakarta.websocket:jakarta.websocket-api:2.0.0")
}
