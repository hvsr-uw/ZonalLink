plugins {
    `java-library`
    kotlin("jvm")
    id("com.google.protobuf")
}

dependencies {
    api("com.google.protobuf:protobuf-kotlin:4.28.3")
    api("io.grpc:grpc-protobuf:1.68.1")
    api("io.grpc:grpc-stub:1.68.1")
    api("io.grpc:grpc-kotlin-stub:1.4.1")
    compileOnly("org.apache.tomcat:annotations-api:6.0.53")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.28.3"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.68.1"
        }
        id("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:1.4.1:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                id("grpc")
                id("grpckt")
            }
            it.builtins {
                id("kotlin")
            }
        }
    }
}
