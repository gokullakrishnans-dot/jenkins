pipeline {
    agent none

    environment {
        GIT_CREDENTIAL_ID = 'saas-git-cred'
        GIT_URL           = 'https://github.com/My-Appz/saasuniverse-v2.git'
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
                }
            }
        }
        
        stage('Build') {
            agent any

            steps {
                sh 'sudo aws s3api put-bucket-policy --bucket build-test-buckent-su --policy file:///home/frontend_env/unlock_policy.json'
                sh 'rm -rf .env'
                sh 'cp /home/frontend_env/.env .'
                sh 'sudo /root/.nvm/versions/node/v24.20.0/bin/npm install --no-dev'
                sh 'sudo /root/.nvm/versions/node/v24.20.0/bin/npm run build'
            }
        }

        stage('Deploy') {
            agent any

            steps {
                sh 'sudo aws s3 rm s3://build-test-buckent-su --recursive'
                sh 'sudo aws s3 sync ./dist s3://build-test-buckent-su'
                sh 'sudo aws s3api put-bucket-policy --bucket build-test-buckent-su --policy file:////home/frontend_env/lock_policy.json'
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

