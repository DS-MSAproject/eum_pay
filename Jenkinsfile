pipeline {
    agent any
    stages {
        stage('Checkout') {
            steps {
                git branch: 'main',
                        credentialsId: 'gopang-github-up',
                        url: 'https://github.com/ProjectGopang/gopang-msa.git'
            }
        }

        stage('Prepare Workspace') {
            steps {
                script {
                    // Gradle Wrapper execution permission
                    sh 'chmod +x gradlew'
                }
            }
        }

        stage('Gradle Build') {
            steps {
                script {
                    // Run Gradle build
                    sh './gradlew clean build'
                }
            }
        }
    }
}
