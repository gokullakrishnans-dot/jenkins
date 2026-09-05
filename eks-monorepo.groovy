pipeline {
agent none
environment {
    GIT_CREDENTIAL_ID = 'saas-git-cred'
    GIT_URL           = 'https://github.com/My-Appz/saasuniverse-v2-infra.git'
    DEPLOY_BRANCH     = 'main'
    AWS_REGION        = 'ap-south-1'
    AWS_ACCOUNT_ID    = '075439264887'
    DOCKER_TAG        = 'latest'
}

stages {
    stage('Git Checkout') {
        agent any
        steps {
            git(
                url: env.GIT_URL,
                credentialsId: env.GIT_CREDENTIAL_ID,
                branch: env.DEPLOY_BRANCH
            )

            script {
                env.COMMIT_ID = sh(
                    script: 'git rev-parse HEAD',
                    returnStdout: true
                ).trim()

                echo "Commit ID: ${env.COMMIT_ID}"
            }
        }
    }

    stage('Gen v2 deployment') {
        agent any
        steps {
            script {

                def ecrRegistry = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

                def containers = [
                    [name: 'prod-supabase-migrations',dockerfile: './docker/migrations/Dockerfile',deploy: 'migration']
                ]

                sh "sudo aws ecr get-login-password --region ${AWS_REGION} | sudo docker login --username AWS --password-stdin ${ecrRegistry}"

                for (container in containers) {

                    def serviceName = container.name
                    def dockerfile  = container.dockerfile
                    def ecrImage    = "${ecrRegistry}/${serviceName}"
                    def kubeDeploy  = container.deploy

                    stage("Build ${serviceName}") {
                    
                        sh """
                            sudo docker build \
                                -t ${serviceName}:${DOCKER_TAG} \
                                -f ${dockerfile} .
                        """
                    }

                    stage("Push ${serviceName}") {
                    
                        sh """
                            sudo docker tag \
                                ${serviceName}:${DOCKER_TAG} \
                                ${ecrImage}:${DOCKER_TAG}

                            sudo docker tag \
                                ${serviceName}:${DOCKER_TAG} \
                                ${ecrImage}:${COMMIT_ID}
                        """

                        sh """
                            sudo docker push ${ecrImage}:${DOCKER_TAG}
                            sudo docker push ${ecrImage}:${COMMIT_ID}
                        """
                    }

                    stage("Deploy ${serviceName}") {
                        node('jump-server') {
                            sh """
                                sudo cp /home/ec2-user/downloads/app_yaml_files/${kubeDeploy}/${kubeDeploy}_deploy.yml .
                                sudo sed -i -E 's|(image: ${ecrImage}:).*|\\1${COMMIT_ID}|' ${kubeDeploy}_deploy.yml
                                sudo /root/bin/kubectl apply -f ${kubeDeploy}_deploy.yml -n prod
                                sudo /root/bin/kubectl rollout restart -f ${kubeDeploy}_deploy.yml -n prod
                            """
                        }
                    }

                    stage("Cleanup ${serviceName}") {
                    
                        sh """
                            sudo docker rmi ${serviceName}:${DOCKER_TAG} || true
                            sudo docker rmi ${ecrImage}:${DOCKER_TAG} || true
                            sudo docker rmi ${ecrImage}:${COMMIT_ID} || true
                        """
                    }
                }
                
            }
        }
    }
}
post {

    success {
        slackSend(
            channel: '#deployment',
            color: 'good',
            message: """*Build & Push Successful*
*Application:* ${env.JOB_NAME}
*Build:* #${env.BUILD_NUMBER}
*Branch:* ${env.DEPLOY_BRANCH}
*Commit:* ${env.COMMIT_ID}

${env.BUILD_URL}
"""
)
}


    failure {
        slackSend(
            channel: '#deployment',
            color: 'danger',
            message: """*Build & Push Failed*
*Application:* ${env.JOB_NAME}
*Build:* #${env.BUILD_NUMBER}
*Branch:* ${env.DEPLOY_BRANCH}


${env.BUILD_URL}
"""
)
}
}
}


