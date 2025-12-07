#!groovy
import groovy.json.JsonSlurperClassic

node {
    // -----------------------------
    // Environment variables
    // -----------------------------
    def SF_CONSUMER_KEY = env.SF_CONSUMER_KEY
    def SF_USERNAME = env.SF_USERNAME
    def SERVER_KEY_CREDENTALS_ID = env.SERVER_KEY_CREDENTALS_ID
    def TEST_LEVEL = 'RunLocalTests'
    def PACKAGE_NAME = '0Ho1U000000CaUzSAK'  // Change to your package
    def PACKAGE_VERSION
    def SF_INSTANCE_URL = env.SF_INSTANCE_URL ?: "https://login.salesforce.com"

    def toolbelt = tool 'toolbelt'

    stage('Checkout Source') {
        checkout scm
    }

    // Use workspace as HOME
    withEnv(["HOME=${env.WORKSPACE}"]) {
        withCredentials([file(credentialsId: SERVER_KEY_CREDENTALS_ID, variable: 'server_key_file')]) {

            // -----------------------------
            // Authorize Dev Hub
            // -----------------------------
            stage('Authorize Dev Hub') {
                rc = command "\"${toolbelt}/sf\" org login jwt --instance-url ${SF_INSTANCE_URL} --client-id ${SF_CONSUMER_KEY} --username ${SF_USERNAME} --jwt-key-file ${server_key_file} --set-default-dev-hub --alias Dazos_DevHub"
                if (rc != 0) error 'Salesforce Dev Hub authorization failed.'
            }

            // -----------------------------
            // Create Scratch Org for Testing
            // -----------------------------
            stage('Create Test Scratch Org') {
                rc = command "\"${toolbelt}/sf\" org create scratch --target-dev-hub Dazos_DevHub --set-default --definition-file config/project-scratch-def.json --alias TestScratchOrg --wait 10 --duration-days 1"
                if (rc != 0) error 'Scratch org creation failed.'
            }

            stage('Push to Scratch Org') {
                rc = command "\"${toolbelt}/sf\" project deploy start --target-org TestScratchOrg"
                if (rc != 0) error 'Deployment to scratch org failed.'
            }

            stage('Run Tests in Scratch Org') {
                rc = command "\"${toolbelt}/sf\" apex run test --target-org TestScratchOrg --wait 10 --result-format tap --code-coverage --test-level ${TEST_LEVEL}"
                if (rc != 0) error 'Unit tests in scratch org failed.'
            }

            stage('Delete Test Scratch Org') {
                rc = command "\"${toolbelt}/sf\" org delete scratch --target-org TestScratchOrg --no-prompt"
                if (rc != 0) error 'Scratch org deletion failed.'
            }
        }
    }
}

// -----------------------------
// Helper function to run shell/bat commands
// -----------------------------
def command(script) {
    if (isUnix()) {
        return sh(returnStatus: true, script: script)
    } else {
        return bat(returnStatus: true, script: script)
    }
}
