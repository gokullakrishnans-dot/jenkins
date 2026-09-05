pipeline{
    environment {
        gitCredentialId = 'manikandangitsecret' //defined in credentials area
        gitUrl = 'https://github.com/ManikandanTechnorucs/Sahayii_Admin_Service.git'
        deployBranch = 'main'
    }
    agent none
    stages{         
        stage('git'){             
            agent {label "sahayi-backend-server"}
            steps{                 
                git (
                    url: gitUrl,
                    credentialsId: gitCredentialId,
                    branch: deployBranch
                    )
            }         
        }
         stage('cleanup'){
            agent {label "sahayi-backend-server"}
            steps{
                sh "echo 'Clean up'"
                sh "sudo docker rm -f sahayi_admin_service_container"
                sh "sudo docker rmi sahayi_admin_service:latest"
            }
        }

        stage('build'){
            agent {label "sahayi-backend-server"}
            steps{
                sh "sudo cp /home/env_admin_service/.env /home/azureuser/workspace/admin_service/"
                sh "sudo docker build -t sahayi_admin_service:latest ."
            }
        }
        
        stage('Deploy'){
            agent {label "sahayi-backend-server"}
            steps{
                sh "sudo docker run -itd --name sahayi_admin_service_container --env-file /home/azureuser/workspace/admin_service/.env -p 3007:3007 sahayi_admin_service:latest"
            }
        }
    }