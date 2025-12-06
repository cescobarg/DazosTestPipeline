#!groovy
node {
    def SF_USERNAME = env.SF_USERNAME
    def SERVER_KEY_CREDENTALS_ID = env.SERVER_KEY_CREDENTALS_ID
    def SF_INSTANCE_URL = env.SF_INSTANCE_URL ?: "https://test.salesforce.com" // Sandbox URL
    def TEST_LEVEL = 'RunLocalTests'
    
    def toolbelt = tool 'toolbelt'

    stage('Checkout Source') {
        checkout scm
    }

    withEnv(["HOME=${env.WORKSPACE}"]) {
        withCredentials([file(credentialsId: SERVER_KEY_CREDENTALS_ID, variable: 'server_key_file')]) {

            // Authorize sandbox org
            stage('Authorize Sandbox') {
                rc = bat(returnStatus: true, script: "\"${toolbelt}/sf\" org login jwt --instance-url ${SF_INSTANCE_URL} --client-id ${env.SF_CONSUMER_KEY} --username ${SF_USERNAME} --jwt-key-file ${server_key_file} --set-default --alias SandboxOrg")
                if (rc != 0) {
                    error 'Salesforce sandbox org authorization failed.'
                }
            }

            // Run unit tests only (no deployment)
            stage('Run Tests in Sandbox') {
                rc = bat(returnStatus: true, script: "\"${toolbelt}/sf\" apex run test --target-org SandboxOrg --wait 10 --result-format tap --code-coverage --test-level ${TEST_LEVEL}")
                if (rc != 0) {
                    error 'Unit tests in sandbox failed.'
                }
            }
        }
    }
}
