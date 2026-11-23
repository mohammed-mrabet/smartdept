// Pipeline Jenkins (TP6) : tests, qualité, images Docker, scan de sécurité, publication et déploiement.
// Identifiants Jenkins attendus : "dockerhub" (nom d'utilisateur + jeton), "kubeconfig" (fichier, facultatif),
// "ssh-azure" (clé SSH pour Ansible, facultatif). SonarQube est utilisé s'il est configuré sous le nom "sonarqube".
pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        timeout(time: 45, unit: 'MINUTES')
    }

    parameters {
        choice(name: 'DEPLOY_TARGET', choices: ['aucun', 'kubernetes', 'azure-vm'], description: 'Déploiement après une construction réussie de main')
    }

    environment {
        DOCKERHUB = credentials('dockerhub')
        IMAGE_BACKEND = "${DOCKERHUB_USR}/smartdept-backend"
        IMAGE_FRONTEND = "${DOCKERHUB_USR}/smartdept-frontend"
        TAG = "${env.GIT_COMMIT ? env.GIT_COMMIT.take(7) : env.BUILD_NUMBER}"
    }

    stages {
        stage('Récupération du code') {
            steps {
                checkout scm
            }
        }

        stage('Tests') {
            parallel {
                stage('Backend : tests et couverture') {
                    steps {
                        dir('backend') {
                            sh 'chmod +x mvnw && ./mvnw -B verify'
                        }
                    }
                    post {
                        always {
                            junit allowEmptyResults: true, testResults: 'backend/target/surefire-reports/*.xml'
                            recordCoverage(tools: [[parser: 'JACOCO', pattern: 'backend/target/site/jacoco/jacoco.xml']],
                                           sourceDirectories: [[path: 'backend/src/main/java']])
                        }
                    }
                }
                stage('Frontend : tests et build') {
                    agent { docker { image 'node:22-alpine'; reuseNode true } }
                    steps {
                        dir('frontend') {
                            sh 'npm ci --no-audit --no-fund'
                            sh 'npm test'
                            sh 'npx ng build --configuration production'
                        }
                    }
                }
            }
        }

        stage('Qualité (SonarQube)') {
            when { expression { return env.SONAR_HOST_URL != null } }
            steps {
                withSonarQubeEnv('sonarqube') {
                    dir('backend') {
                        sh './mvnw -B sonar:sonar -Dsonar.projectKey=smartdept -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml'
                    }
                }
            }
        }

        stage('Images Docker') {
            steps {
                sh "docker build -t ${IMAGE_BACKEND}:${TAG} -t ${IMAGE_BACKEND}:latest backend"
                sh "docker build -t ${IMAGE_FRONTEND}:${TAG} -t ${IMAGE_FRONTEND}:latest frontend"
            }
        }

        stage('Sécurité : Trivy et SBOM') {
            steps {
                sh """
                    for img in ${IMAGE_BACKEND}:${TAG} ${IMAGE_FRONTEND}:${TAG}; do
                      docker run --rm -v /var/run/docker.sock:/var/run/docker.sock aquasec/trivy:0.57.1 image \
                        --severity HIGH,CRITICAL --ignore-unfixed --exit-code 0 --no-progress \$img
                    done
                    docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v \$PWD:/out aquasec/trivy:0.57.1 image \
                      --format cyclonedx --output /out/sbom-backend.json ${IMAGE_BACKEND}:${TAG}
                """
                archiveArtifacts artifacts: 'sbom-*.json', allowEmptyArchive: true
            }
        }

        stage('Publication Docker Hub') {
            when { branch 'main' }
            steps {
                sh 'echo "$DOCKERHUB_PSW" | docker login -u "$DOCKERHUB_USR" --password-stdin'
                sh "docker push ${IMAGE_BACKEND}:${TAG} && docker push ${IMAGE_BACKEND}:latest"
                sh "docker push ${IMAGE_FRONTEND}:${TAG} && docker push ${IMAGE_FRONTEND}:latest"
            }
            post { always { sh 'docker logout || true' } }
        }

        stage('Validation avant production') {
            when { allOf { branch 'main'; expression { params.DEPLOY_TARGET != 'aucun' } } }
            steps {
                input message: "Déployer la version ${TAG} sur ${params.DEPLOY_TARGET} ?", ok: 'Déployer'
            }
        }

        stage('Déploiement Kubernetes') {
            when { allOf { branch 'main'; expression { params.DEPLOY_TARGET == 'kubernetes' } } }
            steps {
                withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG')]) {
                    sh """
                        kubectl apply -k k8s/
                        kubectl -n smartdept set image deployment/backend backend=${IMAGE_BACKEND}:${TAG}
                        kubectl -n smartdept set image deployment/frontend frontend=${IMAGE_FRONTEND}:${TAG}
                        kubectl -n smartdept rollout status deployment/backend --timeout=300s
                        kubectl -n smartdept rollout status deployment/frontend --timeout=120s
                    """
                }
            }
        }

        stage('Déploiement Azure (Ansible)') {
            when { allOf { branch 'main'; expression { params.DEPLOY_TARGET == 'azure-vm' } } }
            steps {
                sshagent(credentials: ['ssh-azure']) {
                    sh "cd ansible && ansible-playbook -i inventory.ini site.yml -e image_tag=${TAG} -e dockerhub_user=${DOCKERHUB_USR}"
                }
            }
        }
    }

    post {
        success { echo "SmartDept ${TAG} : pipeline réussi" }
        failure { echo "SmartDept ${TAG} : échec — voir les journaux de l'étape en rouge" }
        cleanup { cleanWs(deleteDirs: true, notFailBuild: true) }
    }
}
