plugins {
    id("java-library")
    id("maven-publish")
}

repositories {
    mavenCentral()
}

publishing {
    repositories {
        maven {
            url = uri("${rootProject.buildDir}/repo") // change to point to your repo, e.g. http://my.org/repo
        }
    }
}

dependencies {
    api("org.slf4j:slf4j-api:1.7.10")
}

// tag::customize-identity[]
publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "org.gradle.sample"
            artifactId = "project1-sample"
            version = "1.1"

            from(components["java"])
        }
    }
}
// end::customize-identity[]
