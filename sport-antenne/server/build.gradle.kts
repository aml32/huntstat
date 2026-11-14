plugins {
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    application
}


dependencies {
    implementation("org.json:json:20240303")
}

sourceSets {
    main {
        kotlin {
            // Le serveur réutilise exactement le même « cœur » (analyse, vérification) que l'appli Android.
            srcDir("../app/src/main/java/fr/perso/sportantenne/core")
        }
    }
}

application {
    mainClass.set("fr.perso.sportantenne.server.MainKt")
}
