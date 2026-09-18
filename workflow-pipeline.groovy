pipeline {
agent none
environment {
    GIT_CREDENTIAL_ID = 'saas-git-cred'
    GIT_URL           = 'https://github.com/My-Appz/saasuniverse-g2-workflow-pipeline.git'
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

    stage('workflow-pipeline') {
        agent any
        steps {
            script {
                    stage("Build workflow-pipeline") {
                        sh "sudo aws ecr get-login-password --region ${AWS_REGION} | sudo docker login --username AWS --password-stdin ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
                        sh "sudo docker build -t prod-workflow:${DOCKER_TAG} ."
                    }

                    stage("Push workflow-pipeline") {
                    
                        sh """
                            sudo docker tag prod-workflow:${DOCKER_TAG} ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/prod-workflow-pipeline:${DOCKER_TAG}
                            sudo docker tag prod-workflow:${DOCKER_TAG} ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/prod-workflow-pipeline:${COMMIT_ID}
                        """

                        sh """
                            sudo docker push ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/prod-workflow-pipeline:${DOCKER_TAG}
                            sudo docker push ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/prod-workflow-pipeline:${COMMIT_ID}
                        """
                    }

                    stage("Deploy workflow-pipeline") {
                        node('jump-server') {
                            sh """
                                sudo cp /home/ec2-user/downloads/app_yaml_files/workflow_pipeline/workflow_deploy.yml .
                                sudo sed -i -E 's|(image: ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/prod-workflow-pipeline:).*|\\1${COMMIT_ID}|' workflow_deploy.yml

                                sudo /root/bin/kubectl apply -f workflow_deploy.yml
                                sudo /root/bin/kubectl rollout restart deploy workflowdeploy -n prod

                            """
                        }
                    }

                    stage("Cleanup workflow-pipeline") {
                    
                        sh """
                            sudo docker rmi prod-workflow:${DOCKER_TAG} || true
                            sudo docker rmi ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/prod-workflow-pipeline:${DOCKER_TAG} || true
                            sudo docker rmi ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/prod-workflow-pipeline:${COMMIT_ID} || true
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



