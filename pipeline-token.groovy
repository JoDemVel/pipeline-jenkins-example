pipeline{
   agent
   {
    label 'jenkins_slave'
   }
    tools{
        maven 'maven-396'
        jdk 'jdk21'
    }
    parameters{
      string(name: 'BRANCH', defaultValue: 'develop', description: 'Colocar un branch a deployar')
      choice(name: 'TEST_VULNERAVILITY', choices: ['YES', 'NO'], description: 'Colocar YES para realizar el test de vulnerabilidad')
    }
    environment{
        workspace="/data/"
        ACCESS_TOKEN = credentials('ACCESS_TOKEN')
    }
    stages{
        stage("Create build name"){
          steps{
            script{
              currentBuild.displayName = "service_back-"+currentBuild.number
            }
          }
        }
        stage("Clean"){
            steps{
                cleanWs()
            }
        }
        stage("Download project"){
            steps{
                git credentialsId: 'git_credentials', branch: "${BRANCH}", url: "https://${ACCESS_TOKEN}@github.com/JoDemVel/babels.git"
                echo "Downloaded project"
            }
        }
        stage('Build proyect')
        {
            steps{
                sh "mvn clean compile package -Dmaven.test.skip=true -U"
                sh "pwd"
                sh "mv target/*.jar target/app.jar"
                stash includes: 'target/app.jar', name: 'backartifact'
                archiveArtifacts artifacts: 'target/app.jar', onlyIfSuccessful: true
                sh "cp target/app.jar /tmp/"
            }
        }
        stage("Test vulnerability")
        {
            when{equals expected: 'YES', actual: TEST_VULNERAVILITY}
            steps{
               sh "/grype /tmp/app.jar > informe-scan.txt"
               archiveArtifacts artifacts: 'informe-scan.txt', onlyIfSuccessful: true
            }
        }
        stage('sonarqube analysis'){
            steps{
               script{
                   sh "pwd"
						writeFile encoding: 'UTF-8', file: 'sonar-project.properties', text: """sonar.projectKey=academy
						sonar.projectName=academy
						sonar.projectVersion=academy
						sonar.sourceEncoding=UTF-8
						sonar.sources=src/main/
						sonar.java.binaries=target/
						sonar.java.libraries=target/classes
						sonar.language=java
						sonar.scm.provider=git
						"""
                        // Sonar Disabled due to we don't have a sonar in tools account yet
						withSonarQubeEnv('Sonar_CI') {
						     def scannerHome = tool 'Sonar_CI'
						     sh "${tool("Sonar_CI")}/bin/sonar-scanner -X"
						}
               }
            }
        }
    }
}